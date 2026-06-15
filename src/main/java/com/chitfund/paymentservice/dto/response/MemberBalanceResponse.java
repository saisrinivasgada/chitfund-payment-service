package com.chitfund.paymentservice.dto.response;

import com.chitfund.paymentservice.domain.enums.PaymentRecordStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MemberBalanceResponse {

    private UUID memberId;
    private UUID chitId;
    private BigDecimal totalOutstanding;    // sum of (amountDue - amountPaid) for non-SETTLED records
    private List<MonthBalance> months;      // breakdown per month, oldest first

    @Data
    @Builder
    public static class MonthBalance {
        private int monthNumber;
        private LocalDate dueDate;
        private BigDecimal amountDue;
        private BigDecimal amountPaid;
        private BigDecimal balance;          // amountDue - amountPaid
        private PaymentRecordStatus status;
    }
}
