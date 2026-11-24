package com.testforge.backend.service;

import static com.testforge.backend.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.testforge.backend.model.User;
import com.testforge.backend.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks CurrentUserService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheUserBehindTheAuthenticatedToken() {
        User manisha = user(1L, "manisha");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manisha", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(userRepository.findByUsername("manisha")).thenReturn(Optional.of(manisha));

        assertThat(service.getCurrentUser()).isSameAs(manisha);
    }

    @Test
    void rejectsARequestWithNoAuthentication() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.getCurrentUser())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsAnAnonymousToken() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(() -> service.getCurrentUser())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsATokenWhoseUserNoLongerExists() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ghost", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentUser())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("User not found");
    }
}