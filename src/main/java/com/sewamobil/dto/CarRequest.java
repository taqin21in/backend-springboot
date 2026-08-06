package com.sewamobil.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CarRequest(
        @NotBlank String brand,
        @NotBlank String model,
        @NotBlank String plateNumber,
        @Min(1990) int year,
        String imageUrl,
        @Min(2) int seats,
        @NotBlank String transmission,
        @NotNull @DecimalMin("1.00") BigDecimal pricePerDay,
        String description
) {
}
