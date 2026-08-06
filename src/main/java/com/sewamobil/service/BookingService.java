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
import com.sewamobil.exception.BadRequestException;
import com.sewamobil.exception.ResourceNotFoundException;
import com.sewamobil.repository.BookingRepository;
import com.sewamobil.repository.CarRepository;
import com.sewamobil.repository.PaymentRepository;
import com.sewamobil.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BookingService {

    private static final List<BookingStatus> BLOCKING_STATUSES = List.of(
            BookingStatus.PENDING,
            BookingStatus.CONFIRMED,
            BookingStatus.ACTIVE
    );

    private final BookingRepository bookingRepository;
    private final UserAccountRepository userRepository;
    private final CarRepository carRepository;
    private final PaymentRepository paymentRepository;

    public BookingService(
            BookingRepository bookingRepository,
            UserAccountRepository userRepository,
            CarRepository carRepository,
            PaymentRepository paymentRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.carRepository = carRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public BookingResponse create(BookingRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("Tanggal selesai tidak boleh sebelum tanggal mulai");
        }

        UserAccount user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));
        Car car = carRepository.findById(request.carId())
                .orElseThrow(() -> new ResourceNotFoundException("Mobil tidak ditemukan"));

        if (car.getStatus() != CarStatus.AVAILABLE) {
            throw new BadRequestException("Mobil sedang tidak tersedia");
        }

        boolean overlaps = bookingRepository.existsByCar_IdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                car.getId(),
                BLOCKING_STATUSES,
                request.endDate(),
                request.startDate()
        );

        if (overlaps) {
            throw new BadRequestException("Mobil sudah dipesan pada rentang tanggal tersebut");
        }

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setCar(car);
        booking.setStartDate(request.startDate());
        booking.setEndDate(request.endDate());
        booking.setPickupLocation(request.pickupLocation());
        booking.setReturnLocation(request.returnLocation());
        booking.setTotalPrice(calculateTotal(car, request.startDate(), request.endDate()));
        booking.setStatus(BookingStatus.PENDING);

        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findAll() {
        return bookingRepository.findAll().stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findByUserEmail(String email) {
        return bookingRepository.findByUser_EmailOrderByCreatedAtDesc(email).stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Transactional
    public BookingResponse pay(Long bookingId, PaymentRequest request) {
        Booking booking = findEntity(bookingId);

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BadRequestException("Booking tidak dapat dibayar");
        }

        Payment payment = paymentRepository.findByBooking_Id(bookingId).orElseGet(Payment::new);
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalPrice());
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);

        booking.setStatus(BookingStatus.CONFIRMED);

        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse activate(Long bookingId) {
        Booking booking = findEntity(bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BadRequestException("Booking harus berstatus CONFIRMED");
        }

        booking.setStatus(BookingStatus.ACTIVE);
        booking.getCar().setStatus(CarStatus.RENTED);
        carRepository.save(booking.getCar());
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse complete(Long bookingId) {
        Booking booking = findEntity(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Booking yang dibatalkan tidak dapat diselesaikan");
        }

        booking.setStatus(BookingStatus.COMPLETED);
        booking.getCar().setStatus(CarStatus.AVAILABLE);
        carRepository.save(booking.getCar());
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse cancel(Long bookingId) {
        Booking booking = findEntity(bookingId);
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BadRequestException("Booking selesai tidak dapat dibatalkan");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.getCar().setStatus(CarStatus.AVAILABLE);
        carRepository.save(booking.getCar());
        return BookingResponse.from(bookingRepository.save(booking));
    }

    private Booking findEntity(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking tidak ditemukan"));
    }

    private BigDecimal calculateTotal(Car car, LocalDate startDate, LocalDate endDate) {
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return car.getPricePerDay().multiply(BigDecimal.valueOf(days));
    }
}
