package com.sewamobil.dto;

import com.sewamobil.entity.UserAccount;
import com.sewamobil.enums.Role;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        Role role
) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole()
        );
    }
}
