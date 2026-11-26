package com.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocery.dto.LoginRequest;
import com.grocery.dto.StoreRegistrationRequest;
import com.grocery.model.Store;
import com.grocery.model.User;
import com.grocery.model.UserRole;
import com.grocery.repository.CategoryRepository;
import com.grocery.repository.StoreRepository;
import com.grocery.repository.StoreSettingsRepository;
import com.grocery.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthBrutalTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StoreRepository storeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StoreSettingsRepository storeSettingsRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private final List<String> createdStoreIds = new ArrayList<>();
    private final List<String> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanupTestOwnedRecords() {
        for (String storeId : createdStoreIds) {
            categoryRepository.deleteAll(
                    categoryRepository.findAll().stream()
                            .filter(c -> storeId.equals(c.getStoreId()))
                            .toList()
            );
            storeSettingsRepository.deleteById("STORE_SETTINGS_" + storeId);
            storeRepository.deleteById(storeId);
        }
        for (String userId : createdUserIds) {
            userRepository.deleteById(userId);
        }
        createdStoreIds.clear();
        createdUserIds.clear();
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private StoreRegistrationRequest buildRegReq(String suffix, String customAdminUsername) {
        StoreRegistrationRequest req = new StoreRegistrationRequest();
        req.setStoreName("Test Store " + suffix);
        req.setStoreCode("teststore" + suffix);
        req.setOwnerName("Owner " + suffix);
        req.setOwnerEmail("owner_" + suffix + "@test.local");
        req.setPhone("9999900000");
        req.setCity("Bengaluru");
        req.setAddress("123 Test St");
        req.setPassword("StrongPass123!");
        req.setAdminUsername(customAdminUsername);
        return req;
    }

    private void trackCreatedStore(String storeCode) {
        storeRepository.findByStoreCode(storeCode).ifPresent(s -> {
            createdStoreIds.add(s.getId());
            userRepository.findByStoreCode(storeCode).forEach(u -> createdUserIds.add(u.getId()));
        });
    }

    // =========================================================================
    // PHASE 3: STORE VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("Store verification: valid, mixed-case, unknown, malformed, oversized, no sensitive leak")
    void testStoreVerificationCases() throws Exception {
        // Valid
        mockMvc.perform(get("/api/v1/auth/store/freshgrocery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.storeCode").value("freshgrocery"))
                .andExpect(jsonPath("$.storeName").isNotEmpty())
                .andExpect(jsonPath("$.ownerEmail").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.gstin").doesNotExist());

        // Uppercase normalization
        mockMvc.perform(get("/api/v1/auth/store/FRESHGROCERY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));

        // Unknown
        mockMvc.perform(get("/api/v1/auth/store/unknownstore_xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));

        // Whitespace
        mockMvc.perform(get("/api/v1/auth/store/   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));

        // Malformed / injection-like
        mockMvc.perform(get("/api/v1/auth/store/{code}", "{'$ne':null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));

        // Oversized
        String oversized = "a".repeat(500);
        mockMvc.perform(get("/api/v1/auth/store/" + oversized))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));
    }

    // =========================================================================
    // PHASE 3: STAFF LOGIN & ALL ROLES
    // =========================================================================

    @Test
    @DisplayName("Staff login: all 4 seeded roles authenticate with storeCode and return correct roles")
    void testStaffLoginAllRoles() throws Exception {
        String[][] creds = {
                {"cashier", "cashier123", "CASHIER"},
                {"manager", "manager123", "STORE_MANAGER"},
                {"inventory", "inventory123", "INVENTORY_STAFF"},
                {"admin", "admin123", "ADMIN"}
        };
        for (String[] c : creds) {
            LoginRequest req = new LoginRequest(c[0], c[1], "freshgrocery");
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.role").value(c[2]))
                    .andExpect(jsonPath("$.storeCode").value("freshgrocery"));
        }
    }

    @Test
    @DisplayName("Staff login: case-insensitive username & email inside store")
    void testStaffLoginCasingAndEmail() throws Exception {
        // Uppercase username + uppercase storeCode
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("CASHIER", "cashier123", "FRESHGROCERY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CASHIER"));

        // Email inside store
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("cashier@grocery.local", "cashier123", "freshgrocery"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cashier"));
    }

    @Test
    @DisplayName("Staff login: negative cases (wrong password, unknown user, empty, malformed JSON, injection)")
    void testStaffLoginNegativeCases() throws Exception {
        // Wrong password
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("cashier", "wrongpass", "freshgrocery"))))
                .andExpect(status().isUnauthorized());

        // Unknown user
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("ghostuser", "cashier123", "freshgrocery"))))
                .andExpect(status().isUnauthorized());

        // Empty fields
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("", "", "freshgrocery"))))
                .andExpect(status().isBadRequest());

        // Whitespace-only username
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("   ", "cashier123", "freshgrocery"))))
                .andExpect(status().isBadRequest());

        // Injection string
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin' OR '1'='1", "anything", "freshgrocery"))))
                .andExpect(status().isUnauthorized());

        // Malformed JSON
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad-json"))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // PHASE 3: TENANT ISOLATION
    // =========================================================================

    @Test
    @DisplayName("Tenant isolation: user from store A cannot log in via store B")
    void testCrossTenantLoginBlocked() throws Exception {
        String s1 = uniqueSuffix();
        String s2 = uniqueSuffix();
        StoreRegistrationRequest reg1 = buildRegReq(s1, "adm_" + s1);
        StoreRegistrationRequest reg2 = buildRegReq(s2, "adm_" + s2);

        mockMvc.perform(post("/api/v1/auth/register-store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg1)))
                .andExpect(status().isOk());
        trackCreatedStore(reg1.getStoreCode());

        mockMvc.perform(post("/api/v1/auth/register-store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg2)))
                .andExpect(status().isOk());
        trackCreatedStore(reg2.getStoreCode());

        // Attempt Store 1 admin login using Store 2's storeCode
        LoginRequest crossTenant = new LoginRequest("adm_" + s1, "StrongPass123!", reg2.getStoreCode());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crossTenant)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    // =========================================================================
    // PHASE 4: STORE ADMIN AUTHENTICATION
    // =========================================================================

    @Test
    @DisplayName("Admin login: username, email, and storeCode without storeCode field; cashier not elevated")
    void testAdminLoginModes() throws Exception {
        // Username
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Email
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin@grocery.local", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Store code
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("freshgrocery", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Cashier logging in without storeCode must NOT be elevated to ADMIN
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("cashier", "cashier123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CASHIER"));
    }

    // =========================================================================
    // PHASE 5: STORE REGISTRATION & ATOMICITY
    // =========================================================================

    @Test
    @DisplayName("Registration: backend validation rejects invalid email, short password, short storeCode")
    void testRegistrationBackendValidation() throws Exception {
        String s = uniqueSuffix();

        // Invalid email
        StoreRegistrationRequest badEmail = buildRegReq(s, "adm_" + s);
        badEmail.setOwnerEmail("not-an-email");
        mockMvc.perform(post("/api/v1/auth/register-store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badEmail)))
                .andExpect(status().isBadRequest());
        assertFalse(storeRepository.existsByStoreCode(badEmail.getStoreCode()), "Invalid email must not persist store");

        // Short password (< 6)
        StoreRegistrationRequest shortPass = buildRegReq(s + "b", "adm_" + s + "b");
        shortPass.setPassword("123");
        mockMvc.perform(post("/api/v1/auth/register-store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortPass)))
                .andExpect(status().isBadRequest());
        assertFalse(storeRepository.existsByStoreCode(shortPass.getStoreCode()), "Short password must not persist store");

        // Short storeCode (< 2)
        StoreRegistrationRequest shortCode = buildRegReq(s + "c", "adm_" + s + "c");
        shortCode.setStoreCode("a");
        mockMvc.perform(post("/api/v1/auth/register-store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortCode)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Registration: duplicate adminUsername must NOT leave orphaned Store in MongoDB")
    void testRegistrationAtomicityOnDuplicateAdminUsername() throws Exception {
        String s = uniqueSuffix();
        StoreRegistrationRequest req = buildRegReq(s, "admin"); // "admin" already exists in freshgrocery

        try {
            mockMvc.perform(post("/api/v1/auth/register-store")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());

            assertFalse(storeRepository.existsByStoreCode(req.getStoreCode()),
                    "Orphaned store must NOT exist when adminUsername is already taken!");
        } finally {
            trackCreatedStore(req.getStoreCode());
        }
    }

    @Test
    @DisplayName("Registration: concurrent duplicate requests create at most one store")
    void testConcurrentDuplicateRegistration() throws Exception {
        String s = uniqueSuffix();
        StoreRegistrationRequest req = buildRegReq(s, "concur_" + s);
        String json = objectMapper.writeValueAsString(req);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Integer> call = () -> mockMvc.perform(post("/api/v1/auth/register-store")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andReturn().getResponse().getStatus();

        List<Future<Integer>> results = pool.invokeAll(List.of(call, call));
        pool.shutdown();

        int s1 = results.get(0).get();
        int s2 = results.get(1).get();
        trackCreatedStore(req.getStoreCode());

        long okCount = List.of(s1, s2).stream().filter(code -> code == 200).count();
        assertEquals(1, okCount, "Exactly one concurrent registration should succeed, got statuses: " + s1 + ", " + s2);
    }

    // =========================================================================
    // PHASE 6: JWT & ROLE ENFORCEMENT
    // =========================================================================

    @Test
    @DisplayName("JWT security: expired, malformed, and invalid-signature tokens are rejected with 401")
    void testJwtValidationCases() throws Exception {
        // Malformed
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());

        // Invalid signature
        String wrongKeyToken = Jwts.builder()
                .setSubject("admin")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor("DifferentSecretKeyThatIsAtLeast32BytesLong!!".getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + wrongKeyToken))
                .andExpect(status().isUnauthorized());

        // Expired token
        String expiredToken = Jwts.builder()
                .setSubject("admin")
                .setIssuedAt(new Date(System.currentTimeMillis() - 120000))
                .setExpiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Role & tenant enforcement: CASHIER cannot create/delete users or access other store users")
    void testRoleAndTenantEnforcementOnUsersEndpoint() throws Exception {
        MvcResult cashierRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("cashier", "cashier123", "freshgrocery"))))
                .andExpect(status().isOk())
                .andReturn();
        String cashierToken = objectMapper.readTree(cashierRes.getResponse().getContentAsString()).get("token").asText();

        // Cashier must be forbidden from creating, listing, updating, or deleting users
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"rogue\",\"password\":\"123456\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Tenant isolation on UserController: Store A Admin cannot read/modify/delete Store B users, nor self-demote/delete")
    void testUserControllerCrossTenantIdorAndSelfProtection() throws Exception {
        String s1 = uniqueSuffix();
        String s2 = uniqueSuffix();
        StoreRegistrationRequest reg1 = buildRegReq(s1, "adm1_" + s1);
        StoreRegistrationRequest reg2 = buildRegReq(s2, "adm2_" + s2);

        MvcResult r1 = mockMvc.perform(post("/api/v1/auth/register-store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg1)))
                .andExpect(status().isOk())
                .andReturn();
        trackCreatedStore(reg1.getStoreCode());

        MvcResult r2 = mockMvc.perform(post("/api/v1/auth/register-store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg2)))
                .andExpect(status().isOk())
                .andReturn();
        trackCreatedStore(reg2.getStoreCode());

        JsonNode data1 = objectMapper.readTree(r1.getResponse().getContentAsString()).get("data");
        JsonNode data2 = objectMapper.readTree(r2.getResponse().getContentAsString()).get("data");
        String token1 = data1.get("token").asText();
        String admin1Id = data1.get("id").asText();
        String admin2Id = data2.get("id").asText();

        // Store 1 Admin listing users must NOT see Store 2 Admin
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(admin1Id)))
                .andExpect(jsonPath("$[*].id", not(hasItem(admin2Id))));

        // Store 1 Admin attempting to update role/status/password or delete Store 2 Admin -> 403 Forbidden
        mockMvc.perform(put("/api/v1/users/" + admin2Id + "/role")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CASHIER\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/users/" + admin2Id + "/status")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users/" + admin2Id + "/reset-password")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"hacked123\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/users/" + admin2Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isForbidden());

        // Self-demotion & self-deletion blocked -> 400 Bad Request
        mockMvc.perform(put("/api/v1/users/" + admin1Id + "/role")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CASHIER\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/users/" + admin1Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Full-name login fallback removed: duplicate or single full names cannot authenticate")
    void testFullNameFallbackRemoved() throws Exception {
        String s1 = uniqueSuffix();
        String s2 = uniqueSuffix();
        StoreRegistrationRequest reg1 = buildRegReq(s1, "fn1_" + s1);
        reg1.setOwnerName("Shared Full Name");
        StoreRegistrationRequest reg2 = buildRegReq(s2, "fn2_" + s2);
        reg2.setOwnerName("Shared Full Name");

        mockMvc.perform(post("/api/v1/auth/register-store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg1)))
                .andExpect(status().isOk());
        trackCreatedStore(reg1.getStoreCode());

        mockMvc.perform(post("/api/v1/auth/register-store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg2)))
                .andExpect(status().isOk());
        trackCreatedStore(reg2.getStoreCode());

        // Full-name login without storeCode must fail
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("Shared Full Name", "StrongPass123!"))))
                .andExpect(status().isUnauthorized());

        // Full-name login with storeCode must also fail
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("Shared Full Name", "StrongPass123!", reg1.getStoreCode()))))
                .andExpect(status().isUnauthorized());

        // Seeded "Main Cashier" full-name login must also fail
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("Main Cashier", "cashier123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Store Admin portalMode=OWNER restricts login to ADMIN role only")
    void testStoreAdminPortalModeRestriction() throws Exception {
        String[][] nonAdmins = {
                {"cashier", "cashier123"},
                {"manager", "manager123"},
                {"inventory", "inventory123"}
        };
        for (String[] cred : nonAdmins) {
            LoginRequest req = new LoginRequest(cred[0], cred[1]);
            req.setPortalMode("OWNER");
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        LoginRequest adminReq = new LoginRequest("admin", "admin123");
        adminReq.setPortalMode("OWNER");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("Phase 4 RBAC: CASHIER & INVENTORY_STAFF are blocked from privileged endpoints across controllers")
    void testApplicationWideRoleEnforcement() throws Exception {
        MvcResult cashierRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("cashier", "cashier123", "freshgrocery"))))
                .andExpect(status().isOk())
                .andReturn();
        String cashierToken = objectMapper.readTree(cashierRes.getResponse().getContentAsString()).get("token").asText();

        MvcResult invRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("inventory", "inventory123", "freshgrocery"))))
                .andExpect(status().isOk())
                .andReturn();
        String invToken = objectMapper.readTree(invRes.getResponse().getContentAsString()).get("token").asText();

        // CASHIER blocked from Settings PUT, Reports P&L, Expenses, Purchases, Suppliers, Product POST, Inventory, Notifications
        mockMvc.perform(put("/api/v1/settings").header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"storeName\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reports/profit-loss").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/expenses").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/purchases").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/suppliers").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/products").header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"sku\":\"X1\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/inventory").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + cashierToken))
                .andExpect(status().isForbidden());

        // INVENTORY_STAFF blocked from POS Sales, Customers, Reports P&L, Settings PUT
        mockMvc.perform(get("/api/v1/sales").header("Authorization", "Bearer " + invToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + invToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reports/profit-loss").header("Authorization", "Bearer " + invToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/settings").header("Authorization", "Bearer " + invToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"storeName\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Phase 4 Tenant Isolation: CategoryController & SettingsController are scoped per store")
    void testCategoryAndSettingsTenantIsolation() throws Exception {
        String s1 = uniqueSuffix();
        String s2 = uniqueSuffix();
        StoreRegistrationRequest reg1 = buildRegReq(s1, "iso1_" + s1);
        StoreRegistrationRequest reg2 = buildRegReq(s2, "iso2_" + s2);

        MvcResult r1 = mockMvc.perform(post("/api/v1/auth/register-store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg1)))
                .andExpect(status().isOk())
                .andReturn();
        trackCreatedStore(reg1.getStoreCode());

        MvcResult r2 = mockMvc.perform(post("/api/v1/auth/register-store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg2)))
                .andExpect(status().isOk())
                .andReturn();
        trackCreatedStore(reg2.getStoreCode());

        String token1 = objectMapper.readTree(r1.getResponse().getContentAsString()).get("data").get("token").asText();
        String token2 = objectMapper.readTree(r2.getResponse().getContentAsString()).get("data").get("token").asText();

        // Store 1 creates a category; Store 2 must not see or delete it
        MvcResult catRes = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Store1OnlyCat_" + s1 + "\",\"slug\":\"s1cat\",\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        String cat1Id = objectMapper.readTree(catRes.getResponse().getContentAsString()).get("data").get("id").asText();

        mockMvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(cat1Id))));

        mockMvc.perform(delete("/api/v1/categories/" + cat1Id).header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound());

        // Settings are isolated per store
        mockMvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeName").value(reg1.getStoreName()));
        mockMvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeName").value(reg2.getStoreName()));
    }
}
