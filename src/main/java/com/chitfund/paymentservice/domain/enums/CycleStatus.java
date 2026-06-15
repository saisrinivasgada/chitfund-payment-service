package com.chitfund.paymentservice.domain.enums;

public enum CycleStatus {
    OPEN,   // admin opened this month; payment records created for all members
    SKIPPED // admin skipped this month (e.g. COVID); waived records created; chit end-date extends
}
