package com.sewamobil.controller;

import com.sewamobil.dto.CarRequest;
import com.sewamobil.dto.CarResponse;
import com.sewamobil.enums.CarStatus;
import com.sewamobil.service.CarService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;

@RestController
@RequestMapping("/api/cars")
@Tag(name = "API Cars", description = "Endpoints for creating and retrieving cars")
public class CarController {

    private final CarService carService;

    public CarController(CarService carService) {
        this.carService = carService;
    }

    @GetMapping
    public List<CarResponse> findAll(@RequestParam(required = false) CarStatus status) {
        return carService.findAll(status);
    }

    @GetMapping("/{id}")
    public CarResponse findById(@PathVariable Long id) {
        return carService.findById(id);
    }

    @PostMapping
    public ResponseEntity<CarResponse> create(@Valid @RequestBody CarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(carService.create(request));
    }

    @PatchMapping("/{id}/status")
    public CarResponse updateStatus(@PathVariable Long id, @RequestParam CarStatus status) {
        return carService.updateStatus(id, status);
    }
}
