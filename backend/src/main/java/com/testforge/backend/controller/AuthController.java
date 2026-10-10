package com.testforge.backend.controller;

import com.testforge.backend.dto.LoginRequest;
import com.testforge.backend.dto.RegisterRequest;
import com.testforge.backend.model.User;
import com.testforge.backend.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

private final UserRepository userRepository;
private final PasswordEncoder passwordEncoder;

@PostMapping("/register")
public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request) {
    if (userRepository.existsByUsername(request.getUsername())) {
        return ResponseEntity.badRequest().body(Map.of("message", "Error: Username is already taken!"));
    }

    if (userRepository.existsByEmail(request.getEmail())) {
        return ResponseEntity.badRequest().body(Map.of("message", "Error: Email is already in use!"));
    }

    User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .role("USER")
            .build();

    userRepository.save(user);

    return ResponseEntity.ok(Map.of("message", "User registered successfully!"));
}

@PostMapping("/login")
public ResponseEntity<?> loginUser(@Valid @RequestBody LoginRequest request) {
    Optional<User> userOpt = userRepository.findByUsername(request.getUsername());

    if (userOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOpt.get().getPassword())) {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid username or password"));
    }

    User user = userOpt.get();
    return ResponseEntity.ok(Map.of(
        "message", "Login successful",
        "username", user.getUsername(),
        "email", user.getEmail(),
        "role", user.getRole()
    ));
}
}