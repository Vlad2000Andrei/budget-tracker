package com.budgettracker.backend.config;

import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.UserRepository;
import com.budgettracker.backend.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final boolean mockUserFallbackEnabled;

    @Autowired
    public JwtAuthenticationFilter(
            JwtService jwtService, 
            UserRepository userRepository,
            @Value("${app.security.mock-user-fallback.enabled:false}") boolean mockUserFallbackEnabled) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.mockUserFallbackEnabled = mockUserFallbackEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtService.validateToken(token)) {
                Long userId = jwtService.getUserIdFromToken(token);
                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    userRepository.findById(userId).ifPresent(user -> {
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                user, null, Collections.emptyList()
                        );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
                }
            }
        } else if (mockUserFallbackEnabled) {
            String userIdHeader = request.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.isBlank()) {
                try {
                    Long userId = Long.parseLong(userIdHeader.trim());
                    if (SecurityContextHolder.getContext().getAuthentication() == null) {
                        userRepository.findById(userId).ifPresent(user -> {
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    user, null, Collections.emptyList()
                            );
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        filterChain.doFilter(request, response);
    }
}
