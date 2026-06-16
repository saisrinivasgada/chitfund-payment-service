package com.chitfund.paymentservice.service;

import com.chitfund.common.event.CashCollectedEvent;
import com.chitfund.common.event.PaymentCompletedEvent;
import com.chitfund.common.exception.BusinessException;
import com.chitfund.common.exception.ErrorCode;
import com.chitfund.paymentservice.domain.PaymentAllocation;
import com.chitfund.paymentservice.domain.PaymentBatch;
import com.chitfund.paymentservice.domain.PaymentRecord;
import com.chitfund.paymentservice.domain.enums.BatchStatus;
import com.chitfund.paymentservice.domain.enums.PaymentMode;
import com.chitfund.paymentservice.domain.enums.PaymentRecordStatus;
import com.chitfund.paymentservice.dto.request.CollectCashRequest;
import com.chitfund.paymentservice.dto.request.RecordPaymentRequest;
import com.chitfund.paymentservice.dto.response.MemberBalanceResponse;
import com.chitfund.paymentservice.dto.response.PaymentBatchResponse;
import com.chitfund.paymentservice.dto.response.PaymentRecordResponse;
import com.chitfund.paymentservice.client.MemberServiceClient;
import com.chitfund.paymentservice.kafka.PaymentEventPublisher;
import com.chitfund.paymentservice.repository.PaymentAllocationRepository;
import com.chitfund.paymentservice.repository.PaymentBatchRepository;
import com.chitfund.paymentservice.repository.PaymentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentBatchRepository batchRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final PaymentEventPublisher eventPublisher;
    private final MemberServiceClient memberServiceClient;

    /**
     * Step 1 of the cash flow: worker records collection from a member.
     * Money is physically with the worker — FIFO is NOT applied yet.
     * Admin must call remitCash() after receiving from the worker.
     */
    @Transactional
    public PaymentBatchResponse collectCash(CollectCashRequest request, UUID workerId) {
        if (!memberServiceClient.isMemberActive(request.getMemberId())) {
            throw new BusinessException(ErrorCode.MEMBER_INACTIVE,
                    "Member " + request.getMemberId() + " is not active");
        }

        PaymentBatch batch = PaymentBatch.builder()
                .chitId(request.getChitId())
                .memberId(request.getMemberId())
                .totalAmount(request.getAmount())
                .paymentMode(PaymentMode.CASH)
                .status(BatchStatus.AWAITING_REMITTANCE)
                .collectedBy(workerId)
                .collectedAt(LocalDateTime.now())
                .notes(request.getNotes())
                .build();
        batchRepository.save(batch);

        log.info("Worker {} collected ₹{} cash from member {} for chit {} — awaiting remittance",
                workerId, request.getAmount(), request.getMemberId(), request.getChitId());

        eventPublisher.publish(new CashCollectedEvent(
                batch.getId().toString(),
                request.getChitId().toString(),
                request.getMemberId().toString(),
                request.getAmount(),
                workerId.toString(),
                Instant.now()
        ));

        return toBatchResponse(batch, List.of());
    }

    /**
     * For non-cash payments (UPI, BANK_TRANSFER, CHEQUE).
     * Admin records directly — completed immediately, FIFO applied now.
     */
    @Transactional
    public PaymentBatchResponse recordPayment(RecordPaymentRequest request, UUID adminId) {
        if (!memberServiceClient.isMemberActive(request.getMemberId())) {
            throw new BusinessException(ErrorCode.MEMBER_INACTIVE,
                    "Member " + request.getMemberId() + " is not active");
        }

        if (request.getPaymentMode() == PaymentMode.CASH) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_PAYMENT_MODE,
                    "Use POST /payments/collect for cash. This endpoint is for UPI, bank transfer, and cheque.");
        }

        PaymentBatch batch = PaymentBatch.builder()
                .chitId(request.getChitId())
                .memberId(request.getMemberId())
                .totalAmount(request.getAmount())
                .paymentMode(request.getPaymentMode())
                .status(BatchStatus.COMPLETED)
                .notes(request.getNotes())
                .build();
        batchRepository.save(batch);

        List<PaymentAllocation> allocations = applyFifo(batch);

        log.info("Admin {} recorded {} payment of ₹{} for member {} in chit {} — {} months updated",
                adminId, request.getPaymentMode(), request.getAmount(),
                request.getMemberId(), request.getChitId(), allocations.size());

        eventPublisher.publish(buildCompletedEvent(batch, adminId));

        return toBatchResponse(batch, allocations);
    }

    /**
     * Step 2 of the cash flow: admin confirms they received the cash from the worker.
     * FIFO is applied here — payment_records are updated only after admin confirmation.
     *
     * WHY apply FIFO only at remittance, not at collection?
     * Until admin physically receives the money, it's the worker's responsibility.
     * If a worker loses the cash before remitting, we haven't credited the member yet.
     * This keeps accounting accurate — credit follows actual receipt.
     */
    @Transactional
    public PaymentBatchResponse remitCash(UUID batchId, UUID adminId) {
        PaymentBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND,
                        "Payment batch not found: " + batchId));

        if (batch.getStatus() != BatchStatus.AWAITING_REMITTANCE) {
            throw new BusinessException(ErrorCode.BATCH_ALREADY_COMPLETED,
                    "This payment has already been completed");
        }

        batch.setStatus(BatchStatus.COMPLETED);
        batch.setRemittedAt(LocalDateTime.now());
        batch.setRemittedBy(adminId);
        batchRepository.save(batch);

        List<PaymentAllocation> allocations = applyFifo(batch);

        log.info("Admin {} remitted cash batch {} (₹{}) for member {} — {} months updated",
                adminId, batchId, batch.getTotalAmount(), batch.getMemberId(), allocations.size());

        eventPublisher.publish(buildCompletedEvent(batch, adminId));

        return toBatchResponse(batch, allocations);
    }

    /**
     * FIFO debt application.
     * Oldest unpaid month is cleared first. If payment exceeds oldest balance,
     * remainder spills to the next-oldest month — and so on until amount is exhausted.
     *
     * Example: member owes month 2 (₹1,000 remaining) and month 3 (₹5,000).
     * Payment = ₹6,000 → month 2 SETTLED (₹1,000), month 3 SETTLED (₹5,000). Zero leftover.
     *
     * Another example: payment = ₹4,000 → month 2 SETTLED (₹1,000), month 3 PARTIALLY_PAID (₹3,000 applied, ₹2,000 remaining).
     */
    private List<PaymentAllocation> applyFifo(PaymentBatch batch) {
        List<PaymentRecord> outstanding = paymentRecordRepository
                .findByMemberIdAndChitIdAndStatusInOrderByMonthNumberAsc(
                        batch.getMemberId(),
                        batch.getChitId(),
                        List.of(PaymentRecordStatus.OUTSTANDING, PaymentRecordStatus.PARTIALLY_PAID));

        BigDecimal remaining = batch.getTotalAmount();
        List<PaymentAllocation> allocations = new ArrayList<>();

        for (PaymentRecord record : outstanding) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal owed    = record.getAmountDue().subtract(record.getAmountPaid());
            BigDecimal applying = remaining.min(owed);

            PaymentAllocation alloc = PaymentAllocation.builder()
                    .batchId(batch.getId())
                    .paymentRecordId(record.getId())
                    .chitId(batch.getChitId())
                    .memberId(batch.getMemberId())
                    .monthNumber(record.getMonthNumber())
                    .allocatedAmount(applying)
                    .build();
            allocationRepository.save(alloc);
            allocations.add(alloc);

            record.setAmountPaid(record.getAmountPaid().add(applying));
            record.setStatus(record.getAmountPaid().compareTo(record.getAmountDue()) >= 0
                    ? PaymentRecordStatus.SETTLED
                    : PaymentRecordStatus.PARTIALLY_PAID);
            paymentRecordRepository.save(record);

            remaining = remaining.subtract(applying);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            // Overpayment — no more outstanding records to apply to.
            // This is unusual (member paid more than total owed). Log it for admin awareness.
            log.warn("Batch {} has ₹{} unallocated after FIFO — member {} has no more outstanding records in chit {}",
                    batch.getId(), remaining, batch.getMemberId(), batch.getChitId());
        }

        return allocations;
    }

    @Transactional(readOnly = true)
    public MemberBalanceResponse getMemberBalance(UUID memberId, UUID chitId) {
        List<PaymentRecord> nonSettled = paymentRecordRepository
                .findByMemberIdAndChitIdAndStatusInOrderByMonthNumberAsc(
                        memberId, chitId,
                        List.of(PaymentRecordStatus.OUTSTANDING, PaymentRecordStatus.PARTIALLY_PAID));

        BigDecimal totalOutstanding = nonSettled.stream()
                .map(r -> r.getAmountDue().subtract(r.getAmountPaid()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<MemberBalanceResponse.MonthBalance> months = nonSettled.stream()
                .map(r -> MemberBalanceResponse.MonthBalance.builder()
                        .monthNumber(r.getMonthNumber())
                        .dueDate(r.getDueDate())
                        .amountDue(r.getAmountDue())
                        .amountPaid(r.getAmountPaid())
                        .balance(r.getAmountDue().subtract(r.getAmountPaid()))
                        .status(r.getStatus())
                        .build())
                .toList();

        return MemberBalanceResponse.builder()
                .memberId(memberId)
                .chitId(chitId)
                .totalOutstanding(totalOutstanding)
                .months(months)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PaymentRecordResponse> getPaymentHistory(UUID memberId, UUID chitId) {
        List<PaymentRecord> records = paymentRecordRepository
                .findByMemberIdAndChitIdOrderByMonthNumberAsc(memberId, chitId);
        LocalDate today = LocalDate.now();
        return records.stream()
                .map(r -> PaymentRecordResponse.builder()
                        .id(r.getId())
                        .chitId(r.getChitId())
                        .memberId(r.getMemberId())
                        .monthNumber(r.getMonthNumber())
                        .dueDate(r.getDueDate())
                        .amountDue(r.getAmountDue())
                        .amountPaid(r.getAmountPaid())
                        .balance(r.getAmountDue().subtract(r.getAmountPaid()))
                        .status(r.getStatus())
                        .overdue((r.getStatus() == PaymentRecordStatus.OUTSTANDING
                                || r.getStatus() == PaymentRecordStatus.PARTIALLY_PAID)
                                && r.getDueDate().isBefore(today))
                        .createdAt(r.getCreatedAt())
                        .updatedAt(r.getUpdatedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentBatchResponse> getPendingRemittances() {
        return batchRepository.findByStatusOrderByCreatedAtAsc(BatchStatus.AWAITING_REMITTANCE).stream()
                .map(batch -> {
                    List<PaymentAllocation> allocs = allocationRepository.findByBatchId(batch.getId());
                    return toBatchResponse(batch, allocs);
                })
                .toList();
    }

    /**
     * Computes a "fat event" with the member's full payment summary at this point in time.
     * reporting-service uses these totals directly — no cross-service call needed.
     */
    private PaymentCompletedEvent buildCompletedEvent(PaymentBatch batch, UUID actorId) {
        List<PaymentRecord> all = paymentRecordRepository
                .findByMemberIdAndChitIdOrderByMonthNumberAsc(batch.getMemberId(), batch.getChitId());

        BigDecimal totalDue  = all.stream().map(PaymentRecord::getAmountDue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = all.stream().map(PaymentRecord::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);

        long settled  = all.stream().filter(r -> r.getStatus() == PaymentRecordStatus.SETTLED).count();
        long partial  = all.stream().filter(r -> r.getStatus() == PaymentRecordStatus.PARTIALLY_PAID).count();
        long outstanding = all.stream().filter(r -> r.getStatus() == PaymentRecordStatus.OUTSTANDING).count();
        long waived   = all.stream().filter(r -> r.getStatus() == PaymentRecordStatus.WAIVED).count();

        return new PaymentCompletedEvent(
                batch.getId().toString(),
                batch.getChitId().toString(),
                batch.getMemberId().toString(),
                batch.getTotalAmount(),
                batch.getPaymentMode().name(),
                totalDue,
                totalPaid,
                totalDue.subtract(totalPaid),
                (int) settled, (int) partial, (int) outstanding, (int) waived,
                LocalDate.now(),
                actorId.toString(),
                Instant.now()
        );
    }

    private PaymentBatchResponse toBatchResponse(PaymentBatch batch, List<PaymentAllocation> allocations) {
        return PaymentBatchResponse.builder()
                .id(batch.getId())
                .chitId(batch.getChitId())
                .memberId(batch.getMemberId())
                .totalAmount(batch.getTotalAmount())
                .paymentMode(batch.getPaymentMode())
                .status(batch.getStatus())
                .collectedBy(batch.getCollectedBy())
                .collectedAt(batch.getCollectedAt())
                .remittedAt(batch.getRemittedAt())
                .remittedBy(batch.getRemittedBy())
                .notes(batch.getNotes())
                .createdAt(batch.getCreatedAt())
                .allocations(allocations.stream()
                        .map(a -> PaymentBatchResponse.AllocationDetail.builder()
                                .monthNumber(a.getMonthNumber())
                                .allocatedAmount(a.getAllocatedAmount())
                                .paymentRecordId(a.getPaymentRecordId())
                                .build())
                        .toList())
                .build();
    }
}
