package com.testforge.backend.websocket;

import com.testforge.backend.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Pattern RUN_TOPIC = Pattern.compile("^/topic/runs/(\\d{1,18})$");

    private final JwtService jwtService;
    private final RunAccessChecker accessChecker;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            authenticate(accessor);
        } else if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        } else if (command == StompCommand.SEND) {
            throw new AccessDeniedException("Clients are not allowed to send messages");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new AccessDeniedException("Missing bearer token");
        }
        try {
            Claims claims = jwtService.parse(header.substring(7));
            String username = claims.getSubject();
            if (username == null) {
                throw new AccessDeniedException("Invalid token");
            }
            String role = claims.get("role", String.class);
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    username, null, List.of(new SimpleGrantedAuthority("ROLE_" + (role == null ? "USER" : role)))));
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AccessDeniedException("Invalid or expired token");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        String destination = accessor.getDestination();
        Matcher matcher = RUN_TOPIC.matcher(destination == null ? "" : destination);
        if (!matcher.matches()) {
            throw new AccessDeniedException("Subscriptions are limited to /topic/runs/{id}");
        }
        Long runId = Long.parseLong(matcher.group(1));
        if (!accessChecker.canView(runId, user.getName())) {
            log.warn("User {} was denied a subscription to run {}", user.getName(), runId);
            throw new AccessDeniedException("You do not have access to this run");
        }
    }
}