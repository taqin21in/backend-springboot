package com.sewamobil.config;

import com.sewamobil.entity.Car;
import com.sewamobil.entity.UserAccount;
import com.sewamobil.enums.CarStatus;
import com.sewamobil.enums.Role;
import com.sewamobil.repository.CarRepository;
import com.sewamobil.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedData(
            UserAccountRepository users,
            CarRepository cars,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (!users.existsByEmail("admin@sewamobil.test")) {
                UserAccount admin = new UserAccount();
                admin.setFullName("Admin Sewa Mobil");
                admin.setEmail("admin@sewamobil.test");
                admin.setPhone("081200000001");
                admin.setPasswordHash(passwordEncoder.encode("admin123"));
                admin.setRole(Role.ADMIN);
                users.save(admin);
            }

            if (!users.existsByEmail("budi@example.com")) {
                UserAccount customer = new UserAccount();
                customer.setFullName("Budi Santoso");
                customer.setEmail("budi@example.com");
                customer.setPhone("081234567890");
                customer.setPasswordHash(passwordEncoder.encode("customer123"));
                customer.setRole(Role.CUSTOMER);
                users.save(customer);
            }

            if (cars.count() == 0) {
                cars.saveAll(List.of(
                        car("Toyota", "Avanza", "B 1234 SEA", 2022, 7, "Automatic", "https://images.unsplash.com/photo-1549317661-bd32c8ce0db2", new BigDecimal("450000"), "MPV keluarga irit untuk perjalanan kota dan luar kota."),
                        car("Honda", "Brio", "B 2211 MOB", 2023, 5, "Automatic", "https://images.unsplash.com/photo-1503736334956-4c8f8e92946d", new BigDecimal("350000"), "City car ringkas, cocok untuk perjalanan harian."),
                        car("Mitsubishi", "Xpander", "D 8877 RENT", 2021, 7, "Manual", "https://images.unsplash.com/photo-1533473359331-0135ef1b58bf", new BigDecimal("500000"), "Kabin lega dengan suspensi nyaman."),
                        car("Toyota", "Fortuner", "B 9001 SUV", 2022, 7, "Automatic", "https://images.unsplash.com/photo-1519641471654-76ce0107ad1b", new BigDecimal("900000"), "SUV premium untuk perjalanan bisnis dan wisata.")
                ));
            }
        };
    }

    private Car car(
            String brand,
            String model,
            String plateNumber,
            int year,
            int seats,
            String transmission,
            String imageUrl,
            BigDecimal price,
            String description
    ) {
        Car car = new Car();
        car.setBrand(brand);
        car.setModel(model);
        car.setPlateNumber(plateNumber);
        car.setYear(year);
        car.setSeats(seats);
        car.setTransmission(transmission);
        car.setImageUrl(imageUrl);
        car.setPricePerDay(price);
        car.setStatus(CarStatus.AVAILABLE);
        car.setDescription(description);
        return car;
    }
}
