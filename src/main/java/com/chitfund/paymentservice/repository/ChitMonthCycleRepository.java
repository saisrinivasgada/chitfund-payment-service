package com.chitfund.paymentservice.repository;

import com.chitfund.paymentservice.domain.ChitMonthCycle;
import com.chitfund.paymentservice.domain.enums.CycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChitMonthCycleRepository extends JpaRepository<ChitMonthCycle, UUID> {

    boolean existsByChitIdAndMonthNumber(UUID chitId, int monthNumber);

    Optional<ChitMonthCycle> findByChitIdAndMonthNumber(UUID chitId, int monthNumber);

    List<ChitMonthCycle> findByStatusOrderByDueDateAsc(CycleStatus status);

    List<ChitMonthCycle> findByChitIdOrderByMonthNumberAsc(UUID chitId);
}
