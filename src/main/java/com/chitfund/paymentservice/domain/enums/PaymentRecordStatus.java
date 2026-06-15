package com.chitfund.paymentservice.domain.enums;

public enum PaymentRecordStatus {
    OUTSTANDING,    // nothing paid yet
    PARTIALLY_PAID, // some amount paid, balance still remaining
    SETTLED,        // fully paid — amount_paid == amount_due
    WAIVED          // month was skipped; no payment required; amount_due shows what was forgiven
}
