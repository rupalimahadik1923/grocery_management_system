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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
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
class InventoryRestockTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private UserRepository userRepository;

    @SpyBean
    private InventoryBatchRepository batchRepository;

    @SpyBean
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    private Store storeA;
    private Store storeB;
    private String tokenManagerA;
    private String tokenInventoryStaffA;
    private String tokenCashierA;
    private String tokenManagerB;
    private String usernameManagerA;
    private String suffix;

    @BeforeEach
    void setUp() {
        reset(batchRepository, transactionRepository);
        suffix = UUID.randomUUID().toString().substring(0, 8);

        storeA = storeRepository.save(new Store(
                "store-rst-a-" + suffix,
                "Restock Store A",
                "Owner A",
                "ownerA_" + suffix + "@test.local",
                "9800001001",
                "Bengaluru"
        ));

        usernameManagerA = "mgr_a_" + suffix;
        User managerA = userRepository.save(new User(
                usernameManagerA,
                passwordEncoder.encode("password123"),
                "Manager A",
                "mgrA_" + suffix + "@test.local",
                "9800001001",
                UserRole.STORE_MANAGER,
                storeA.getId(),
                storeA.getStoreCode()
        ));
        tokenManagerA = generateToken(managerA);

        User invStaffA = userRepository.save(new User(
                "inv_a_" + suffix,
                passwordEncoder.encode("password123"),
                "Inventory Staff A",
                "invA_" + suffix + "@test.local",
                "9800001002",
                UserRole.INVENTORY_STAFF,
                storeA.getId(),
                storeA.getStoreCode()
        ));
        tokenInventoryStaffA = generateToken(invStaffA);

        User cashierA = userRepository.save(new User(
                "cashier_a_" + suffix,
                passwordEncoder.encode("password123"),
                "Cashier A",
                "cashierA_" + suffix + "@test.local",
                "9800001003",
                UserRole.CASHIER,
                storeA.getId(),
                storeA.getStoreCode()
        ));
        tokenCashierA = generateToken(cashierA);

        storeB = storeRepository.save(new Store(
                "store-rst-b-" + suffix,
                "Restock Store B",
                "Owner B",
                "ownerB_" + suffix + "@test.local",
                "9800002001",
                "Bengaluru"
        ));

        User managerB = userRepository.save(new User(
                "mgr_b_" + suffix,
                passwordEncoder.encode("password123"),
                "Manager B",
                "mgrB_" + suffix + "@test.local",
                "9800002001",
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

    private Product createProduct(String storeId, String skuSuffix, double currentStock, double costPrice, String status) {
        Product p = new Product(
                "Basmati Rice " + skuSuffix,
                "SKU-" + suffix + "-" + skuSuffix,
                null,
                null,
                "Grains",
                "KG",
                costPrice,
                120.0,
                currentStock,
                5.0
        );
        p.setStoreId(storeId);
        p.setStatus(status);
        return productRepository.save(p);
    }

    private Supplier createSupplier(String storeId, String codeSuffix, String name) {
        Supplier s = new Supplier(
                "SUP-" + suffix + "-" + codeSuffix,
                name,
                "Contact Person",
                "9811111111",
                "sup_" + codeSuffix + "@test.local",
                "29ABCDE1234F1Z5",
                "Bengaluru",
                "Net 30"
        );
        s.setStoreId(storeId);
        return supplierRepository.save(s);
    }

    // ==================== FUNCTIONAL TESTS ====================

    @Test
    @DisplayName("1-7,11-15: Restock product with positive stock creates batch, ledger, defaults cost, and generates batch number")
    void restockProductWithPositiveStock_succeedsAndCreatesBatchAndLedger() throws Exception {
        Product p = createProduct(storeA.getId(), "POS", 10.0, 45.5, "ACTIVE");

        Map<String, Object> body = Map.of(
                "productId", p.getId(),
                "quantity", 25.0,
                "notes", "Morning delivery"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.product.currentStock").value(35.0))
                .andExpect(jsonPath("$.data.batch.initialQty").value(25.0))
                .andExpect(jsonPath("$.data.batch.currentQty").value(25.0))
                .andExpect(jsonPath("$.data.batch.purchaseCost").value(45.5))
                .andExpect(jsonPath("$.data.batch.batchNumber", startsWith("BATCH-")))
                .andExpect(jsonPath("$.data.batch.expiryDate").value(nullValue()))
                .andExpect(jsonPath("$.data.transaction.type").value("RESTOCK"))
                .andExpect(jsonPath("$.data.transaction.quantity").value(25.0))
                .andExpect(jsonPath("$.data.transaction.balanceAfter").value(35.0))
                .andExpect(jsonPath("$.data.transaction.referenceType").value("DIRECT_RESTOCK"))
                .andExpect(jsonPath("$.data.transaction.performedBy").value(usernameManagerA))
                .andReturn();

        Product updated = productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow();
        assertEquals(35.0, updated.getCurrentStock(), 0.001);

        List<InventoryBatch> batches = batchRepository.findByProductId(p.getId());
        assertEquals(1, batches.size());
        assertEquals(25.0, batches.get(0).getInitialQty(), 0.001);
        assertEquals(25.0, batches.get(0).getCurrentQty(), 0.001);
        assertEquals(45.5, batches.get(0).getPurchaseCost(), 0.001);
        assertNull(batches.get(0).getExpiryDate());

        List<InventoryTransaction> txs = transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId());
        assertEquals(1, txs.size());
        assertEquals("RESTOCK", txs.get(0).getType());
        assertEquals(25.0, txs.get(0).getQuantity(), 0.001);
        assertEquals(35.0, txs.get(0).getBalanceAfter(), 0.001);
        assertEquals("DIRECT_RESTOCK", txs.get(0).getReferenceType());
        assertEquals(batches.get(0).getBatchNumber(), txs.get(0).getReferenceId());
        assertEquals(usernameManagerA, txs.get(0).getPerformedBy());
    }

    @Test
    @DisplayName("2: Restock an out-of-stock product (0 -> N) and allow subsequent POS sale")
    void restockOutOfStockProduct_succeedsAndAllowsSale() throws Exception {
        Product p = createProduct(storeA.getId(), "OOS", 0.0, 50.0, "ACTIVE");

        Map<String, Object> body = Map.of(
                "productId", p.getId(),
                "quantity", 15.0
        );

        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenInventoryStaffA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product.currentStock").value(15.0));

        // Verify POS sale works after restocking
        Map<String, Object> saleBody = Map.of(
                "items", List.of(Map.of(
                        "productId", p.getId(),
                        "productName", p.getName(),
                        "quantity", 3.0,
                        "unitPrice", 120.0,
                        "totalPrice", 360.0
                )),
                "subTotal", 360.0,
                "grandTotal", 360.0,
                "paymentMethod", "CASH"
        );

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenCashierA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saleBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Product afterSale = productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow();
        assertEquals(12.0, afterSale.getCurrentStock(), 0.001);
    }

    @Test
    @DisplayName("8-11: Custom purchaseCost, supplier assignment, optional expiryDate, and custom batchNumber")
    void restockWithCustomCostSupplierExpiryAndBatchNumber_persistsAllFields() throws Exception {
        Product p = createProduct(storeA.getId(), "CUST", 5.0, 40.0, "ACTIVE");
        Supplier sup = createSupplier(storeA.getId(), "S1", "Fresh Farms Ltd");
        LocalDate expiry = LocalDate.now().plusDays(45);
        String customBatch = "CUSTOM-BATCH-" + suffix;

        Map<String, Object> body = new HashMap<>();
        body.put("productId", p.getId());
        body.put("quantity", 20.0);
        body.put("purchaseCost", 38.25);
        body.put("supplierId", sup.getId());
        body.put("batchNumber", customBatch);
        body.put("expiryDate", expiry.toString());
        body.put("notes", "Supplier invoice #INV-99");

        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batch.purchaseCost").value(38.25))
                .andExpect(jsonPath("$.data.batch.supplierId").value(sup.getId()))
                .andExpect(jsonPath("$.data.batch.supplierName").value("Fresh Farms Ltd"))
                .andExpect(jsonPath("$.data.batch.batchNumber").value(customBatch))
                .andExpect(jsonPath("$.data.batch.expiryDate").value(expiry.toString()));

        // Duplicate custom batch number in same store must be rejected
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ==================== VALIDATION TESTS ====================

    @Test
    @DisplayName("16-28: Validation rejects invalid productId, quantity, cost, date, archived, missing, and supplier")
    void restockValidationRejectsInvalidInputs() throws Exception {
        Product active = createProduct(storeA.getId(), "VAL", 10.0, 20.0, "ACTIVE");
        Product archived = createProduct(storeA.getId(), "ARCH", 0.0, 20.0, "ARCHIVED");
        Supplier supB = createSupplier(storeB.getId(), "SB", "Store B Supplier");

        // 16. Missing productId
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":10}"))
                .andExpect(status().isBadRequest());

        // 17. Blank productId
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"   \",\"quantity\":10}"))
                .andExpect(status().isBadRequest());

        // 18. Missing quantity
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + active.getId() + "\"}"))
                .andExpect(status().isBadRequest());

        // 19. Zero quantity
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + active.getId() + "\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());

        // 20. Negative quantity
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + active.getId() + "\",\"quantity\":-5}"))
                .andExpect(status().isBadRequest());

        // 21. NaN quantity
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + active.getId() + "\",\"quantity\":\"NaN\"}"))
                .andExpect(status().isBadRequest());

        // 22. Infinity quantity
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + active.getId() + "\",\"quantity\":\"Infinity\"}"))
                .andExpect(status().isBadRequest());

        // 23. Negative cost
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + active.getId() + "\",\"quantity\":5,\"purchaseCost\":-1}"))
                .andExpect(status().isBadRequest());

        // 24. Invalid cost
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + active.getId() + "\",\"quantity\":5,\"purchaseCost\":\"NaN\"}"))
                .andExpect(status().isBadRequest());

        // Invalid date
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + active.getId() + "\",\"quantity\":5,\"expiryDate\":\"2026-99-99\"}"))
                .andExpect(status().isBadRequest());

        // 25. Archived product
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + archived.getId() + "\",\"quantity\":5}"))
                .andExpect(status().isBadRequest());

        // 26. Missing product
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"non-existent-id\",\"quantity\":5}"))
                .andExpect(status().isBadRequest());

        // 27. Invalid supplier
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + active.getId() + "\",\"quantity\":5,\"supplierId\":\"bad-sup-id\"}"))
                .andExpect(status().isBadRequest());

        // 28. Cross-store supplier
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + active.getId() + "\",\"quantity\":5,\"supplierId\":\"" + supB.getId() + "\"}"))
                .andExpect(status().isBadRequest());

        // Verify stock remained untouched after all rejected requests
        Product unchanged = productRepository.findByStoreIdAndId(storeA.getId(), active.getId()).orElseThrow();
        assertEquals(10.0, unchanged.getCurrentStock(), 0.001);
    }

    // ==================== SECURITY & TENANT ISOLATION TESTS ====================

    @Test
    @DisplayName("29-33: Auth, role enforcement, and strict cross-store isolation")
    void restockSecurityAndTenantIsolation() throws Exception {
        Product prodA = createProduct(storeA.getId(), "SECA", 10.0, 20.0, "ACTIVE");
        Product prodB = createProduct(storeB.getId(), "SECB", 10.0, 20.0, "ACTIVE");
        Supplier supB = createSupplier(storeB.getId(), "SUPB", "B Supplier");

        String validBodyA = "{\"productId\":\"" + prodA.getId() + "\",\"quantity\":5}";

        // 29. Unauthenticated
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBodyA))
                .andExpect(status().isUnauthorized());

        // 30. Unauthorized role (CASHIER)
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenCashierA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBodyA))
                .andExpect(status().isForbidden());

        // 31. Store A cannot restock Store B's product
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + prodB.getId() + "\",\"quantity\":10}"))
                .andExpect(status().isBadRequest());

        assertEquals(10.0, productRepository.findById(prodB.getId()).orElseThrow().getCurrentStock(), 0.001);

        // 32. Store A cannot use Store B's supplier
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + prodA.getId() + "\",\"quantity\":10,\"supplierId\":\"" + supB.getId() + "\"}"))
                .andExpect(status().isBadRequest());

        assertEquals(10.0, productRepository.findById(prodA.getId()).orElseThrow().getCurrentStock(), 0.001);

        // 33. Store ID and performedBy derived from auth context even if client sends spoofed fields
        String spoofedBody = "{\"productId\":\"" + prodA.getId() + "\",\"quantity\":5,\"storeId\":\"" + storeB.getId() + "\",\"performedBy\":\"hacker\"}";
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spoofedBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batch.storeId").value(storeA.getId()))
                .andExpect(jsonPath("$.data.transaction.storeId").value(storeA.getId()))
                .andExpect(jsonPath("$.data.transaction.performedBy").value(usernameManagerA));
    }

    // ==================== CONSISTENCY, COMPENSATION & CONCURRENCY TESTS ====================

    @Test
    @DisplayName("34,36,39: Batch save failure rolls back stock and creates no ledger entry")
    void batchSaveFailure_rollsBackStockAndCreatesNoLedger() throws Exception {
        Product p = createProduct(storeA.getId(), "BFAIL", 10.0, 20.0, "ACTIVE");

        doThrow(new RuntimeException("Simulated batch write failure"))
                .when(batchRepository).save(any(InventoryBatch.class));

        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + p.getId() + "\",\"quantity\":15}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("rolled back")));

        Product afterFailure = productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow();
        assertEquals(10.0, afterFailure.getCurrentStock(), 0.001);
        assertTrue(batchRepository.findByProductId(p.getId()).isEmpty());
        assertTrue(transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId()).isEmpty());
    }

    @Test
    @DisplayName("35,36,38,39: Ledger save failure rolls back stock and deletes orphan batch")
    void ledgerSaveFailure_rollsBackStockAndDeletesOrphanBatch() throws Exception {
        Product p = createProduct(storeA.getId(), "TFAIL", 10.0, 20.0, "ACTIVE");

        doThrow(new RuntimeException("Simulated ledger write failure"))
                .when(transactionRepository).save(any(InventoryTransaction.class));

        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + p.getId() + "\",\"quantity\":15}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("rolled back")));

        Product afterFailure = productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow();
        assertEquals(10.0, afterFailure.getCurrentStock(), 0.001);
        assertTrue(batchRepository.findByProductId(p.getId()).isEmpty(), "Orphan batch must be deleted");
        assertTrue(transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId()).isEmpty());
    }

    @Test
    @DisplayName("37: Compensation failure is reported accurately without claiming rollback succeeded")
    void compensationFailure_isReportedAccurately() throws Exception {
        Product p = createProduct(storeA.getId(), "CFAIL", 10.0, 20.0, "ACTIVE");

        // Delete product right as batchRepository.save is called so rollbackStockIncrement finds 0 docs
        doAnswer(invocation -> {
            productRepository.deleteById(p.getId());
            throw new RuntimeException("Simulated batch write failure");
        }).when(batchRepository).save(any(InventoryBatch.class));

        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + p.getId() + "\",\"quantity\":15}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("manual reconciliation required")));
    }

    @Test
    @DisplayName("40: Concurrent stock-in requests atomically accumulate all increments")
    void concurrentRestockRequests_atomicallyAccumulateStock() throws Exception {
        Product p = createProduct(storeA.getId(), "CONC", 0.0, 20.0, "ACTIVE");

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                MvcResult res = mockMvc.perform(post("/api/v1/inventory/restock")
                                .header("Authorization", "Bearer " + tokenManagerA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productId\":\"" + p.getId() + "\",\"quantity\":10}"))
                        .andReturn();
                return res.getResponse().getStatus();
            }));
        }

        startLatch.countDown();
        for (Future<Integer> f : futures) {
            assertEquals(200, f.get(15, TimeUnit.SECONDS));
        }
        executor.shutdown();

        Product updated = productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow();
        assertEquals(50.0, updated.getCurrentStock(), 0.001);
        assertEquals(5, batchRepository.findByProductId(p.getId()).size());
        assertEquals(5, transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId()).size());
    }

    @Test
    @DisplayName("41: Idempotency handles sequential retries, concurrent duplicates, and payload conflicts")
    void idempotentRestock_preventsDoubleStockingAndRejectsConflicts() throws Exception {
        Product p = createProduct(storeA.getId(), "IDEM", 0.0, 20.0, "ACTIVE");
        String idemKey = "restock-key-" + suffix;

        String payload = objectMapper.writeValueAsString(Map.of(
                "productId", p.getId(),
                "quantity", 20.0,
                "idempotencyKey", idemKey
        ));

        // Concurrent duplicate requests with the same key and payload
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                MvcResult res = mockMvc.perform(post("/api/v1/inventory/restock")
                                .header("Authorization", "Bearer " + tokenManagerA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andReturn();
                return res.getResponse().getStatus();
            }));
        }

        startLatch.countDown();
        for (Future<Integer> f : futures) {
            assertEquals(200, f.get(15, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // Stock must only be incremented ONCE
        Product afterConcurrent = productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow();
        assertEquals(20.0, afterConcurrent.getCurrentStock(), 0.001);
        assertEquals(1, batchRepository.findByProductId(p.getId()).size());
        assertEquals(1, transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId()).size());

        // Same key with DIFFERENT quantity must return 409 Conflict
        String conflictPayload = objectMapper.writeValueAsString(Map.of(
                "productId", p.getId(),
                "quantity", 99.0,
                "idempotencyKey", idemKey
        ));
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Phase 1A.1: Unique index storeId_batchNumber_unique_idx is persisted and enforces concurrent custom batch uniqueness")
    void uniqueBatchNumberIndexPersisted_andPreventsConcurrentDuplicates() throws Exception {
        var indexes = mongoTemplate.indexOps(InventoryBatch.class).getIndexInfo();
        boolean hasUniqueBatchIdx = indexes.stream()
                .anyMatch(idx -> "storeId_batchNumber_unique_idx".equals(idx.getName()) && idx.isUnique());
        assertTrue(hasUniqueBatchIdx, "storeId_batchNumber_unique_idx unique index must exist on inventory_batches");

        Product p = createProduct(storeA.getId(), "BATCHCONC", 0.0, 20.0, "ACTIVE");
        String sharedCustomBatch = "RACE-BATCH-" + suffix;
        String payload = objectMapper.writeValueAsString(Map.of(
                "productId", p.getId(),
                "quantity", 12.0,
                "batchNumber", sharedCustomBatch
        ));

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/v1/inventory/restock")
                                .header("Authorization", "Bearer " + tokenManagerA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andReturn().getResponse().getStatus();
            }));
        }

        startLatch.countDown();
        int okCount = 0;
        int rejectedCount = 0;
        for (Future<Integer> f : futures) {
            int code = f.get(15, TimeUnit.SECONDS);
            if (code == 200) okCount++;
            else if (code == 400 || code == 409) rejectedCount++;
        }
        executor.shutdown();

        assertEquals(1, okCount, "Exactly one concurrent request should win custom batch creation");
        assertEquals(threads - 1, rejectedCount, "All losing concurrent requests must be rejected with 400/409");
        assertEquals(12.0, productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertEquals(1, batchRepository.findByProductId(p.getId()).size());
    }

    @Test
    @DisplayName("Phase 1A.1: Concurrent conflicting idempotency keys and cross-store batch isolation")
    void concurrentConflictingIdempotencyKeys_andCrossStoreBatchIsolation() throws Exception {
        Product prodA = createProduct(storeA.getId(), "CONF_A", 0.0, 20.0, "ACTIVE");
        Product prodB = createProduct(storeB.getId(), "CONF_B", 0.0, 20.0, "ACTIVE");

        // Same custom batchNumber is allowed across different stores
        String crossStoreBatch = "CROSS-BATCH-" + suffix;
        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productId", prodB.getId(),
                                "quantity", 5.0,
                                "batchNumber", crossStoreBatch))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/inventory/restock")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productId", prodA.getId(),
                                "quantity", 7.0,
                                "batchNumber", crossStoreBatch))))
                .andExpect(status().isOk());

        // Concurrent conflicting idempotency keys (same key, different quantities)
        String conflictKey = "conc-conflict-" + suffix;
        String body1 = objectMapper.writeValueAsString(Map.of(
                "productId", prodA.getId(),
                "quantity", 10.0,
                "idempotencyKey", conflictKey));
        String body2 = objectMapper.writeValueAsString(Map.of(
                "productId", prodA.getId(),
                "quantity", 30.0,
                "idempotencyKey", conflictKey));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        Future<Integer> f1 = executor.submit(() -> {
            startLatch.await();
            return mockMvc.perform(post("/api/v1/inventory/restock")
                            .header("Authorization", "Bearer " + tokenManagerA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body1))
                    .andReturn().getResponse().getStatus();
        });
        Future<Integer> f2 = executor.submit(() -> {
            startLatch.await();
            return mockMvc.perform(post("/api/v1/inventory/restock")
                            .header("Authorization", "Bearer " + tokenManagerA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body2))
                    .andReturn().getResponse().getStatus();
        });

        startLatch.countDown();
        int s1 = f1.get(15, TimeUnit.SECONDS);
        int s2 = f2.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue((s1 == 200 && s2 == 409) || (s1 == 409 && s2 == 200),
                "One request must succeed (200) and the conflicting one must fail (409), got: " + s1 + ", " + s2);

        double expectedStock = 7.0 + (s1 == 200 ? 10.0 : 30.0);
        assertEquals(expectedStock, productRepository.findByStoreIdAndId(storeA.getId(), prodA.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertEquals(2, batchRepository.findByProductId(prodA.getId()).size(), "Losing request must not leave an extra batch");
        assertEquals(5.0, productRepository.findByStoreIdAndId(storeB.getId(), prodB.getId()).orElseThrow().getCurrentStock(), 0.001);
    }
}
