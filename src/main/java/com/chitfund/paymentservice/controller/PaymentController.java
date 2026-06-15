package com.chitfund.paymentservice.controller;

import com.chitfund.common.dto.ApiResponse;
import com.chitfund.paymentservice.dto.request.CollectCashRequest;
import com.chitfund.paymentservice.dto.request.RecordPaymentRequest;
import com.chitfund.paymentservice.dto.response.MemberBalanceResponse;
import com.chitfund.paymentservice.dto.response.PaymentBatchResponse;
import com.chitfund.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Worker collects cash from a member during their rounds.
     * Creates AWAITING_REMITTANCE batch — payment_records NOT yet updated.
     * Admin must call /remit after receiving the cash from the worker.
     */
    @PostMapping("/collect")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_WORKER')")
    public ResponseEntity<ApiResponse<PaymentBatchResponse>> collectCash(
            @Valid @RequestBody CollectCashRequest request,
            Authentication auth) {
        UUID workerId = (UUID) auth.getPrincipal();
        PaymentBatchResponse response = paymentService.collectCash(request, workerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * Admin records a non-cash payment (UPI, bank transfer, cheque).
     * FIFO is applied immediately — payment_records updated in the same transaction.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaymentBatchResponse>> recordPayment(
            @Valid @RequestBody RecordPaymentRequest request,
            Authentication auth) {
        UUID adminId = (UUID) auth.getPrincipal();
        PaymentBatchResponse response = paymentService.recordPayment(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * Admin confirms they received cash from the worker at end of day.
     * FIFO is applied here — this is when the member's payment is officially credited.
     */
    @PostMapping("/{batchId}/remit")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PaymentBatchResponse>> remitCash(
            @PathVariable UUID batchId,
            Authentication auth) {
        UUID adminId = (UUID) auth.getPrincipal();
        PaymentBatchResponse response = paymentService.remitCash(batchId, adminId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * All AWAITING_REMITTANCE batches — admin sees which workers still hold cash.
     */
    @GetMapping("/pending-remittance")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<PaymentBatchResponse>>> getPendingRemittances() {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPendingRemittances()));
    }

    /**
     * Member's outstanding balance for a chit: total owed + breakdown by month (FIFO order).
     * Response: { totalOutstanding: ₹6000, months: [{month:2, balance:₹1000}, {month:3, balance:₹5000}] }
     */
    @GetMapping("/balance")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_WORKER')")
    public ResponseEntity<ApiResponse<MemberBalanceResponse>> getMemberBalance(
            @RequestParam UUID memberId,
            @RequestParam UUID chitId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getMemberBalance(memberId, chitId)));
    }
}
