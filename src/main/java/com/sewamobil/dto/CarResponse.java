package com.sewamobil.dto;

import com.sewamobil.entity.Car;
import com.sewamobil.enums.CarStatus;

import java.math.BigDecimal;

public record CarResponse(
        Long id,
        String brand,
        String model,
        String plateNumber,
        int year,
        String imageUrl,
        int seats,
        String transmission,
        BigDecimal pricePerDay,
        CarStatus status,
        String description
) {
    public static CarResponse from(Car car) {
        return new CarResponse(
                car.getId(),
                car.getBrand(),
                car.getModel(),
                car.getPlateNumber(),
                car.getYear(),
                car.getImageUrl(),
                car.getSeats(),
                car.getTransmission(),
                car.getPricePerDay(),
                car.getStatus(),
                car.getDescription()
        );
    }
}
