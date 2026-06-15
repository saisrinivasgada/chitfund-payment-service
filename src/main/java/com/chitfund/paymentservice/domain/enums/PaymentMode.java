package com.chitfund.paymentservice.domain.enums;

public enum PaymentMode {
    CASH,          // two-step: worker collects → admin remits
    UPI,           // direct transfer, completed immediately
    BANK_TRANSFER, // direct transfer, completed immediately
    CHEQUE         // completed immediately (caller verifies clearance offline)
}
