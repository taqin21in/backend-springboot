package com.sewamobil.service;

import com.sewamobil.dto.AuthResponse;
import com.sewamobil.dto.LoginRequest;
import com.sewamobil.dto.RegisterRequest;
import com.sewamobil.entity.UserAccount;
import com.sewamobil.enums.Role;
import com.sewamobil.exception.BadRequestException;
import com.sewamobil.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void registerCreatesCustomerWithHashedPassword() {
        RegisterRequest request = new RegisterRequest(
                "Budi Santoso",
                "budi@example.com",
                "081234567890",
                "customer123"
        );
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            user.setId(7L);
            return user;
        });

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("demo-token-7-customer");
        assertThat(response.user().email()).isEqualTo("budi@example.com");
        assertThat(response.user().role()).isEqualTo(Role.CUSTOMER);

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-password");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                "Budi Santoso",
                "budi@example.com",
                "081234567890",
                "customer123"
        );
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email sudah terdaftar");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void loginReturnsSessionWhenPasswordMatches() {
        UserAccount user = customer();
        when(userRepository.findByEmail("budi@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("customer123", "hashed-password")).thenReturn(true);

        AuthResponse response = authService.login(
                new LoginRequest("budi@example.com", "customer123")
        );

        assertThat(response.token()).isEqualTo("demo-token-2-customer");
        assertThat(response.user().fullName()).isEqualTo("Budi Santoso");
    }

    @Test
    void loginRejectsInvalidPassword() {
        UserAccount user = customer();
        when(userRepository.findByEmail("budi@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("budi@example.com", "wrong-password")
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email atau password salah");
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
}
