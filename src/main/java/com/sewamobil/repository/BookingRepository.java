package com.sewamobil.repository;

import com.sewamobil.entity.Booking;
import com.sewamobil.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUser_EmailOrderByCreatedAtDesc(String email);

    boolean existsByCar_IdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long carId,
            Collection<BookingStatus> statuses,
            LocalDate requestedEndDate,
            LocalDate requestedStartDate
    );
}
