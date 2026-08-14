package com.arayas.transaction.application.service;

import com.arayas.transaction.adapters.persistence.entity.UserEntity;
import com.arayas.transaction.adapters.persistence.repository.UserRepository;
import com.arayas.transaction.adapters.web.dto.LoginRequest;
import com.arayas.transaction.adapters.web.dto.LoginResponse;
import com.arayas.transaction.adapters.web.dto.RegisterRequest;
import com.arayas.transaction.application.model.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)


@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {

        registerRequest = new RegisterRequest(
                "abraham",
                "abraham@test.com",
                "Password123!"
        );

        loginRequest = new LoginRequest(
                "abraham",
                "Password123!"
        );
    }

    @Test
    void register_shouldCreateUserAndReturnToken() {

        // Arrange
        when(userRepository.findByUsername("abraham"))
                .thenReturn(Optional.empty());

        when(userRepository.findByEmail("abraham@test.com"))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode("Password123!"))
                .thenReturn("encoded-password");

        when(jwtService.generateToken(any(UserEntity.class)))
                .thenReturn("jwt-token");

        // Act
        LoginResponse response =
                authService.register(registerRequest);

        // Assert
        assertNotNull(response);
        assertEquals("jwt-token", response.token());

        verify(userRepository)
                .findByUsername("abraham");

        verify(userRepository)
                .findByEmail("abraham@test.com");

        verify(passwordEncoder)
                .encode("Password123!");

        verify(userRepository)
                .save(any(UserEntity.class));

        verify(jwtService)
                .generateToken(any(UserEntity.class));
    }

    @Test
    void register_shouldSaveEncodedPassword() {

        // Arrange
        when(userRepository.findByUsername("abraham"))
                .thenReturn(Optional.empty());

        when(userRepository.findByEmail("abraham@test.com"))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode("Password123!"))
                .thenReturn("encoded-password");

        when(jwtService.generateToken(any(UserEntity.class)))
                .thenReturn("jwt-token");

        // Act
        authService.register(registerRequest);

        // Assert
        ArgumentCaptor<UserEntity> captor =
                ArgumentCaptor.forClass(UserEntity.class);

        verify(userRepository).save(captor.capture());

        UserEntity savedUser = captor.getValue();

        assertEquals(
                "encoded-password",
                savedUser.getPassword()
        );

        assertNotEquals(
                "Password123!",
                savedUser.getPassword()
        );
    }

    @Test
    void register_shouldSetUserRoleAndEnableUser() {

        // Arrange
        when(userRepository.findByUsername("abraham"))
                .thenReturn(Optional.empty());

        when(userRepository.findByEmail("abraham@test.com"))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode(anyString()))
                .thenReturn("encoded-password");

        when(jwtService.generateToken(any(UserEntity.class)))
                .thenReturn("jwt-token");

        // Act
        authService.register(registerRequest);

        // Assert
        ArgumentCaptor<UserEntity> captor =
                ArgumentCaptor.forClass(UserEntity.class);

        verify(userRepository).save(captor.capture());

        UserEntity savedUser = captor.getValue();

        assertEquals(Role.USER, savedUser.getRole());
        assertTrue(savedUser.isEnabled());
    }

    @Test
    void register_shouldThrowExceptionWhenUsernameAlreadyExists() {

        // Arrange
        UserEntity existingUser = new UserEntity();
        existingUser.setUsername("abraham");

        when(userRepository.findByUsername("abraham"))
                .thenReturn(Optional.of(existingUser));

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> authService.register(registerRequest)
                );

        assertEquals(
                "Username already exists",
                exception.getMessage()
        );

        verify(userRepository)
                .findByUsername("abraham");

        verify(userRepository, never())
                .findByEmail(anyString());

        verify(userRepository, never())
                .save(any(UserEntity.class));

        verify(passwordEncoder, never())
                .encode(anyString());

        verify(jwtService, never())
                .generateToken(any(UserEntity.class));
    }

    @Test
    void register_shouldThrowExceptionWhenEmailAlreadyExists() {

        // Arrange
        when(userRepository.findByUsername("abraham"))
                .thenReturn(Optional.empty());

        UserEntity existingUser = new UserEntity();
        existingUser.setEmail("abraham@test.com");

        when(userRepository.findByEmail("abraham@test.com"))
                .thenReturn(Optional.of(existingUser));

        // Act & Assert
        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> authService.register(registerRequest)
                );

        assertEquals(
                "Email already exists",
                exception.getMessage()
        );

        verify(userRepository)
                .findByUsername("abraham");

        verify(userRepository)
                .findByEmail("abraham@test.com");

        verify(userRepository, never())
                .save(any(UserEntity.class));

        verify(passwordEncoder, never())
                .encode(anyString());

        verify(jwtService, never())
                .generateToken(any(UserEntity.class));
    }

    @Test
    void login_shouldAuthenticateUserAndReturnToken() {

        // Arrange
        UserEntity user = new UserEntity();
        user.setUsername("abraham");
        user.setEmail("abraham@test.com");
        user.setRole(Role.USER);
        user.setEnabled(true);

        when(authenticationManager.authenticate(any(
                UsernamePasswordAuthenticationToken.class
        ))).thenReturn(null);

        when(userRepository.findByUsername("abraham"))
                .thenReturn(Optional.of(user));

        when(jwtService.generateToken(user))
                .thenReturn("jwt-token");

        // Act
        LoginResponse response =
                authService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("jwt-token", response.token());

        verify(authenticationManager)
                .authenticate(any(
                        UsernamePasswordAuthenticationToken.class
                ));

        verify(userRepository)
                .findByUsername("abraham");

        verify(jwtService)
                .generateToken(user);
    }

    @Test
    void login_shouldUseProvidedUsernameAndPassword() {

        // Arrange
        UserEntity user = new UserEntity();
        user.setUsername("abraham");

        when(authenticationManager.authenticate(any(
                UsernamePasswordAuthenticationToken.class
        ))).thenReturn(null);

        when(userRepository.findByUsername("abraham"))
                .thenReturn(Optional.of(user));

        when(jwtService.generateToken(user))
                .thenReturn("jwt-token");

        // Act
        authService.login(loginRequest);

        // Assert
        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(
                        UsernamePasswordAuthenticationToken.class
                );

        verify(authenticationManager)
                .authenticate(captor.capture());

        UsernamePasswordAuthenticationToken authentication =
                captor.getValue();

        assertEquals(
                "abraham",
                authentication.getPrincipal()
        );

        assertEquals(
                "Password123!",
                authentication.getCredentials()
        );
    }

    @Test
    void login_shouldThrowExceptionWhenUserDoesNotExist() {

        // Arrange
        when(authenticationManager.authenticate(any(
                UsernamePasswordAuthenticationToken.class
        ))).thenReturn(null);

        when(userRepository.findByUsername("abraham"))
                .thenReturn(Optional.empty());

        // Act & Assert
        UsernameNotFoundException exception =
                assertThrows(
                        UsernameNotFoundException.class,
                        () -> authService.login(loginRequest)
                );

        assertEquals(
                "User not found",
                exception.getMessage()
        );

        verify(authenticationManager)
                .authenticate(any(
                        UsernamePasswordAuthenticationToken.class
                ));

        verify(userRepository)
                .findByUsername("abraham");

        verify(jwtService, never())
                .generateToken(any(UserEntity.class));
    }

    @Test
    void login_shouldNotGenerateTokenWhenCredentialsAreInvalid() {

        // Arrange
        when(authenticationManager.authenticate(any(
                UsernamePasswordAuthenticationToken.class
        ))).thenThrow(
                new BadCredentialsException("Bad credentials")
        );

        // Act & Assert
        assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        verify(authenticationManager)
                .authenticate(any(
                        UsernamePasswordAuthenticationToken.class
                ));

        verify(userRepository, never())
                .findByUsername(anyString());

        verify(jwtService, never())
                .generateToken(any(UserEntity.class));
    }
}
