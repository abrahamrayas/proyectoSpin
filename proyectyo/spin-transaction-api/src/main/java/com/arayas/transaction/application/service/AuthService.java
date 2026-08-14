package com.arayas.transaction.application.service;

import com.arayas.transaction.adapters.persistence.entity.UserEntity;
import com.arayas.transaction.adapters.persistence.repository.UserRepository;
import com.arayas.transaction.adapters.web.dto.LoginRequest;
import com.arayas.transaction.adapters.web.dto.LoginResponse;
import com.arayas.transaction.adapters.web.dto.RegisterRequest;
import com.arayas.transaction.application.model.Role;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public LoginResponse register(RegisterRequest request) {

        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        UserEntity user = new UserEntity();

        user.setUsername(request.username());
        user.setEmail(request.email());

        // Nunca guardar password en texto plano
        user.setPassword(
                passwordEncoder.encode(request.password())
        );

        user.setRole(Role.USER);
        user.setEnabled(true);

        userRepository.save(user);

        String token = jwtService.generateToken(user);

        return new LoginResponse(token);
    }

    public LoginResponse login(LoginRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        UserEntity user = userRepository
                .findByUsername(request.username())
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found")
                );

        String token = jwtService.generateToken(user);

        return new LoginResponse(token);
    }
}
