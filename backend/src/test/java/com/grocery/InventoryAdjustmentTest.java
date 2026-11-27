package com.grocery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocery.model.*;
import com.grocery.repository.*;
import com.grocery.security.JwtTokenProvider;
import com.grocery.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryAdjustmentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private UserRepository userRepository;

    @SpyBean
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Store storeA;
    private Store storeB;
    private String tokenManagerA;
    private String tokenInvStaffA;
    private String tokenCashierA;
    private String tokenManagerB;
    private String usernameManagerA;
    private String suffix;

    @BeforeEach
    void setUp() {
        reset(transactionRepository);
        suffix = UUID.randomUUID().toString().substring(0, 8);

        storeA = storeRepository.save(new Store(
                "store-adj-a-" + suffix,
                "Adj Store A",
                "Owner A",
                "ownerA_" + suffix + "@test.local",
                "9800003001",
                "Bengaluru"
        ));

        usernameManagerA = "adj_mgr_a_" + suffix;
        User managerA = userRepository.save(new User(
                usernameManagerA,
                passwordEncoder.encode("password123"),
                "Manager A",
                "mgrA_" + suffix + "@test.local",
                "9800003001",
                UserRole.STORE_MANAGER,
                storeA.getId(),
                storeA.getStoreCode()
        ));
        tokenManagerA = generateToken(managerA);

        User invStaffA = userRepository.save(new User(
                "adj_inv_a_" + suffix,
                passwordEncoder.encode("password123"),
                "Inv Staff A",
                "invA_" + suffix + "@test.local",
                "9800003002",
                UserRole.INVENTORY_STAFF,
                storeA.getId(),
                storeA.getStoreCode()
        ));
        tokenInvStaffA = generateToken(invStaffA);

        User cashierA = userRepository.save(new User(
                "adj_cashier_a_" + suffix,
                passwordEncoder.encode("password123"),
                "Cashier A",
                "cashierA_" + suffix + "@test.local",
                "9800003003",
                UserRole.CASHIER,
                storeA.getId(),
                storeA.getStoreCode()
        ));
        tokenCashierA = generateToken(cashierA);

        storeB = storeRepository.save(new Store(
                "store-adj-b-" + suffix,
                "Adj Store B",
                "Owner B",
                "ownerB_" + suffix + "@test.local",
                "9800004001",
                "Bengaluru"
        ));

        User managerB = userRepository.save(new User(
                "adj_mgr_b_" + suffix,
                passwordEncoder.encode("password123"),
                "Manager B",
                "mgrB_" + suffix + "@test.local",
                "9800004001",
                UserRole.STORE_MANAGER,
                storeB.getId(),
                storeB.getStoreCode()
        ));
        tokenManagerB = generateToken(managerB);
    }

    private String generateToken(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtTokenProvider.generateToken(auth);
    }

    private Product createProduct(String storeId, String skuSuffix, double currentStock, String status) {
        Product p = new Product(
                "Organic Milk " + skuSuffix,
                "ADJ-SKU-" + suffix + "-" + skuSuffix,
                null,
                null,
                "Dairy",
                "LITER",
                40.0,
                60.0,
                currentStock,
                5.0
        );
        p.setStoreId(storeId);
        p.setStatus(status);
        return productRepository.save(p);
    }

    // ==================== FUNCTIONAL & VALIDATION TESTS ====================

    @Test
    @DisplayName("1,2,11,12: Valid stock increase and reduction adjustments create accurate ledger entries")
    void validIncreaseAndReductionAdjustments_updateStockAndCreateLedger() throws Exception {
        Product p = createProduct(storeA.getId(), "VALID", 20.0, "ACTIVE");

        // Increase (+15)
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productId", p.getId(),
                                "type", "ADJUSTMENT",
                                "quantity", 15.0,
                                "notes", "Found extra carton during count"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.data.quantity").value(15.0))
                .andExpect(jsonPath("$.data.balanceAfter").value(35.0))
                .andExpect(jsonPath("$.data.referenceType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.data.referenceId", startsWith("ADJ-")))
                .andExpect(jsonPath("$.data.performedBy").value(usernameManagerA))
                .andExpect(jsonPath("$.data.notes").value("Found extra carton during count"));

        assertEquals(35.0, productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow().getCurrentStock(), 0.001);

        // Reduction (-5, DAMAGE)
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenInvStaffA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productId", p.getId(),
                                "type", "DAMAGE",
                                "quantity", -5.0,
                                "notes", "Damaged during shelf restock"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("DAMAGE"))
                .andExpect(jsonPath("$.data.quantity").value(-5.0))
                .andExpect(jsonPath("$.data.balanceAfter").value(30.0));

        assertEquals(30.0, productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertEquals(2, transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId()).size());
    }

    @Test
    @DisplayName("3-10: Validation rejects zero, invalid numbers, unsupported type, missing/archived/cross-store product, and insufficient stock")
    void validationAndSecurityRejectInvalidAdjustments() throws Exception {
        Product p = createProduct(storeA.getId(), "VAL", 10.0, "ACTIVE");
        Product archived = createProduct(storeA.getId(), "ARCH", 10.0, "ARCHIVED");
        Product prodB = createProduct(storeB.getId(), "B", 10.0, "ACTIVE");

        // Zero
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + p.getId() + "\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());

        // Invalid numeric values (NaN, Infinity, non-numeric, missing)
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + p.getId() + "\",\"quantity\":\"NaN\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + p.getId() + "\",\"quantity\":\"Infinity\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + p.getId() + "\",\"quantity\":\"abc\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + p.getId() + "\"}"))
                .andExpect(status().isBadRequest());

        // Missing / blank productId
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"   \",\"quantity\":5}"))
                .andExpect(status().isBadRequest());

        // Unsupported adjustment type
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + p.getId() + "\",\"type\":\"INVALID_TYPE\",\"quantity\":5}"))
                .andExpect(status().isBadRequest());

        // Missing product
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"non-existent\",\"quantity\":5}"))
                .andExpect(status().isBadRequest());

        // Archived product
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + archived.getId() + "\",\"quantity\":5}"))
                .andExpect(status().isBadRequest());

        // Cross-store product
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + prodB.getId() + "\",\"quantity\":-2}"))
                .andExpect(status().isBadRequest());

        // Insufficient stock (must NOT silently clamp to 0)
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + p.getId() + "\",\"type\":\"EXPIRY\",\"quantity\":-25}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient stock")));

        // Unauthenticated & unauthorized role
        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + p.getId() + "\",\"quantity\":5}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenCashierA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + p.getId() + "\",\"quantity\":5}"))
                .andExpect(status().isForbidden());

        // Verify no stock was mutated and no ledger entries were created
        assertEquals(10.0, productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertEquals(10.0, productRepository.findByStoreIdAndId(storeB.getId(), prodB.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertTrue(transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId()).isEmpty());
    }

    // ==================== COMPENSATION TESTS ====================

    @Test
    @DisplayName("13-14: Ledger save failure rolls back stock; compensation failure is reported accurately")
    void ledgerFailure_rollsBackStock_andCompensationFailureIsReported() throws Exception {
        Product p = createProduct(storeA.getId(), "COMP", 20.0, "ACTIVE");

        doThrow(new RuntimeException("Simulated ledger failure"))
                .when(transactionRepository).save(any(InventoryTransaction.class));

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + p.getId() + "\",\"type\":\"DAMAGE\",\"quantity\":-6}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("rolled back")));

        assertEquals(20.0, productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertTrue(transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId()).isEmpty());

        // Now simulate compensation failure (product deleted before rollback runs)
        reset(transactionRepository);
        doAnswer(invocation -> {
            productRepository.deleteById(p.getId());
            throw new RuntimeException("Simulated ledger failure");
        }).when(transactionRepository).save(any(InventoryTransaction.class));

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + p.getId() + "\",\"quantity\":5}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", containsString("manual reconciliation required")));
    }

    // ==================== CONCURRENCY TESTS ====================

    @Test
    @DisplayName("Concurrency: Parallel reductions against insufficient stock never go negative")
    void concurrentReductionsAgainstInsufficientStock_neverGoNegative() throws Exception {
        Product p = createProduct(storeA.getId(), "CONC_RED", 10.0, "ACTIVE");

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/v1/inventory/adjustments")
                                .header("Authorization", "Bearer " + tokenManagerA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productId\":\"" + p.getId() + "\",\"type\":\"DAMAGE\",\"quantity\":-4}"))
                        .andReturn().getResponse().getStatus();
            }));
        }

        startLatch.countDown();
        int okCount = 0;
        int badCount = 0;
        for (Future<Integer> f : futures) {
            int code = f.get(15, TimeUnit.SECONDS);
            if (code == 200) okCount++;
            else if (code == 400) badCount++;
        }
        executor.shutdown();

        // From initial 10, only 2 reductions of -4 can succeed (leaving 2.0)
        assertEquals(2, okCount);
        assertEquals(3, badCount);
        Product updated = productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow();
        assertEquals(2.0, updated.getCurrentStock(), 0.001);
        assertTrue(updated.getCurrentStock() >= 0);
        assertEquals(2, transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId()).size());
    }

    @Test
    @DisplayName("Concurrency: Parallel increases accumulate atomically and cross-store adjustments remain isolated")
    void concurrentIncreases_accumulateAtomicallyAndRespectTenantIsolation() throws Exception {
        Product prodA = createProduct(storeA.getId(), "CONC_INC_A", 10.0, "ACTIVE");
        Product prodB = createProduct(storeB.getId(), "CONC_INC_B", 10.0, "ACTIVE");

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads + 2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> okFuturesA = new ArrayList<>();
        List<Future<Integer>> crossTenantFutures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            okFuturesA.add(executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/v1/inventory/adjustments")
                                .header("Authorization", "Bearer " + tokenManagerA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productId\":\"" + prodA.getId() + "\",\"quantity\":5}"))
                        .andReturn().getResponse().getStatus();
            }));
        }

        for (int i = 0; i < 2; i++) {
            crossTenantFutures.add(executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/v1/inventory/adjustments")
                                .header("Authorization", "Bearer " + tokenManagerA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productId\":\"" + prodB.getId() + "\",\"quantity\":-5}"))
                        .andReturn().getResponse().getStatus();
            }));
        }

        startLatch.countDown();
        for (Future<Integer> f : okFuturesA) {
            assertEquals(200, f.get(15, TimeUnit.SECONDS));
        }
        for (Future<Integer> f : crossTenantFutures) {
            assertEquals(400, f.get(15, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertEquals(35.0, productRepository.findByStoreIdAndId(storeA.getId(), prodA.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertEquals(10.0, productRepository.findByStoreIdAndId(storeB.getId(), prodB.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertEquals(5, transactionRepository.findByProductIdOrderByCreatedAtDesc(prodA.getId()).size());
        assertTrue(transactionRepository.findByProductIdOrderByCreatedAtDesc(prodB.getId()).isEmpty());
    }
}
