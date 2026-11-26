package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.dto.UserResponse;
import com.grocery.model.User;
import com.grocery.model.UserRole;
import com.grocery.model.UserStatus;
import com.grocery.repository.UserRepository;
import com.grocery.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private boolean isSameTenant(User target, UserDetails userDetails) {
        if (!(userDetails instanceof UserPrincipal principal)) {
            return false;
        }
        if (principal.getStoreId() != null && target.getStoreId() != null) {
            return Objects.equals(principal.getStoreId(), target.getStoreId());
        }
        return principal.getStoreCode() != null && principal.getStoreCode().equalsIgnoreCase(target.getStoreCode());
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers(@org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        String storeCode = null;
        if (userDetails instanceof com.grocery.security.UserPrincipal) {
            storeCode = ((com.grocery.security.UserPrincipal) userDetails).getStoreCode();
        }

        if (storeCode == null || storeCode.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<User> users = userRepository.findByStoreCode(storeCode);

        List<UserResponse> list = users.stream()
                .map(u -> new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(), u.getStatus()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @RequestBody Map<String, String> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        String username = body.get("username") != null ? body.get("username").trim() : "";
        String password = body.getOrDefault("password", "password123");
        String fullName = body.get("fullName");
        String email = body.get("email") != null ? body.get("email").trim().toLowerCase() : null;
        String phone = body.get("phone");
        String roleStr = body.getOrDefault("role", "CASHIER");

        if (username.isEmpty() || password == null || password.length() < 6) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Username and a password of at least 6 characters are required"));
        }

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
    public ResponseEntity<ApiResponse<Void>> updateRole(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        return userRepository.findById(id).map(user -> {
            if (!isSameTenant(user, userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.<Void>error("Access denied for another store's user"));
            }
            if (userDetails instanceof UserPrincipal principal && Objects.equals(user.getId(), principal.getId())) {
                return ResponseEntity.badRequest().body(ApiResponse.<Void>error("Admins cannot modify their own role"));
            }
            try {
                user.setRole(UserRole.valueOf(body.get("role")));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(ApiResponse.<Void>error("Invalid role specified"));
            }
            userRepository.save(user);
            return ResponseEntity.ok(ApiResponse.<Void>success("Role updated", null));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        return userRepository.findById(id).map(user -> {
            if (!isSameTenant(user, userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.<Void>error("Access denied for another store's user"));
            }
            try {
                user.setStatus(UserStatus.valueOf(body.get("status")));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(ApiResponse.<Void>error("Invalid status specified"));
            }
            userRepository.save(user);
            return ResponseEntity.ok(ApiResponse.<Void>success("Status updated", null));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        String newPassword = body.getOrDefault("password", "password123");
        return userRepository.findById(id).map(user -> {
            if (!isSameTenant(user, userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.<Void>error("Access denied for another store's user"));
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            return ResponseEntity.ok(ApiResponse.<Void>success("Password reset successfully", null));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return userRepository.findById(id).map(user -> {
            if (!isSameTenant(user, userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.<Void>error("Access denied for another store's user"));
            }
            if (userDetails instanceof UserPrincipal principal && Objects.equals(user.getId(), principal.getId())) {
                return ResponseEntity.badRequest().body(ApiResponse.<Void>error("Admins cannot delete their own account"));
            }
            userRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.<Void>success("User deleted", null));
        }).orElse(ResponseEntity.notFound().build());
    }
}
