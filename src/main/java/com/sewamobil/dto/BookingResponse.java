package com.sewamobil.dto;

import com.sewamobil.entity.Booking;
import com.sewamobil.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
        Long id,
        UserResponse user,
        CarResponse car,
        LocalDate startDate,
        LocalDate endDate,
        String pickupLocation,
        String returnLocation,
        BigDecimal totalPrice,
        BookingStatus status,
        Instant createdAt
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                UserResponse.from(booking.getUser()),
                CarResponse.from(booking.getCar()),
                booking.getStartDate(),
                booking.getEndDate(),
                booking.getPickupLocation(),
                booking.getReturnLocation(),
                booking.getTotalPrice(),
                booking.getStatus(),
                booking.getCreatedAt()
        );
    }
}
