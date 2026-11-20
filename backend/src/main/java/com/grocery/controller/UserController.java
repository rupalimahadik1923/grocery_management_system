package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.dto.UserResponse;
import com.grocery.model.User;
import com.grocery.model.UserRole;
import com.grocery.model.UserStatus;
import com.grocery.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers(@org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        String storeCode = null;
        if (userDetails instanceof com.grocery.security.UserPrincipal) {
            storeCode = ((com.grocery.security.UserPrincipal) userDetails).getStoreCode();
        }

        List<User> users = (storeCode != null && !storeCode.isEmpty())
                ? userRepository.findByStoreCode(storeCode)
                : userRepository.findAll();

        List<UserResponse> list = users.stream()
                .map(u -> new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(), u.getStatus()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @RequestBody Map<String, String> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        String username = body.get("username");
        String password = body.getOrDefault("password", "password123");
        String fullName = body.get("fullName");
        String email = body.get("email");
        String phone = body.get("phone");
        String roleStr = body.getOrDefault("role", "CASHIER");

        String storeId = null;
        String storeCode = null;
        if (userDetails instanceof com.grocery.security.UserPrincipal) {
            storeId = ((com.grocery.security.UserPrincipal) userDetails).getStoreId();
            storeCode = ((com.grocery.security.UserPrincipal) userDetails).getStoreCode();
        }

        if (storeCode != null && userRepository.existsByUsernameAndStoreCode(username, storeCode)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Username already taken in this store"));
        } else if (storeCode == null && userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Username already taken"));
        }

        UserRole role;
        try {
            role = UserRole.valueOf(roleStr);
        } catch (Exception e) {
            role = UserRole.CASHIER;
        }

        User user = new User(username, passwordEncoder.encode(password), fullName, email, phone, role, storeId, storeCode);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        UserResponse resp = new UserResponse(saved.getId(), saved.getUsername(), saved.getFullName(), saved.getEmail(), saved.getPhone(), saved.getRole(), saved.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Staff user created", resp));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<ApiResponse<Void>> updateRole(@PathVariable String id, @RequestBody Map<String, String> body) {
        return userRepository.findById(id).map(user -> {
            user.setRole(UserRole.valueOf(body.get("role")));
            userRepository.save(user);
            return ResponseEntity.ok(ApiResponse.<Void>success("Role updated", null));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        return userRepository.findById(id).map(user -> {
            user.setStatus(UserStatus.valueOf(body.get("status")));
            userRepository.save(user);
            return ResponseEntity.ok(ApiResponse.<Void>success("Status updated", null));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable String id, @RequestBody Map<String, String> body) {
        String newPassword = body.getOrDefault("password", "password123");
        return userRepository.findById(id).map(user -> {
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            return ResponseEntity.ok(ApiResponse.<Void>success("Password reset successfully", null));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String id) {
        userRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted", null));
    }
}
