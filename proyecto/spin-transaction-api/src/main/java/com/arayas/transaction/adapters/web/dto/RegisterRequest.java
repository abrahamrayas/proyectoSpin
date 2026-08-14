package com.arayas.transaction.adapters.web.dto;

public record RegisterRequest(
        String username,
        String email,
        String password
) {
}
