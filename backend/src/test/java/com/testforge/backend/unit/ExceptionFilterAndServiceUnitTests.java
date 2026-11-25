package com.testforge.backend.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.testforge.backend.common.exception.ApiError;
import com.testforge.backend.common.exception.BadRequestException;
import com.testforge.backend.common.exception.GlobalExceptionHandler;
import com.testforge.backend.common.exception.ResourceNotFoundException;
import com.testforge.backend.common.exception.ServiceBusyException;
import com.testforge.backend.logging.RequestIdFilter;
import com.testforge.backend.mapper.ProjectMapper;
import com.testforge.backend.model.Project;
import com.testforge.backend.model.User;
import com.testforge.backend.repository.ProjectRepository;
import com.testforge.backend.repository.UserRepository;
import com.testforge.backend.service.CurrentUserService;
import com.testforge.backend.service.ProjectService;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ExceptionFilterAndServiceUnitTests {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void globalExceptionHandlerMapsExpectedStatusesAndFieldErrors() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/projects");

        ResponseEntity<ApiError> notFound = handler.notFound(
                ResourceNotFoundException.of("Project", 99L), req);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFound.getBody()).isNotNull();
        assertThat(notFound.getBody().message()).contains("99");

        ResponseEntity<ApiError> noResource = handler.noResource(
                new NoResourceFoundException(HttpMethod.GET, "/missing", null), req);
        assertThat(noResource.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiError> badReq = handler.badRequest(new BadRequestException("Invalid input"), req);
        assertThat(badReq.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "req");
        bindingResult.addError(new FieldError("req", "name", "must not be blank"));
        MethodParameter param = mock(MethodParameter.class);
        MethodArgumentNotValidException validationEx = new MethodArgumentNotValidException(param, bindingResult);
        ResponseEntity<ApiError> validation = handler.validation(validationEx, req);
        assertThat(validation.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(validation.getBody().fieldErrors()).containsEntry("name", "must not be blank");

        ResponseEntity<ApiError> unreadable = handler.unreadable(
                mock(HttpMessageNotReadableException.class), req);
        assertThat(unreadable.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        MethodArgumentTypeMismatchException mismatchEx = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", param, new IllegalArgumentException("bad"));
        ResponseEntity<ApiError> mismatch = handler.typeMismatch(mismatchEx, req);
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mismatch.getBody().message()).contains("id");

        ResponseEntity<ApiError> missingParam = handler.missingParameter(
                new MissingServletRequestParameterException("projectId", "Long"), req);
        assertThat(missingParam.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ApiError> methodNotAllowed = handler.methodNotAllowed(
                new HttpRequestMethodNotSupportedException("DELETE"), req);
        assertThat(methodNotAllowed.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        ResponseEntity<ApiError> unsupportedMedia = handler.unsupportedMediaType(
                new HttpMediaTypeNotSupportedException("text/plain"), req);
        assertThat(unsupportedMedia.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        ResponseEntity<ApiError> defaultForbidden = handler.forbidden(new AccessDeniedException(""), req);
        assertThat(defaultForbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(defaultForbidden.getBody().message()).isEqualTo("Access denied");

        ResponseEntity<ApiError> customForbidden = handler.forbidden(new AccessDeniedException("Custom deny"), req);
        assertThat(customForbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(customForbidden.getBody().message()).isEqualTo("Custom deny");

        ResponseEntity<ApiError> unauth = handler.unauthorized(new BadCredentialsException("bad creds"), req);
        assertThat(unauth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<ApiError> busy = handler.busy(new ServiceBusyException("Queue full"), req);
        assertThat(busy.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        ResponseEntity<ApiError> unexpected = handler.unexpected(new RuntimeException("boom"), req);
        assertThat(unexpected.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(unexpected.getBody().message()).isEqualTo("Unexpected server error");
    }

    @Test
    void requestIdFilterPreservesValidIdAndGeneratesWhenInvalid() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();

        MockHttpServletRequest validReq = new MockHttpServletRequest("GET", "/api/projects");
        validReq.addHeader(RequestIdFilter.HEADER, "req-id_123.ABC");
        MockHttpServletResponse validRes = new MockHttpServletResponse();
        FilterChain chain1 = (request, response) ->
                assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo("req-id_123.ABC");

        filter.doFilter(validReq, validRes, chain1);
        assertThat(validRes.getHeader(RequestIdFilter.HEADER)).isEqualTo("req-id_123.ABC");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();

        MockHttpServletRequest invalidReq = new MockHttpServletRequest("GET", "/actuator/health");
        invalidReq.addHeader(RequestIdFilter.HEADER, "bad\ninjection!");
        MockHttpServletResponse invalidRes = new MockHttpServletResponse();
        FilterChain chain2 = mock(FilterChain.class);

        filter.doFilter(invalidReq, invalidRes, chain2);
        assertThat(invalidRes.getHeader(RequestIdFilter.HEADER)).isNotBlank();
        assertThat(invalidRes.getHeader(RequestIdFilter.HEADER)).doesNotContain("\n");
        verify(chain2).doFilter(any(), any());
    }

    @Test
    void currentUserServiceAndProjectServiceEnforceAuthAndOwnership() {
        UserRepository userRepository = mock(UserRepository.class);
        CurrentUserService currentUserService = new CurrentUserService(userRepository);

        assertThatThrownBy(currentUserService::getCurrentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        AnonymousAuthenticationToken anon = new AnonymousAuthenticationToken(
                "key", "anon", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(anon);
        assertThatThrownBy(currentUserService::getCurrentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        User owner = User.builder().id(1L).username("alice").email("a@test.local").role("USER").build();
        User intruder = User.builder().id(2L).username("bob").email("b@test.local").role("USER").build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(intruder));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        assertThat(currentUserService.getCurrentUser().getId()).isEqualTo(1L);

        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        ProjectService projectService = new ProjectService(projectRepository, projectMapper, currentUserService);

        Project project = new Project();
        project.setId(10L);
        project.setOwner(owner);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));

        assertThat(projectService.getOwnedProject(10L).getId()).isEqualTo(10L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("bob", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        assertThatThrownBy(() -> projectService.getOwnedProject(10L))
                .isInstanceOf(AccessDeniedException.class);
    }
}