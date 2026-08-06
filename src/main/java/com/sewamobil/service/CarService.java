package com.sewamobil.service;

import com.sewamobil.dto.CarRequest;
import com.sewamobil.dto.CarResponse;
import com.sewamobil.entity.Car;
import com.sewamobil.enums.CarStatus;
import com.sewamobil.exception.BadRequestException;
import com.sewamobil.exception.ResourceNotFoundException;
import com.sewamobil.repository.CarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CarService {

    private final CarRepository carRepository;

    public CarService(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    @Transactional(readOnly = true)
    public List<CarResponse> findAll(CarStatus status) {
        List<Car> cars = status == null ? carRepository.findAll() : carRepository.findByStatus(status);
        return cars.stream().map(CarResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CarResponse findById(Long id) {
        return CarResponse.from(findEntity(id));
    }

    @Transactional
    public CarResponse create(CarRequest request) {
        if (carRepository.existsByPlateNumber(request.plateNumber())) {
            throw new BadRequestException("Nomor polisi sudah digunakan");
        }

        Car car = new Car();
        applyRequest(car, request);
        car.setStatus(CarStatus.AVAILABLE);
        return CarResponse.from(carRepository.save(car));
    }

    @Transactional
    public CarResponse updateStatus(Long id, CarStatus status) {
        Car car = findEntity(id);
        car.setStatus(status);
        return CarResponse.from(carRepository.save(car));
    }

    public Car findEntity(Long id) {
        return carRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mobil tidak ditemukan"));
    }

    private void applyRequest(Car car, CarRequest request) {
        car.setBrand(request.brand());
        car.setModel(request.model());
        car.setPlateNumber(request.plateNumber());
        car.setYear(request.year());
        car.setImageUrl(request.imageUrl());
        car.setSeats(request.seats());
        car.setTransmission(request.transmission());
        car.setPricePerDay(request.pricePerDay());
        car.setDescription(request.description());
    }
}
