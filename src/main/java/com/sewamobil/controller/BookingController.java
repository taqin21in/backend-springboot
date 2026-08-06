package com.sewamobil.controller;

import com.sewamobil.dto.BookingRequest;
import com.sewamobil.dto.BookingResponse;
import com.sewamobil.dto.PaymentRequest;
import com.sewamobil.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
    }

    @GetMapping
    public List<BookingResponse> findAll() {
        return bookingService.findAll();
    }

    @GetMapping("/user/{email}")
    public List<BookingResponse> findByUserEmail(@PathVariable String email) {
        return bookingService.findByUserEmail(email);
    }

    @PostMapping("/{id}/pay")
    public BookingResponse pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return bookingService.pay(id, request);
    }

    @PatchMapping("/{id}/activate")
    public BookingResponse activate(@PathVariable Long id) {
        return bookingService.activate(id);
    }

    @PatchMapping("/{id}/complete")
    public BookingResponse complete(@PathVariable Long id) {
        return bookingService.complete(id);
    }

    @PatchMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id) {
        return bookingService.cancel(id);
    }
}
