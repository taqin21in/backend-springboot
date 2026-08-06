package com.sewamobil.repository;

import com.sewamobil.entity.Car;
import com.sewamobil.enums.CarStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarRepository extends JpaRepository<Car, Long> {
    List<Car> findByStatus(CarStatus status);

    boolean existsByPlateNumber(String plateNumber);
}
