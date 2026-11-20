package com.grocery.service;

import com.grocery.dto.LoginRequest;
import com.grocery.dto.LoginResponse;
import com.grocery.dto.StoreRegistrationRequest;
import com.grocery.dto.UserResponse;
import com.grocery.model.*;
import com.grocery.repository.CategoryRepository;
import com.grocery.repository.StoreRepository;
import com.grocery.repository.StoreSettingsRepository;
import com.grocery.repository.UserRepository;
import com.grocery.security.JwtTokenProvider;
import com.grocery.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final StoreSettingsRepository storeSettingsRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider tokenProvider,
                       UserRepository userRepository,
                       StoreRepository storeRepository,
                       StoreSettingsRepository storeSettingsRepository,
                       CategoryRepository categoryRepository,
                       PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.storeSettingsRepository = storeSettingsRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse authenticateUser(LoginRequest loginRequest) {
        String authUsername = loginRequest.getUsername().trim();

        // If storeCode is provided (Staff Login), combine into "storeCode#username"
        if (loginRequest.getStoreCode() != null && !loginRequest.getStoreCode().trim().isEmpty()) {
            authUsername = loginRequest.getStoreCode().toLowerCase().trim() + "#" + loginRequest.getUsername().trim();
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(authUsername, loginRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        String storeId = userPrincipal.getStoreId();
        String storeCode = userPrincipal.getStoreCode();
        String storeName = "FreshGrocery Supermarket";

        if (storeCode != null) {
            Optional<Store> storeOpt = storeRepository.findByStoreCode(storeCode);
            if (storeOpt.isPresent()) {
                storeName = storeOpt.get().getStoreName();
                storeId = storeOpt.get().getId();
            }
        }

        return new LoginResponse(
                jwt,
                userPrincipal.getId(),
                userPrincipal.getUsername(),
                userPrincipal.getFullName(),
                userPrincipal.getEmail(),
                userPrincipal.getRole(),
                userPrincipal.getStatus(),
                storeId,
                storeCode,
                storeName
        );
    }

    public LoginResponse registerNewStore(StoreRegistrationRequest req) {
        String storeCode = req.getStoreCode() != null 
                ? req.getStoreCode().toLowerCase().trim().replaceAll("[^a-z0-9_-]", "") 
                : "";

        if (storeCode.isEmpty()) {
            throw new IllegalArgumentException("Store code must contain alphanumeric characters.");
        }

        if (storeRepository.existsByStoreCode(storeCode)) {
            throw new IllegalArgumentException("Store code '" + storeCode + "' is already registered. Please choose another.");
        }

        if (req.getOwnerEmail() == null || req.getOwnerEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Owner email is required.");
        }

        String ownerEmail = req.getOwnerEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(ownerEmail)) {
            throw new IllegalArgumentException("Email '" + ownerEmail + "' is already registered with another account. Please use a different email or log in.");
        }

        // 1. Create Store Entity
        Store store = new Store(
                storeCode,
                req.getStoreName().trim(),
                req.getOwnerName().trim(),
                ownerEmail,
                req.getPhone() != null ? req.getPhone().trim() : "",
                req.getCity() != null ? req.getCity().trim() : "City"
        );
        store.setAddress(req.getAddress());
        store.setGstin(req.getGstin());
        Store savedStore = storeRepository.save(store);

        // 2. Create Store Admin Account
        String adminUsername;
        if (req.getAdminUsername() != null && !req.getAdminUsername().trim().isEmpty()) {
            adminUsername = req.getAdminUsername().trim().toLowerCase().replaceAll("[^a-z0-9_.-]", "");
            if (userRepository.existsByUsername(adminUsername)) {
                throw new IllegalArgumentException("Username '" + adminUsername + "' is already in use. Please choose another username.");
            }
        } else {
            adminUsername = storeCode + "_admin";
            if (userRepository.existsByUsername(adminUsername)) {
                adminUsername = adminUsername + "_" + (System.currentTimeMillis() % 1000);
            }
        }

        User adminUser = new User(
                adminUsername,
                passwordEncoder.encode(req.getPassword()),
                req.getOwnerName().trim(),
                ownerEmail,
                req.getPhone(),
                UserRole.ADMIN,
                savedStore.getId(),
                storeCode
        );
        userRepository.save(adminUser);

        // 3. Create Store Settings
        StoreSettings settings = new StoreSettings();
        settings.setId("STORE_SETTINGS_" + savedStore.getId());
        settings.setStoreId(savedStore.getId());
        settings.setStoreName(savedStore.getStoreName());
        settings.setCity(savedStore.getCity());
        settings.setPhone(savedStore.getPhone());
        settings.setEmail(ownerEmail);
        settings.setAddress(req.getAddress() != null ? req.getAddress().trim() : "");
        settings.setLegalName(savedStore.getStoreName() + " Pvt Ltd");
        if (req.getGstin() != null) settings.setGstin(req.getGstin().trim());
        storeSettingsRepository.save(settings);

        // 4. Initialize Starter Categories for this store
        categoryRepository.save(new Category("Dairy & Eggs", "dairy", "🥛", "Fresh dairy, milk, butter, cheese", 1, savedStore.getId()));
        categoryRepository.save(new Category("Fruits & Vegetables", "fruits-vegetables", "🍎", "Fresh farm-sourced produce", 2, savedStore.getId()));
        categoryRepository.save(new Category("Bakery & Breads", "bakery", "🍞", "Fresh breads, buns, bakes", 3, savedStore.getId()));
        categoryRepository.save(new Category("Staples & Grains", "staples", "🌾", "Rice, flour, pulses, oil", 4, savedStore.getId()));
        categoryRepository.save(new Category("Beverages", "beverages", "🧃", "Juices, soft drinks, tea", 5, savedStore.getId()));
        categoryRepository.save(new Category("Snacks & Munchies", "snacks", "🍿", "Chips, biscuits, chocolates", 6, savedStore.getId()));

        // 5. Automatically Authenticate the new Store Admin
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(adminUsername, req.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        return new LoginResponse(
                jwt,
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getFullName(),
                adminUser.getEmail(),
                adminUser.getRole(),
                adminUser.getStatus(),
                savedStore.getId(),
                savedStore.getStoreCode(),
                savedStore.getStoreName()
        );
    }

    public Map<String, Object> getStorePublicInfo(String storeCode) {
        if (storeCode == null) return Map.of("exists", false);
        return storeRepository.findByStoreCode(storeCode.toLowerCase().trim())
                .map(s -> Map.<String, Object>of(
                        "exists", true,
                        "storeName", s.getStoreName(),
                        "storeCode", s.getStoreCode(),
                        "city", s.getCity() != null ? s.getCity() : "",
                        "status", s.getStatus()
                ))
                .orElse(Map.of("exists", false));
    }

    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus()
        );
    }
}
