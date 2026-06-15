package com.chitfund.paymentservice.repository;

import com.chitfund.paymentservice.domain.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {

    List<PaymentAllocation> findByBatchId(UUID batchId);

    List<PaymentAllocation> findByPaymentRecordId(UUID paymentRecordId);
}
