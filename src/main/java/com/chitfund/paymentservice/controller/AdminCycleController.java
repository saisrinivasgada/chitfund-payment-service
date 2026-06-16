package com.chitfund.paymentservice.controller;

import com.chitfund.common.dto.ApiResponse;
import com.chitfund.paymentservice.dto.request.OpenMonthRequest;
import com.chitfund.paymentservice.dto.request.SkipMonthRequest;
import com.chitfund.paymentservice.dto.response.CycleSummaryResponse;
import com.chitfund.paymentservice.service.ChitMonthCycleService;
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
@RequestMapping("/admin/cycles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminCycleController {

    private final ChitMonthCycleService cycleService;

    /**
     * Admin dashboard: shows all OPEN cycles with payment progress.
     * The UI uses this to show: "Chit X / Month 3 — 8 paid, 4 outstanding, due 2026-07-01"
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<List<CycleSummaryResponse>>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(cycleService.getDashboard()));
    }

    /**
     * Admin opens a month: creates payment records for all enrolled members.
     * Call this when the month starts and you're ready to collect installments.
     */
    @PostMapping("/open")
    public ResponseEntity<ApiResponse<CycleSummaryResponse>> openMonth(
            @Valid @RequestBody OpenMonthRequest request,
            Authentication auth) {
        UUID adminId = (UUID) auth.getPrincipal();
        CycleSummaryResponse response = cycleService.openMonth(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * Admin skips a month (e.g. COVID, festival, admin decision).
     * - Creates WAIVED payment records for all members (for audit/reporting)
     * - Publishes ChitMonthSkippedEvent → notifies members + workers + extends chit end date
     */
    @PostMapping("/skip")
    public ResponseEntity<ApiResponse<CycleSummaryResponse>> skipMonth(
            @Valid @RequestBody SkipMonthRequest request,
            Authentication auth) {
        UUID adminId = (UUID) auth.getPrincipal();
        CycleSummaryResponse response = cycleService.skipMonth(request, adminId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Admin closes an open month — marks collection period as over.
     * Works even if some members are still OUTSTANDING (force-close).
     */
    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<CycleSummaryResponse>> closeMonth(
            @PathVariable UUID id,
            Authentication auth) {
        UUID adminId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(cycleService.closeMonth(id, adminId)));
    }

    /**
     * All cycles (open + skipped + closed) for a specific chit — used for chit-level audit view.
     */
    @GetMapping("/chit/{chitId}")
    public ResponseEntity<ApiResponse<List<CycleSummaryResponse>>> getCyclesForChit(
            @PathVariable UUID chitId) {
        return ResponseEntity.ok(ApiResponse.success(cycleService.getCyclesForChit(chitId)));
    }
}
