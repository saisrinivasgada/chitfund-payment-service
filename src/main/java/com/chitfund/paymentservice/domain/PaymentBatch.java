package com.chitfund.paymentservice.domain;

import com.chitfund.paymentservice.domain.enums.BatchStatus;
import com.chitfund.paymentservice.domain.enums.PaymentMode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_batches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID chitId;

    @Column(nullable = false)
    private UUID memberId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private PaymentMode paymentMode;

    // WHY varchar(25)? AWAITING_REMITTANCE = 20 chars, needs room.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(25)")
    private BatchStatus status;

    private UUID collectedBy;       // worker who collected (CASH only)
    private LocalDateTime collectedAt;
    private LocalDateTime remittedAt;
    private UUID remittedBy;        // admin who confirmed receipt (CASH only)

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
