package com.chitfund.paymentservice.repository;

import com.chitfund.paymentservice.domain.PaymentRecord;
import com.chitfund.paymentservice.domain.enums.PaymentRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {

    // FIFO query — oldest month first, only OUTSTANDING or PARTIALLY_PAID
    List<PaymentRecord> findByMemberIdAndChitIdAndStatusInOrderByMonthNumberAsc(
            UUID memberId, UUID chitId, List<PaymentRecordStatus> statuses);

    // All records for a member in a chit — includes SETTLED and WAIVED
    List<PaymentRecord> findByMemberIdAndChitIdOrderByMonthNumberAsc(UUID memberId, UUID chitId);

    // All records for a given cycle (used in dashboard stats)
    List<PaymentRecord> findByChitIdAndMonthNumber(UUID chitId, int monthNumber);
}
