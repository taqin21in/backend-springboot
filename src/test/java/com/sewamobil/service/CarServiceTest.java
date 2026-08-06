package com.sewamobil.service;

import com.sewamobil.dto.CarRequest;
import com.sewamobil.dto.CarResponse;
import com.sewamobil.entity.Car;
import com.sewamobil.enums.CarStatus;
import com.sewamobil.exception.BadRequestException;
import com.sewamobil.exception.ResourceNotFoundException;
import com.sewamobil.repository.CarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarServiceTest {

    @Mock
    private CarRepository carRepository;

    private CarService carService;

    @BeforeEach
    void setUp() {
        carService = new CarService(carRepository);
    }

    @Test
    void findAllReturnsCarsByStatusWhenFilterProvided() {
        Car car = car(1L, "Toyota", "Avanza", CarStatus.AVAILABLE);
        when(carRepository.findByStatus(CarStatus.AVAILABLE)).thenReturn(List.of(car));

        List<CarResponse> responses = carService.findAll(CarStatus.AVAILABLE);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).model()).isEqualTo("Avanza");
        verify(carRepository).findByStatus(CarStatus.AVAILABLE);
    }

    @Test
    void createPersistsAvailableCar() {
        CarRequest request = request();
        when(carRepository.existsByPlateNumber(request.plateNumber())).thenReturn(false);
        when(carRepository.save(any(Car.class))).thenAnswer(invocation -> {
            Car saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        CarResponse response = carService.create(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(CarStatus.AVAILABLE);

        ArgumentCaptor<Car> captor = ArgumentCaptor.forClass(Car.class);
        verify(carRepository).save(captor.capture());
        assertThat(captor.getValue().getPlateNumber()).isEqualTo("B 1234 SEA");
        assertThat(captor.getValue().getPricePerDay()).isEqualByComparingTo("450000");
    }

    @Test
    void createRejectsDuplicatePlateNumber() {
        CarRequest request = request();
        when(carRepository.existsByPlateNumber(request.plateNumber())).thenReturn(true);

        assertThatThrownBy(() -> carService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Nomor polisi sudah digunakan");
    }

    @Test
    void findByIdThrowsWhenCarDoesNotExist() {
        when(carRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> carService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Mobil tidak ditemukan");
    }

    private CarRequest request() {
        return new CarRequest(
                "Toyota",
                "Avanza",
                "B 1234 SEA",
                2022,
                "https://example.com/avanza.jpg",
                7,
                "Automatic",
                new BigDecimal("450000"),
                "MPV keluarga"
        );
    }

    private Car car(Long id, String brand, String model, CarStatus status) {
        Car car = new Car();
        car.setId(id);
        car.setBrand(brand);
        car.setModel(model);
        car.setPlateNumber("B 1234 SEA");
        car.setYear(2022);
        car.setSeats(7);
        car.setTransmission("Automatic");
        car.setPricePerDay(new BigDecimal("450000"));
        car.setStatus(status);
        car.setDescription("MPV keluarga");
        return car;
    }
}
