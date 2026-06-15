package com.chitfund.paymentservice.domain.enums;

public enum BatchStatus {
    AWAITING_REMITTANCE, // cash collected by worker, not yet handed to admin — FIFO not yet applied
    COMPLETED            // payment confirmed; FIFO applied; payment_records updated
}
