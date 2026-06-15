package com.chitfund.paymentservice.repository;

import com.chitfund.paymentservice.domain.PaymentBatch;
import com.chitfund.paymentservice.domain.enums.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentBatchRepository extends JpaRepository<PaymentBatch, UUID> {

    // Admin dashboard: show all cash collections waiting to be remitted
    List<PaymentBatch> findByStatusOrderByCreatedAtAsc(BatchStatus status);

    // Member payment history for a specific chit
    List<PaymentBatch> findByMemberIdAndChitIdOrderByCreatedAtDesc(UUID memberId, UUID chitId);
}
