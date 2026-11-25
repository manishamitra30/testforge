package com.testforge.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.backend.common.exception.GlobalExceptionHandler;
import com.testforge.backend.common.exception.ResourceNotFoundException;
import com.testforge.backend.config.SecurityConfig;
import com.testforge.backend.dto.ProjectRequest;
import com.testforge.backend.dto.ProjectResponse;
import com.testforge.backend.model.User;
import com.testforge.backend.repository.UserRepository;
import com.testforge.backend.security.JwtService;
import com.testforge.backend.service.ProjectService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AuthController.class, ProjectController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthAndProjectMockMvcTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private ProjectService projectService;

    @Test
    void registerAndLoginFlowWorksWithExpectedStatuses() throws Exception {
        when(userRepository.existsByUsername("manisha")).thenReturn(false);
        when(userRepository.existsByEmail("manisha@testforge.local")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-secret");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "manisha",
                                  "email": "manisha@testforge.local",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully!"));

        User existing = User.builder()
                .id(1L)
                .username("manisha")
                .email("manisha@testforge.local")
                .password("hashed-secret")
                .role("USER")
                .build();
        when(userRepository.findByUsername("manisha")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("secret123", "hashed-secret")).thenReturn(true);
        when(jwtService.generateToken("manisha", "USER")).thenReturn("jwt-token-123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "manisha",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-123"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("manisha"));

        when(passwordEncoder.matches("wrong", "hashed-secret")).thenReturn(false);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "manisha",
                                  "password": "wrong"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void protectedEndpointRejectsAnonymousRequestWith401() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    @WithMockUser(username = "manisha")
    void projectCreateValidationAndOwnershipErrorsReturnExpectedPayloads() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "description": "desc"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        ProjectResponse created = new ProjectResponse(
                10L, "TestForge QA", "Core suite", 1L, LocalDateTime.now());
        when(projectService.create(any(ProjectRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProjectRequest("TestForge QA", "Core suite"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/projects/10"))
                .andExpect(jsonPath("$.id").value(10));

        when(projectService.list(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(created)));
        mockMvc.perform(get("/api/projects").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("TestForge QA"));

        when(projectService.get(99L))
                .thenThrow(new ResourceNotFoundException("Project with ID 99 not found"));
        mockMvc.perform(get("/api/projects/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        doThrow(new AccessDeniedException("You do not have access to this project"))
                .when(projectService).delete(eq(7L));
        mockMvc.perform(delete("/api/projects/7"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}