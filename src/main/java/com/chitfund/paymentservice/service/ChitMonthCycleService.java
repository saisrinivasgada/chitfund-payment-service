package com.chitfund.paymentservice.service;

import com.chitfund.common.event.ChitMonthOpenedEvent;
import com.chitfund.common.event.ChitMonthSkippedEvent;
import com.chitfund.common.exception.BusinessException;
import com.chitfund.common.exception.ErrorCode;
import com.chitfund.paymentservice.domain.ChitMonthCycle;
import com.chitfund.paymentservice.domain.PaymentRecord;
import com.chitfund.paymentservice.domain.enums.CycleStatus;
import com.chitfund.paymentservice.domain.enums.PaymentRecordStatus;
import com.chitfund.paymentservice.dto.request.OpenMonthRequest;
import com.chitfund.paymentservice.dto.request.SkipMonthRequest;
import com.chitfund.paymentservice.dto.response.CycleSummaryResponse;
import com.chitfund.paymentservice.kafka.PaymentEventPublisher;
import com.chitfund.paymentservice.repository.ChitMonthCycleRepository;
import com.chitfund.paymentservice.repository.PaymentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChitMonthCycleService {

    private final ChitMonthCycleRepository cycleRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentEventPublisher eventPublisher;

    @Transactional
    public CycleSummaryResponse openMonth(OpenMonthRequest request, UUID adminId) {
        if (cycleRepository.existsByChitIdAndMonthNumber(request.getChitId(), request.getMonthNumber())) {
            throw new BusinessException(ErrorCode.MONTH_ALREADY_OPEN,
                    "Month " + request.getMonthNumber() + " is already open or skipped for this chit");
        }

        ChitMonthCycle cycle = ChitMonthCycle.builder()
                .chitId(request.getChitId())
                .monthNumber(request.getMonthNumber())
                .dueDate(request.getDueDate())
                .installmentAmount(request.getInstallmentAmount())
                .totalMembers(request.getMemberIds().size())
                .status(CycleStatus.OPEN)
                .openedAt(LocalDateTime.now())
                .openedBy(adminId)
                .build();
        cycleRepository.save(cycle);

        List<PaymentRecord> records = request.getMemberIds().stream()
                .map(memberId -> PaymentRecord.builder()
                        .chitId(request.getChitId())
                        .memberId(memberId)
                        .monthNumber(request.getMonthNumber())
                        .dueDate(request.getDueDate())
                        .amountDue(request.getInstallmentAmount())
                        .amountPaid(BigDecimal.ZERO)
                        .status(PaymentRecordStatus.OUTSTANDING)
                        .build())
                .toList();
        paymentRecordRepository.saveAll(records);

        log.info("Admin {} opened month {} for chit {} — {} payment records created",
                adminId, request.getMonthNumber(), request.getChitId(), records.size());

        eventPublisher.publish(new ChitMonthOpenedEvent(
                request.getChitId().toString(),
                null,                          // chitName not available in payment-service
                request.getMonthNumber(),
                request.getDueDate(),
                request.getInstallmentAmount(),
                request.getMemberIds().size(),
                request.getMemberIds().stream().map(UUID::toString).toList(),
                adminId.toString(),
                Instant.now()
        ));

        return buildSummary(cycle, records);
    }

    @Transactional
    public CycleSummaryResponse skipMonth(SkipMonthRequest request, UUID adminId) {
        if (cycleRepository.existsByChitIdAndMonthNumber(request.getChitId(), request.getMonthNumber())) {
            throw new BusinessException(ErrorCode.MONTH_ALREADY_OPEN,
                    "Month " + request.getMonthNumber() + " is already open or skipped for this chit");
        }

        ChitMonthCycle cycle = ChitMonthCycle.builder()
                .chitId(request.getChitId())
                .monthNumber(request.getMonthNumber())
                .dueDate(request.getDueDate())
                .installmentAmount(request.getInstallmentAmount())
                .totalMembers(request.getMemberIds().size())
                .status(CycleStatus.SKIPPED)
                .skipReason(request.getSkipReason())
                .skippedAt(LocalDateTime.now())
                .skippedBy(adminId)
                .build();
        cycleRepository.save(cycle);

        // WAIVED records preserve installmentAmount for reports ("₹X waived in month Y due to Z")
        List<PaymentRecord> waivedRecords = request.getMemberIds().stream()
                .map(memberId -> PaymentRecord.builder()
                        .chitId(request.getChitId())
                        .memberId(memberId)
                        .monthNumber(request.getMonthNumber())
                        .dueDate(request.getDueDate())
                        .amountDue(request.getInstallmentAmount())
                        .amountPaid(BigDecimal.ZERO)
                        .status(PaymentRecordStatus.WAIVED)
                        .build())
                .toList();
        paymentRecordRepository.saveAll(waivedRecords);

        log.info("Admin {} skipped month {} for chit {}. Reason: {}",
                adminId, request.getMonthNumber(), request.getChitId(), request.getSkipReason());

        eventPublisher.publish(new ChitMonthSkippedEvent(
                request.getChitId().toString(),
                null,
                request.getMonthNumber(),
                request.getDueDate(),
                request.getInstallmentAmount(),
                request.getMemberIds().size(),
                request.getMemberIds().stream().map(UUID::toString).toList(),
                request.getSkipReason(),
                adminId.toString(),
                Instant.now()
        ));

        return buildSummary(cycle, waivedRecords);
    }

    @Transactional(readOnly = true)
    public List<CycleSummaryResponse> getDashboard() {
        List<ChitMonthCycle> openCycles = cycleRepository.findByStatusOrderByDueDateAsc(CycleStatus.OPEN);
        return openCycles.stream()
                .map(this::buildSummaryWithLiveStats)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CycleSummaryResponse> getCyclesForChit(UUID chitId) {
        return cycleRepository.findByChitIdOrderByMonthNumberAsc(chitId).stream()
                .map(this::buildSummaryWithLiveStats)
                .toList();
    }

    private CycleSummaryResponse buildSummaryWithLiveStats(ChitMonthCycle cycle) {
        List<PaymentRecord> records = paymentRecordRepository
                .findByChitIdAndMonthNumber(cycle.getChitId(), cycle.getMonthNumber());

        long settled     = count(records, PaymentRecordStatus.SETTLED);
        long partial     = count(records, PaymentRecordStatus.PARTIALLY_PAID);
        long outstanding = count(records, PaymentRecordStatus.OUTSTANDING);
        long waived      = count(records, PaymentRecordStatus.WAIVED);

        BigDecimal totalCollected = records.stream()
                .map(PaymentRecord::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutstanding = records.stream()
                .map(r -> r.getAmountDue().subtract(r.getAmountPaid()))
                .filter(b -> b.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CycleSummaryResponse.builder()
                .id(cycle.getId())
                .chitId(cycle.getChitId())
                .monthNumber(cycle.getMonthNumber())
                .dueDate(cycle.getDueDate())
                .installmentAmount(cycle.getInstallmentAmount())
                .totalMembers(cycle.getTotalMembers())
                .status(cycle.getStatus())
                .settledCount((int) settled)
                .partiallyPaidCount((int) partial)
                .outstandingCount((int) outstanding)
                .waivedCount((int) waived)
                .totalCollected(totalCollected)
                .totalOutstanding(totalOutstanding)
                .skipReason(cycle.getSkipReason())
                .openedAt(cycle.getOpenedAt())
                .openedBy(cycle.getOpenedBy())
                .skippedAt(cycle.getSkippedAt())
                .skippedBy(cycle.getSkippedBy())
                .build();
    }

    private CycleSummaryResponse buildSummary(ChitMonthCycle cycle, List<PaymentRecord> records) {
        boolean isSkipped = cycle.getStatus() == CycleStatus.SKIPPED;
        int memberCount = records.size();

        return CycleSummaryResponse.builder()
                .id(cycle.getId())
                .chitId(cycle.getChitId())
                .monthNumber(cycle.getMonthNumber())
                .dueDate(cycle.getDueDate())
                .installmentAmount(cycle.getInstallmentAmount())
                .totalMembers(memberCount)
                .status(cycle.getStatus())
                .settledCount(0)
                .partiallyPaidCount(0)
                .outstandingCount(isSkipped ? 0 : memberCount)
                .waivedCount(isSkipped ? memberCount : 0)
                .totalCollected(BigDecimal.ZERO)
                .totalOutstanding(isSkipped ? BigDecimal.ZERO
                        : cycle.getInstallmentAmount().multiply(BigDecimal.valueOf(memberCount)))
                .skipReason(cycle.getSkipReason())
                .openedAt(cycle.getOpenedAt())
                .openedBy(cycle.getOpenedBy())
                .skippedAt(cycle.getSkippedAt())
                .skippedBy(cycle.getSkippedBy())
                .build();
    }

    private long count(List<PaymentRecord> records, PaymentRecordStatus status) {
        return records.stream().filter(r -> r.getStatus() == status).count();
    }
}
