package com.grocery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocery.dto.ApiResponse;
import com.grocery.model.*;
import com.grocery.repository.*;
import com.grocery.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductCreationBrutalTest {

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

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Store storeA;
    private Store storeB;
    private String tokenAdminA;
    private String tokenCashierA;
    private String tokenAdminB;

    @BeforeEach
    void setUp() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);

        // Store A
        storeA = storeRepository.save(new Store(
                "store-a-" + testSuffix,
                "Store A",
                "Owner A",
                "ownerA_" + testSuffix + "@test.local",
                "9800000001",
                "Bengaluru"
        ));

        User adminA = userRepository.save(new User(
                "adminA_" + testSuffix,
                passwordEncoder.encode("password123"),
                "Admin A",
                "adminA_" + testSuffix + "@test.local",
                "9800000001",
                UserRole.ADMIN,
                storeA.getId(),
                storeA.getStoreCode()
        ));
        com.grocery.security.UserPrincipal principalA = new com.grocery.security.UserPrincipal(adminA);
        tokenAdminA = jwtTokenProvider.generateToken(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principalA, null, principalA.getAuthorities()));

        User cashierA = userRepository.save(new User(
                "cashierA_" + testSuffix,
                passwordEncoder.encode("password123"),
                "Cashier A",
                "cashierA_" + testSuffix + "@test.local",
                "9800000002",
                UserRole.CASHIER,
                storeA.getId(),
                storeA.getStoreCode()
        ));
        com.grocery.security.UserPrincipal principalCashierA = new com.grocery.security.UserPrincipal(cashierA);
        tokenCashierA = jwtTokenProvider.generateToken(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principalCashierA, null, principalCashierA.getAuthorities()));

        // Store B
        storeB = storeRepository.save(new Store(
                "store-b-" + testSuffix,
                "Store B",
                "Owner B",
                "ownerB_" + testSuffix + "@test.local",
                "9800000003",
                "Bengaluru"
        ));

        User adminB = userRepository.save(new User(
                "adminB_" + testSuffix,
                passwordEncoder.encode("password123"),
                "Admin B",
                "adminB_" + testSuffix + "@test.local",
                "9800000003",
                UserRole.ADMIN,
                storeB.getId(),
                storeB.getStoreCode()
        ));
        com.grocery.security.UserPrincipal principalB = new com.grocery.security.UserPrincipal(adminB);
        tokenAdminB = jwtTokenProvider.generateToken(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principalB, null, principalB.getAuthorities()));
    }

    private Map<String, Object> basePayload() {
        Map<String, Object> p = new HashMap<>();
        p.put("name", "Valid Product");
        p.put("sku", "SKU-" + UUID.randomUUID().toString().substring(0, 8));
        p.put("costPrice", 50.0);
        p.put("sellingPrice", 75.0);
        p.put("currentStock", 20.0);
        p.put("minStockLevel", 5.0);
        p.put("taxRate", 5.0);
        p.put("unit", "PACK");
        p.put("status", "ACTIVE");
        return p;
    }

    // ==========================================
    // 1. RBAC & AUTHENTICATION ATTACKS
    // ==========================================

    @Test
    @DisplayName("BRUTAL-01: Unauthenticated request must be rejected with 401")
    void brutal01_unauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(basePayload())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("BRUTAL-02: Cashier role must be rejected with 403 Forbidden")
    void brutal02_cashierForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenCashierA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(basePayload())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BRUTAL-31: Invalid or malformed Bearer token must return 401")
    void brutal31_invalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer invalid.jwt.signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(basePayload())))
                .andExpect(status().isUnauthorized());
    }

    // ==========================================
    // 2. TENANT SPOOFING & DOCUMENT HIJACKING
    // ==========================================

    @Test
    @DisplayName("BRUTAL-03: Client-supplied storeId must be strictly ignored and bound to Store A")
    void brutal03_tenantSpoofing() throws Exception {
        Map<String, Object> payload = basePayload();
        payload.put("storeId", storeB.getId()); // Store A user maliciously claims Store B ownership

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeId").value(storeA.getId()))
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
        Product saved = productRepository.findById(id).orElseThrow();
        assertEquals(storeA.getId(), saved.getStoreId(), "Document must be owned by Store A");
        assertFalse(productRepository.existsByStoreIdAndSku(storeB.getId(), saved.getSku()), "Store B must not own this product");
    }

    @Test
    @DisplayName("BRUTAL-04: Existing product ID spoofing must be cleared, creating new document without overwrite")
    void brutal04_documentHijacking() throws Exception {
        // Pre-create a product in Store B
        Product prodB = new Product("Victim Product", "SKU-VICTIM-B", null, null, null, "PCS", 10.0, 15.0, 50.0, 5.0);
        prodB.setStoreId(storeB.getId());
        prodB = productRepository.save(prodB);

        Map<String, Object> payload = basePayload();
        payload.put("id", prodB.getId()); // Attacker supplies Store B's product ID to overwrite it
        payload.put("name", "Hijacker Item");

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();

        String generatedId = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
        assertNotEquals(prodB.getId(), generatedId, "Server must generate a fresh ID and never reuse client ID");

        // Assert victim product in Store B remains completely untouched
        Product unchangedVictim = productRepository.findById(prodB.getId()).orElseThrow();
        assertEquals("Victim Product", unchangedVictim.getName());
        assertEquals(storeB.getId(), unchangedVictim.getStoreId());
    }

    // ==========================================
    // 3. PRODUCT NAME VALIDATION & SANITIZATION
    // ==========================================

    @Test
    @DisplayName("BRUTAL-05: Missing, empty, or whitespace-only product name must be rejected with 400")
    void brutal05_nameValidation() throws Exception {
        Map<String, Object> p1 = basePayload();
        p1.put("name", null);
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Product name is required")));

        Map<String, Object> p2 = basePayload();
        p2.put("name", "    ");
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Product name is required")));
    }

    @Test
    @DisplayName("BRUTAL-22: Control characters (null bytes) in name or SKU must be rejected with 400")
    void brutal22_controlCharacters() throws Exception {
        Map<String, Object> p = basePayload();
        p.put("name", "Bad\u0000Name");
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("control characters")));

        Map<String, Object> pSku = basePayload();
        pSku.put("sku", "SKU\u0000BAD");
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pSku)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("control characters")));
    }

    @Test
    @DisplayName("BRUTAL-23: Massive 50KB string exceeding max length must be rejected with 400")
    void brutal23_massiveString() throws Exception {
        Map<String, Object> p = basePayload();
        p.put("name", "A".repeat(500)); // Limit is 150
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("cannot exceed 150 characters")));
    }

    // ==========================================
    // 4. SKU NORMALIZATION & CROSS-STORE ISOLATION
    // ==========================================

    @Test
    @DisplayName("BRUTAL-06: Missing or whitespace SKU must be rejected with 400")
    void brutal06_missingSku() throws Exception {
        Map<String, Object> p = basePayload();
        p.put("sku", "  \t  ");
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("SKU is required")));
    }

    @Test
    @DisplayName("BRUTAL-07: Duplicate SKU case/whitespace variants in same store must be rejected with 400")
    void brutal07_duplicateSkuSameStore() throws Exception {
        String baseSku = "SKU-DUP-" + UUID.randomUUID().toString().substring(0, 6);

        Map<String, Object> p1 = basePayload();
        p1.put("sku", baseSku);
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p1)))
                .andExpect(status().isOk());

        // Attempt second insert with lowercase and whitespace variant
        Map<String, Object> p2 = basePayload();
        p2.put("sku", "  " + baseSku.toLowerCase() + "  ");
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("SKU already exists in this store")));
    }

    @Test
    @DisplayName("BRUTAL-08: Same SKU across Store A and Store B must succeed (Multi-tenant safety)")
    void brutal08_sharedSkuCrossStore() throws Exception {
        String sharedSku = "SKU-CROSS-" + UUID.randomUUID().toString().substring(0, 6);

        Map<String, Object> pA = basePayload();
        pA.put("sku", sharedSku);
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pA)))
                .andExpect(status().isOk());

        Map<String, Object> pB = basePayload();
        pB.put("sku", sharedSku);
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeId").value(storeB.getId()))
                .andExpect(jsonPath("$.data.sku").value(sharedSku.toUpperCase()));
    }

    // ==========================================
    // 5. NUMERIC BOUNDS & PRICE INTEGRITY
    // ==========================================

    @Test
    @DisplayName("BRUTAL-09: Negative cost price must be rejected with 400")
    void brutal09_negativeCostPrice() throws Exception {
        Map<String, Object> p = basePayload();
        p.put("costPrice", -0.01);
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("cannot be negative")));
    }

    @Test
    @DisplayName("BRUTAL-10: Negative selling price must be rejected with 400")
    void brutal10_negativeSellingPrice() throws Exception {
        Map<String, Object> p = basePayload();
        p.put("sellingPrice", -10.0);
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("cannot be negative")));
    }

    @Test
    @DisplayName("BRUTAL-11: Negative initial stock must be rejected with 400")
    void brutal11_negativeStock() throws Exception {
        Map<String, Object> p = basePayload();
        p.put("currentStock", -5.0);
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Initial stock cannot be negative")));
    }

    @Test
    @DisplayName("BRUTAL-12: Extreme price exceeding maximum boundary must be rejected with 400")
    void brutal12_extremePriceOverflow() throws Exception {
        Map<String, Object> p = basePayload();
        p.put("sellingPrice", 5_000_000.0); // Exceeds 1,000,000 limit
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Price exceeds maximum permitted limit")));
    }

    // ==========================================
    // 6. CATEGORY VALIDATION & DERIVATION
    // ==========================================

    @Test
    @DisplayName("BRUTAL-13: Cross-store category ID must be rejected with 400")
    void brutal13_crossStoreCategory() throws Exception {
        Category catB = categoryRepository.save(new Category("Dairy B", "dairy-b", "🥛", "Desc", 1, storeB.getId()));

        Map<String, Object> p = basePayload();
        p.put("categoryId", catB.getId()); // Store A user passes Store B category
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Category does not belong to your store")));
    }

    @Test
    @DisplayName("BRUTAL-14: Inactive category must be rejected with 400")
    void brutal14_inactiveCategory() throws Exception {
        Category inactiveCat = new Category("Old Dairy", "old-dairy", "🥛", "Desc", 1, storeA.getId());
        inactiveCat.setActive(false);
        inactiveCat = categoryRepository.save(inactiveCat);

        Map<String, Object> p = basePayload();
        p.put("categoryId", inactiveCat.getId());
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Cannot assign an inactive category")));
    }

    @Test
    @DisplayName("BRUTAL-15: Non-existent category ID must be rejected with 400")
    void brutal15_nonExistentCategory() throws Exception {
        Map<String, Object> p = basePayload();
        p.put("categoryId", "654321098765432109876543");
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Category does not belong to your store")));
    }

    @Test
    @DisplayName("BRUTAL-16: Forged categoryName in payload must be overridden with DB value")
    void brutal16_categoryNameForgery() throws Exception {
        Category catA = categoryRepository.save(new Category("Authentic Produce", "produce", "🍎", "Desc", 1, storeA.getId()));

        Map<String, Object> p = basePayload();
        p.put("categoryId", catA.getId());
        p.put("categoryName", "Hacked Category Name"); // Forged in body

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
        Product saved = productRepository.findById(id).orElseThrow();
        assertEquals("Authentic Produce", saved.getCategoryName(), "Database category name must prevail");
    }

    // ==========================================
    // 7. BARCODE & STATUS SANITIZATION
    // ==========================================

    @Test
    @DisplayName("BRUTAL-17: Whitespace or empty barcode must be normalized to null")
    void brutal17_whitespaceBarcode() throws Exception {
        Map<String, Object> p = basePayload();
        p.put("barcode", "   \t  ");

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
        Product saved = productRepository.findById(id).orElseThrow();
        assertNull(saved.getBarcode(), "Whitespace barcode must be normalized to null");
    }

    @Test
    @DisplayName("BRUTAL-18: Submitting explicit ARCHIVED or DELETED status on creation must be rejected with 400")
    void brutal18_statusTampering() throws Exception {
        Map<String, Object> p = basePayload();
        p.put("status", "ARCHIVED");

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("New products cannot be created in ARCHIVED or DELETED status")));

        Map<String, Object> p2 = basePayload();
        p2.put("status", "INVALID_JUNK");
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Invalid product status")));
    }

    // ==========================================
    // 8. XSS & NOSQL INJECTION PAYLOADS
    // ==========================================

    @Test
    @DisplayName("BRUTAL-19: XSS and SQL injection strings must be stored safely as literal text")
    void brutal19_xssAndInjectionPayloads() throws Exception {
        Map<String, Object> p = basePayload();
        p.put("name", "<script>alert('XSS')</script>");
        p.put("description", "'; DROP TABLE products; -- {\"$gt\": \"\"}");

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
        Product saved = productRepository.findById(id).orElseThrow();
        assertEquals("<script>alert('XSS')</script>", saved.getName(), "Text must be stored faithfully without execution");
        assertEquals("'; DROP TABLE products; -- {\"$gt\": \"\"}", saved.getDescription());
    }

    // ==========================================
    // 9. CONCURRENT SKU RACE CONDITION TEST
    // ==========================================

    @Test
    @DisplayName("BRUTAL-20: 10 concurrent threads creating same SKU in Store A must produce exactly 1 product and 9 400s")
    void brutal20_concurrentSameSkuRace() throws Exception {
        String raceSku = "SKU-RACE-" + UUID.randomUUID().toString().substring(0, 8);
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronized release of all 10 threads
                    Map<String, Object> payload = basePayload();
                    payload.put("name", "Concurrent Product " + index);
                    payload.put("sku", raceSku);

                    MvcResult result = mockMvc.perform(post("/api/v1/products")
                                    .header("Authorization", "Bearer " + tokenAdminA)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(payload)))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    statusCodes.add(status);
                    if (status == 200) {
                        successCount.incrementAndGet();
                    } else if (status == 400) {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All concurrent requests must complete within timeout");
        assertEquals(1, successCount.get(), "Exactly ONE thread must succeed in creating the product");
        assertEquals(9, failureCount.get(), "Exactly NINE threads must be rejected with 400 Bad Request");

        // Database verification: Exactly 1 document exists for this store & SKU
        List<Product> matches = productRepository.findByStoreId(storeA.getId()).stream()
                .filter(prod -> raceSku.equalsIgnoreCase(prod.getSku()))
                .toList();
        assertEquals(1, matches.size(), "Database must contain exactly 1 document for the canonical SKU");
    }
}
