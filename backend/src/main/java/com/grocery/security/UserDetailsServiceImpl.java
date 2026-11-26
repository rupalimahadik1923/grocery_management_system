package com.grocery.security;

import com.grocery.model.User;
import com.grocery.model.UserRole;
import com.grocery.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new UsernameNotFoundException("Login identifier cannot be empty");
        }

        // 1. Multi-tenant staff format: "storeCode#username"
        if (identifier.contains("#")) {
            String[] parts = identifier.split("#", 2);
            String storeCode = parts[0].toLowerCase().trim();
            String login = parts[1].trim();

            List<User> storeUsers = userRepository.findByStoreCode(storeCode);

            // 1.1 Try exact username in this store
            Optional<User> userOpt = storeUsers.stream()
                    .filter(u -> u.getUsername() != null && u.getUsername().equalsIgnoreCase(login))
                    .findFirst();

            // 1.2 If login is "admin", "owner", or matches storeCode, find the ADMIN user of this store
            if (userOpt.isEmpty() && (login.equalsIgnoreCase("admin") || login.equalsIgnoreCase("owner") || login.equalsIgnoreCase(storeCode))) {
                userOpt = storeUsers.stream()
                        .filter(u -> u.getRole() == UserRole.ADMIN)
                        .findFirst();
            }

            // 1.3 Try matching email in this store (case-insensitive)
            if (userOpt.isEmpty()) {
                userOpt = storeUsers.stream()
                        .filter(u -> u.getEmail() != null && u.getEmail().trim().equalsIgnoreCase(login))
                        .findFirst();
            }

            User user = userOpt.orElseThrow(() -> 
                new UsernameNotFoundException("Staff user '" + login + "' not found in store '" + storeCode + "'")
            );
            return new UserPrincipal(user);
        }

        // 2. Standard or Admin format: username, email, or storeCode
        String cleanIdentifier = identifier.trim();

        // 2.1 Exact or case-insensitive username across all users
        Optional<User> userOpt = userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().equalsIgnoreCase(cleanIdentifier))
                .findFirst();

        // 2.2 Case-insensitive email
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findAll().stream()
                    .filter(u -> u.getEmail() != null && u.getEmail().trim().equalsIgnoreCase(cleanIdentifier))
                    .findFirst();
        }

        // 2.3 If identifier is a storeCode, find the ADMIN of that store
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByStoreCode(cleanIdentifier.toLowerCase()).stream()
                    .filter(u -> u.getRole() == UserRole.ADMIN)
                    .findFirst();
        }

        User user = userOpt.orElseThrow(() -> 
            new UsernameNotFoundException("User not found with username, email, or store code: " + cleanIdentifier)
        );
        return new UserPrincipal(user);
    }
}
