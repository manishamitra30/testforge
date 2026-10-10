package com.testforge.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

@Column(nullable = false, unique = true, length = 50)
private String username;

@Column(nullable = false, unique = true, length = 100)
private String email;

@Column(nullable = false)
private String password;

@Column(nullable = false, length = 20)
@Builder.Default
private String role = "USER";

@Column(name = "created_at", insertable = false, updatable = false)
private OffsetDateTime createdAt;
}