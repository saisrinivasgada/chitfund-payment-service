package com.chitfund.paymentservice.dto.response;

import com.chitfund.paymentservice.domain.enums.BatchStatus;
import com.chitfund.paymentservice.domain.enums.PaymentMode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PaymentBatchResponse {

    private UUID id;
    private UUID chitId;
    private UUID memberId;
    private BigDecimal totalAmount;
    private PaymentMode paymentMode;
    private BatchStatus status;

    private UUID collectedBy;
    private LocalDateTime collectedAt;
    private LocalDateTime remittedAt;
    private UUID remittedBy;

    private String notes;
    private LocalDateTime createdAt;

    // How this batch's amount was distributed across months (FIFO order)
    private List<AllocationDetail> allocations;

    @Data
    @Builder
    public static class AllocationDetail {
        private int monthNumber;
        private BigDecimal allocatedAmount;
        private UUID paymentRecordId;
    }
}
