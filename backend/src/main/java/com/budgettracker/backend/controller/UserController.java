package com.budgettracker.backend.controller;

import com.budgettracker.backend.dto.UpdateUserRequest;
import com.budgettracker.backend.dto.UserDto;
import com.budgettracker.backend.model.User;
import com.budgettracker.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users/me")
public class UserController {

    private final UserRepository userRepository;

    @Autowired
    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<UserDto> getMe(User user) {
        UserDto dto = UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .defaultCurrency(user.getDefaultCurrency())
                .isOnboarded(user.isOnboarded())
                .build();
        return ResponseEntity.ok(dto);
    }

    @PatchMapping
    public ResponseEntity<UserDto> updateMe(@Valid @RequestBody UpdateUserRequest request, User user) {
        user.setDefaultCurrency(request.getDefaultCurrency());
        user.setOnboarded(true);
        User updated = userRepository.save(user);

        UserDto dto = UserDto.builder()
                .id(updated.getId())
                .email(updated.getEmail())
                .defaultCurrency(updated.getDefaultCurrency())
                .isOnboarded(updated.isOnboarded())
                .build();
        return ResponseEntity.ok(dto);
    }
}
