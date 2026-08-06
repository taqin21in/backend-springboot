package com.sewamobil.service;

import com.sewamobil.dto.BookingRequest;
import com.sewamobil.dto.BookingResponse;
import com.sewamobil.dto.PaymentRequest;
import com.sewamobil.entity.Booking;
import com.sewamobil.entity.Car;
import com.sewamobil.entity.Payment;
import com.sewamobil.entity.UserAccount;
import com.sewamobil.enums.BookingStatus;
import com.sewamobil.enums.CarStatus;
import com.sewamobil.enums.PaymentStatus;
import com.sewamobil.enums.Role;
import com.sewamobil.exception.BadRequestException;
import com.sewamobil.repository.BookingRepository;
import com.sewamobil.repository.CarRepository;
import com.sewamobil.repository.PaymentRepository;
import com.sewamobil.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private CarRepository carRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingRepository,
                userRepository,
                carRepository,
                paymentRepository
        );
    }

    @Test
    void createCalculatesInclusiveTotalAndPersistsPendingBooking() {
        UserAccount user = customer();
        Car car = availableCar();
        BookingRequest request = request();

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(carRepository.findById(1L)).thenReturn(Optional.of(car));
        when(bookingRepository.existsByCar_IdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                any(),
                anyCollection(),
                any(),
                any()
        )).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(20L);
            return booking;
        });

        BookingResponse response = bookingService.create(request);

        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.totalPrice()).isEqualByComparingTo("1350000");

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getStartDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(captor.getValue().getEndDate()).isEqualTo(LocalDate.of(2026, 6, 3));
    }

    @Test
    void createRejectsOverlappingBookingDates() {
        UserAccount user = customer();
        Car car = availableCar();
        BookingRequest request = request();

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(carRepository.findById(1L)).thenReturn(Optional.of(car));
        when(bookingRepository.existsByCar_IdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                any(),
                anyCollection(),
                any(),
                any()
        )).thenReturn(true);

        assertThatThrownBy(() -> bookingService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Mobil sudah dipesan pada rentang tanggal tersebut");
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        BookingRequest request = new BookingRequest(
                2L,
                1L,
                LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 6, 1),
                "Kantor Jakarta",
                "Kantor Jakarta"
        );

        assertThatThrownBy(() -> bookingService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tanggal selesai tidak boleh sebelum tanggal mulai");
    }

    @Test
    void payCreatesPaidPaymentAndConfirmsBooking() {
        Booking booking = pendingBooking();
        when(bookingRepository.findById(20L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking_Id(20L)).thenReturn(Optional.empty());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.pay(20L, new PaymentRequest("TRANSFER"));

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getCar().getStatus()).isEqualTo(CarStatus.AVAILABLE);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo("1350000");
        assertThat(paymentCaptor.getValue().getMethod()).isEqualTo("TRANSFER");
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(carRepository, never()).save(any(Car.class));
    }

    private BookingRequest request() {
        return new BookingRequest(
                2L,
                1L,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                "Kantor Jakarta",
                "Kantor Jakarta"
        );
    }

    private Booking pendingBooking() {
        Booking booking = new Booking();
        booking.setId(20L);
        booking.setUser(customer());
        booking.setCar(availableCar());
        booking.setStartDate(LocalDate.of(2026, 6, 1));
        booking.setEndDate(LocalDate.of(2026, 6, 3));
        booking.setPickupLocation("Kantor Jakarta");
        booking.setReturnLocation("Kantor Jakarta");
        booking.setTotalPrice(new BigDecimal("1350000"));
        booking.setStatus(BookingStatus.PENDING);
        return booking;
    }

    private UserAccount customer() {
        UserAccount user = new UserAccount();
        user.setId(2L);
        user.setFullName("Budi Santoso");
        user.setEmail("budi@example.com");
        user.setPhone("081234567890");
        user.setPasswordHash("hashed-password");
        user.setRole(Role.CUSTOMER);
        return user;
    }

    private Car availableCar() {
        Car car = new Car();
        car.setId(1L);
        car.setBrand("Toyota");
        car.setModel("Avanza");
        car.setPlateNumber("B 1234 SEA");
        car.setYear(2022);
        car.setSeats(7);
        car.setTransmission("Automatic");
        car.setPricePerDay(new BigDecimal("450000"));
        car.setStatus(CarStatus.AVAILABLE);
        car.setDescription("MPV keluarga");
        return car;
    }
}
