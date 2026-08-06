package com.sewamobil.service;

import com.sewamobil.dto.AuthResponse;
import com.sewamobil.dto.LoginRequest;
import com.sewamobil.dto.RegisterRequest;
import com.sewamobil.dto.UserResponse;
import com.sewamobil.entity.UserAccount;
import com.sewamobil.enums.Role;
import com.sewamobil.exception.BadRequestException;
import com.sewamobil.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email sudah terdaftar");
        }

        UserAccount user = new UserAccount();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.CUSTOMER);

        UserAccount saved = userRepository.save(user);
        return new AuthResponse(tokenFor(saved), UserResponse.from(saved));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserAccount user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadRequestException("Email atau password salah"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadRequestException("Email atau password salah");
        }

        return new AuthResponse(tokenFor(user), UserResponse.from(user));
    }

    private String tokenFor(UserAccount user) {
        return "demo-token-" + user.getId() + "-" + user.getRole().name().toLowerCase();
    }
}
