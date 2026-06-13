package com.budgettracker.backend.config;

import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.UserRepository;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class UserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;

    public UserArgumentResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(User.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        
        String userIdHeader = webRequest.getHeader("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                Long id = Long.parseLong(userIdHeader.trim());
                return userRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("User with ID " + id + " not found"));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid X-User-Id header format");
            }
        }

        // Default behavior: Provision and return a default mock user for local development and testing
        String defaultSub = "mock-google-sub-123";
        return userRepository.findByGoogleSub(defaultSub)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email("test@example.com")
                                .googleSub(defaultSub)
                                .defaultCurrency("USD")
                                .build()
                ));
    }
}
