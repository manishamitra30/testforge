package com.testforge.backend.repository;

import com.testforge.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
Optional<User> findByUsername(String username);
Optional<User> findByEmail(String email);
boolean existsByUsername(String username);
boolean existsByEmail(String email);
}