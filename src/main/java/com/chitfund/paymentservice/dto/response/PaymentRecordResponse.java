package com.chitfund.paymentservice.dto.response;

import com.chitfund.paymentservice.domain.enums.PaymentRecordStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PaymentRecordResponse {
    private UUID id;
    private UUID chitId;
    private UUID memberId;
    private int monthNumber;
    private LocalDate dueDate;
    private BigDecimal amountDue;
    private BigDecimal amountPaid;
    private BigDecimal balance;
    private PaymentRecordStatus status;
    private boolean overdue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
