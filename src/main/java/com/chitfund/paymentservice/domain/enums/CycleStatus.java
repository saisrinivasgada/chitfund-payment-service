package com.chitfund.paymentservice.domain.enums;

public enum CycleStatus {
    OPEN,    // admin opened this month; payment records created for all members
    CLOSED,  // admin explicitly closed this month; collection period is over
    SKIPPED  // admin skipped this month (e.g. COVID); waived records created; chit end-date extends
}
