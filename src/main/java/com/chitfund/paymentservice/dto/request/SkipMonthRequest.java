package com.chitfund.paymentservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class SkipMonthRequest {

    @NotNull
    private UUID chitId;

    @NotNull
    @Min(1)
    private Integer monthNumber;

    @NotNull
    private LocalDate dueDate;

    // Stored even when skipped so reports can show "₹X was waived in month Y"
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal installmentAmount;

    @NotNull
    @Size(min = 3, max = 500, message = "Skip reason must be between 3 and 500 characters")
    private String skipReason;

    @NotNull
    @Size(min = 1, message = "At least one member ID is required")
    private List<UUID> memberIds;
}
