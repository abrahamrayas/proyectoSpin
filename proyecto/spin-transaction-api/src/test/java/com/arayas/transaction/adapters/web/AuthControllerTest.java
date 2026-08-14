package com.arayas.transaction.adapters.web;

import com.arayas.transaction.adapters.web.dto.LoginRequest;
import com.arayas.transaction.adapters.web.dto.LoginResponse;
import com.arayas.transaction.adapters.web.dto.RegisterRequest;
import com.arayas.transaction.application.service.AuthService;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @MockitoBean
    private AuthService authService;

    @Test
    void register_shouldReturnCreated() throws Exception {

        // Arrange
        RegisterRequest request = new RegisterRequest(
                "abraham",
                "abraham@test.com",
                "Password123!"
        );

        LoginResponse response =
                new LoginResponse("jwt-token");

        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.token").value("jwt-token"));

        verify(authService)
                .register(any(RegisterRequest.class));
    }

    @Test
    void login_shouldReturnOk() throws Exception {

        // Arrange
        LoginRequest request = new LoginRequest(
                "abraham",
                "Password123!"
        );

        LoginResponse response =
                new LoginResponse("jwt-token");

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.token").value("jwt-token"));

        verify(authService)
                .login(any(LoginRequest.class));
    }

    @Test
    void register_shouldPassRequestToService() throws Exception {

        // Arrange
        RegisterRequest request = new RegisterRequest(
                "abraham",
                "abraham@test.com",
                "Password123!"
        );

        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new LoginResponse("jwt-token"));

        // Act
        mockMvc.perform(
                post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        );

        // Assert
        verify(authService).register(
                argThat(r ->
                        r.username().equals("abraham")
                                && r.email().equals("abraham@test.com")
                                && r.password().equals("Password123!")
                )
        );
    }

    @Test
    void login_shouldPassRequestToService() throws Exception {

        // Arrange
        LoginRequest request = new LoginRequest(
                "abraham",
                "Password123!"
        );

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse("jwt-token"));

        // Act
        mockMvc.perform(
                post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        );

        // Assert
        verify(authService).login(
                argThat(r ->
                        r.username().equals("abraham")
                                && r.password().equals("Password123!")
                )
        );
    }
}