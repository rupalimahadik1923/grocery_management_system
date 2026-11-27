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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.test.mock.mockito.SpyBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@AutoConfigureMockMvc
class SaleControllerTest {

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
    private SaleRepository saleRepository;

    @SpyBean
    private InventoryTransactionRepository transactionRepository;

    @SpyBean
    private CustomerRepository customerRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Store storeA;
    private Store storeB;
    private String tokenStoreA;
    private String tokenStoreB;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // Store A
        storeA = storeRepository.save(new Store(
                "store-pos-a-" + suffix,
                "Store POS A",
                "Owner A",
                "ownerA_" + suffix + "@test.local",
                "9800000001",
                "Bengaluru"
        ));

        User userA = userRepository.save(new User(
                "pos_cashier_a_" + suffix,
                passwordEncoder.encode("password123"),
                "Cashier A",
                "cashierA_" + suffix + "@test.local",
                "9800000001",
                UserRole.CASHIER,
                storeA.getId(),
                storeA.getStoreCode()
        ));

        UserPrincipal principalA = new UserPrincipal(userA);
        UsernamePasswordAuthenticationToken authA =
                new UsernamePasswordAuthenticationToken(principalA, null, principalA.getAuthorities());
        tokenStoreA = jwtTokenProvider.generateToken(authA);

        // Store B
        storeB = storeRepository.save(new Store(
                "store-pos-b-" + suffix,
                "Store POS B",
                "Owner B",
                "ownerB_" + suffix + "@test.local",
                "9800000002",
                "Bengaluru"
        ));

        User userB = userRepository.save(new User(
                "pos_cashier_b_" + suffix,
                passwordEncoder.encode("password123"),
                "Cashier B",
                "cashierB_" + suffix + "@test.local",
                "9800000002",
                UserRole.CASHIER,
                storeB.getId(),
                storeB.getStoreCode()
        ));

        UserPrincipal principalB = new UserPrincipal(userB);
        UsernamePasswordAuthenticationToken authB =
                new UsernamePasswordAuthenticationToken(principalB, null, principalB.getAuthorities());
        tokenStoreB = jwtTokenProvider.generateToken(authB);
    }

    private Product createProduct(String storeId, String name, double stock, double minStock, String status) {
        Product p = new Product();
        p.setStoreId(storeId);
        p.setName(name);
        p.setCategoryId("CAT-TEST");
        p.setCategoryName("Grocery");
        p.setSellingPrice(100.0);
        p.setCostPrice(70.0);
        p.setCurrentStock(stock);
        p.setMinStockLevel(minStock);
        p.setUnit("PCS");
        p.setStatus(status);
        p.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return productRepository.save(p);
    }

    @Test
    @DisplayName("Test 1: Checkout with sufficient stock succeeds and deducts stock accurately")
    void testCheckoutSufficientStockSuccess() throws Exception {
        Product p = createProduct(storeA.getId(), "Organic Rice", 25.0, 5.0, "ACTIVE");

        Sale sale = new Sale();
        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(5.0);
        item.setUnitPrice(100.0);
        item.setLineTotal(500.0);

        sale.setItems(List.of(item));
        sale.setSubtotal(500.0);
        sale.setGrandTotal(500.0);
        sale.setPaymentMethod("CASH");

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sale)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.receiptNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].costPrice").value(70.0));

        // Verify stock deducted
        Product updated = productRepository.findById(p.getId()).orElseThrow();
        assertEquals(20.0, updated.getCurrentStock(), 0.001);

        // Verify inventory transaction
        List<InventoryTransaction> txs = transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId());
        assertFalse(txs.isEmpty());
        assertEquals(-5.0, txs.get(0).getQuantity());
        assertEquals(20.0, txs.get(0).getBalanceAfter());
        assertEquals("SALE", txs.get(0).getType());
    }

    @Test
    @DisplayName("Test 2: Checkout with insufficient stock is rejected with 400 and stock remains unchanged")
    void testCheckoutInsufficientStockRejected() throws Exception {
        Product p = createProduct(storeA.getId(), "Premium Tea", 3.0, 1.0, "ACTIVE");

        Sale sale = new Sale();
        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(5.0); // requesting 5, only 3 available
        item.setUnitPrice(100.0);
        item.setLineTotal(500.0);

        sale.setItems(List.of(item));
        sale.setSubtotal(500.0);
        sale.setGrandTotal(500.0);
        sale.setPaymentMethod("CASH");

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sale)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Insufficient stock")));

        // Verify stock was NOT altered
        Product reloaded = productRepository.findById(p.getId()).orElseThrow();
        assertEquals(3.0, reloaded.getCurrentStock(), 0.001);
    }

    @Test
    @DisplayName("Test 3: Multiple cart lines for same product aggregate requested quantity and reject if sum exceeds stock")
    void testCheckoutDuplicateLinesAggregatedRejectsExceedingStock() throws Exception {
        Product p = createProduct(storeA.getId(), "Sugar 1kg", 6.0, 1.0, "ACTIVE");

        Sale sale = new Sale();
        Sale.SaleItem item1 = new Sale.SaleItem();
        item1.setProductId(p.getId());
        item1.setProductName(p.getName());
        item1.setQuantity(4.0);
        item1.setUnitPrice(50.0);
        item1.setLineTotal(200.0);

        Sale.SaleItem item2 = new Sale.SaleItem();
        item2.setProductId(p.getId());
        item2.setProductName(p.getName());
        item2.setQuantity(3.0); // 4 + 3 = 7, but stock is 6
        item2.setUnitPrice(50.0);
        item2.setLineTotal(150.0);

        sale.setItems(List.of(item1, item2));
        sale.setSubtotal(350.0);
        sale.setGrandTotal(350.0);
        sale.setPaymentMethod("CASH");

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sale)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Insufficient stock")));

        Product reloaded = productRepository.findById(p.getId()).orElseThrow();
        assertEquals(6.0, reloaded.getCurrentStock(), 0.001);
    }

    @Test
    @DisplayName("Test 4: Negative or zero quantities in checkout are rejected")
    void testCheckoutNegativeOrZeroQuantityRejected() throws Exception {
        Product p = createProduct(storeA.getId(), "Milk Carton", 10.0, 2.0, "ACTIVE");

        Sale sale = new Sale();
        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(-2.0); // negative!
        item.setUnitPrice(50.0);
        item.setLineTotal(-100.0);

        sale.setItems(List.of(item));
        sale.setPaymentMethod("CASH");

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sale)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("greater than 0")));

        // Zero quantity
        item.setQuantity(0.0);
        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sale)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("greater than 0")));

        Product reloaded = productRepository.findById(p.getId()).orElseThrow();
        assertEquals(10.0, reloaded.getCurrentStock(), 0.001);
    }

    @Test
    @DisplayName("Test 5: Inactive product checkout is rejected")
    void testCheckoutInactiveProductRejected() throws Exception {
        Product p = createProduct(storeA.getId(), "Discontinued Biscuit", 50.0, 5.0, "INACTIVE");

        Sale sale = new Sale();
        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(1.0);
        item.setUnitPrice(20.0);
        item.setLineTotal(20.0);

        sale.setItems(List.of(item));
        sale.setPaymentMethod("CASH");

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sale)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Inactive product cannot be sold")));

        Product reloaded = productRepository.findById(p.getId()).orElseThrow();
        assertEquals(50.0, reloaded.getCurrentStock(), 0.001);
    }

    @Test
    @DisplayName("Test 6: Cross-store product checkout is rejected")
    void testCheckoutCrossStoreProductRejected() throws Exception {
        Product pStoreB = createProduct(storeB.getId(), "Store B Specialty", 20.0, 5.0, "ACTIVE");

        Sale sale = new Sale();
        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(pStoreB.getId());
        item.setProductName(pStoreB.getName());
        item.setQuantity(1.0);
        item.setUnitPrice(30.0);
        item.setLineTotal(30.0);

        sale.setItems(List.of(item));
        sale.setPaymentMethod("CASH");

        // Attempting checkout from Store A with Store B's product
        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sale)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("does not belong to your store")));

        Product reloaded = productRepository.findById(pStoreB.getId()).orElseThrow();
        assertEquals(20.0, reloaded.getCurrentStock(), 0.001);
    }

    @Test
    @DisplayName("Test 7: Idempotent checkout retry with same key returns existing sale and does not deduct stock twice")
    void testCheckoutIdempotencyPreventsDuplicateSaleAndDoubleDeduction() throws Exception {
        Product p = createProduct(storeA.getId(), "Cooking Oil", 10.0, 2.0, "ACTIVE");
        String idemKey = "idem-" + UUID.randomUUID().toString();

        Sale sale = new Sale();
        sale.setIdempotencyKey(idemKey);
        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(2.0);
        item.setUnitPrice(150.0);
        item.setLineTotal(300.0);

        sale.setItems(List.of(item));
        sale.setSubtotal(300.0);
        sale.setGrandTotal(300.0);
        sale.setPaymentMethod("UPI");

        // First attempt
        MvcResult res1 = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sale)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        Product stockAfterFirst = productRepository.findById(p.getId()).orElseThrow();
        assertEquals(8.0, stockAfterFirst.getCurrentStock(), 0.001);

        // Second attempt with SAME idempotency key
        MvcResult res2 = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sale)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message", containsString("idempotent")))
                .andReturn();

        // Stock must still be 8.0, NOT 6.0!
        Product stockAfterSecond = productRepository.findById(p.getId()).orElseThrow();
        assertEquals(8.0, stockAfterSecond.getCurrentStock(), 0.001);

        // Receipt numbers must match
        Map map1 = objectMapper.readValue(res1.getResponse().getContentAsString(), Map.class);
        Map map2 = objectMapper.readValue(res2.getResponse().getContentAsString(), Map.class);
        Map data1 = (Map) map1.get("data");
        Map data2 = (Map) map2.get("data");
        assertEquals(data1.get("id"), data2.get("id"));
        assertEquals(data1.get("receiptNumber"), data2.get("receiptNumber"));
    }

    @Test
    @DisplayName("Test 8: Concurrent checkouts competing for limited stock do not oversell")
    void testConcurrentCheckoutsPreventOverselling() throws Exception {
        // Stock is only 3. 5 concurrent requests try to buy 1 each.
        // Exactly 3 must succeed, 2 must fail. Final stock must be exactly 0.0, never negative!
        Product p = createProduct(storeA.getId(), "Limited Edition Chocolate", 3.0, 1.0, "ACTIVE");

        int concurrency = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrency);

        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Sale sale = new Sale();
                    Sale.SaleItem item = new Sale.SaleItem();
                    item.setProductId(p.getId());
                    item.setProductName(p.getName());
                    item.setQuantity(1.0);
                    item.setUnitPrice(80.0);
                    item.setLineTotal(80.0);

                    sale.setItems(List.of(item));
                    sale.setGrandTotal(80.0);
                    sale.setPaymentMethod("CASH");

                    MvcResult result = mockMvc.perform(post("/api/v1/sales")
                                    .header("Authorization", "Bearer " + tokenStoreA)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(sale)))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    statuses.add(status);
                    if (status != 200 && status != 400) {
                        System.out.println("Concurrent checkout non-standard status: " + status + " body: " + result.getResponse().getContentAsString());
                    }
                } catch (Exception e) {
                    System.out.println("Concurrent checkout exception: " + e.getMessage());
                    statuses.add(500);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        long successCount = statuses.stream().filter(s -> s == 200).count();
        long badRequestCount = statuses.stream().filter(s -> s == 400).count();

        assertEquals(3, successCount, "Exactly 3 checkouts should succeed for stock of 3");
        assertEquals(2, badRequestCount, "Exactly 2 checkouts should fail with 400 Insufficient stock");

        Product finalProduct = productRepository.findById(p.getId()).orElseThrow();
        assertEquals(0.0, finalProduct.getCurrentStock(), 0.001, "Final stock must be exactly 0.0, never negative");
    }

    @Test
    @DisplayName("Test 9: Compensating rollback restores stock if multi-item sale fails mid-way")
    void testCompensatingRollbackWhenMultiItemSaleFails() throws Exception {
        Product p1 = createProduct(storeA.getId(), "Item In Stock", 10.0, 2.0, "ACTIVE");
        Product p2 = createProduct(storeA.getId(), "Item Out Of Stock", 1.0, 1.0, "ACTIVE");

        Sale sale = new Sale();
        Sale.SaleItem item1 = new Sale.SaleItem();
        item1.setProductId(p1.getId());
        item1.setProductName(p1.getName());
        item1.setQuantity(5.0); // available
        item1.setUnitPrice(50.0);
        item1.setLineTotal(250.0);

        Sale.SaleItem item2 = new Sale.SaleItem();
        item2.setProductId(p2.getId());
        item2.setProductName(p2.getName());
        item2.setQuantity(5.0); // NOT available!
        item2.setUnitPrice(50.0);
        item2.setLineTotal(250.0);

        sale.setItems(List.of(item1, item2));
        sale.setGrandTotal(500.0);
        sale.setPaymentMethod("CASH");

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sale)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient stock")));

        // Verify p1 was rolled back and is still 10.0!
        Product reloaded1 = productRepository.findById(p1.getId()).orElseThrow();
        assertEquals(10.0, reloaded1.getCurrentStock(), 0.001);

        Product reloaded2 = productRepository.findById(p2.getId()).orElseThrow();
        assertEquals(1.0, reloaded2.getCurrentStock(), 0.001);
    }

    @Test
    @DisplayName("Test 10: Idempotency conflict protection rejects same key reused with different payload")
    void testIdempotencyConflictRejectsDifferentPayload() throws Exception {
        Product p1 = createProduct(storeA.getId(), "Basmati Rice 1kg", 15.0, 2.0, "ACTIVE");
        Product p2 = createProduct(storeA.getId(), "Sunflower Oil 1L", 15.0, 2.0, "ACTIVE");
        String sharedKey = "conflict-key-" + UUID.randomUUID().toString();

        // 1. Initial checkout with Basmati Rice
        Sale sale1 = new Sale();
        sale1.setIdempotencyKey(sharedKey);
        Sale.SaleItem item1 = new Sale.SaleItem();
        item1.setProductId(p1.getId());
        item1.setProductName(p1.getName());
        item1.setQuantity(2.0);
        item1.setUnitPrice(120.0);
        item1.setLineTotal(240.0);
        sale1.setItems(List.of(item1));
        sale1.setGrandTotal(240.0);
        sale1.setPaymentMethod("CASH");

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sale1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertEquals(13.0, productRepository.findById(p1.getId()).orElseThrow().getCurrentStock(), 0.001);

        // 2. Conflicting checkout attempt reusing the same key with Sunflower Oil
        Sale sale2 = new Sale();
        sale2.setIdempotencyKey(sharedKey);
        Sale.SaleItem item2 = new Sale.SaleItem();
        item2.setProductId(p2.getId());
        item2.setProductName(p2.getName());
        item2.setQuantity(3.0);
        item2.setUnitPrice(150.0);
        item2.setLineTotal(450.0);
        sale2.setItems(List.of(item2));
        sale2.setGrandTotal(450.0);
        sale2.setPaymentMethod("UPI");

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sale2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Idempotency conflict")));

        // Verify stock for p2 was untouched!
        assertEquals(15.0, productRepository.findById(p2.getId()).orElseThrow().getCurrentStock(), 0.001);
        // Verify stock for p1 was not further deducted!
        assertEquals(13.0, productRepository.findById(p1.getId()).orElseThrow().getCurrentStock(), 0.001);
    }

    @Test
    @DisplayName("Test 11: Concurrent identical idempotency requests resolve safely without duplicate deduction")
    void testConcurrentIdenticalIdempotencyRequests() throws Exception {
        Product p = createProduct(storeA.getId(), "Apples 1kg", 20.0, 2.0, "ACTIVE");
        String idempotencyKey = "concurrent-key-" + UUID.randomUUID().toString();

        int concurrency = 4;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrency);

        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        List<String> receiptNumbers = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Sale sale = new Sale();
                    sale.setIdempotencyKey(idempotencyKey);
                    Sale.SaleItem item = new Sale.SaleItem();
                    item.setProductId(p.getId());
                    item.setProductName(p.getName());
                    item.setQuantity(2.0);
                    item.setUnitPrice(100.0);
                    item.setLineTotal(200.0);

                    sale.setItems(List.of(item));
                    sale.setGrandTotal(200.0);
                    sale.setPaymentMethod("CASH");

                    MvcResult result = mockMvc.perform(post("/api/v1/sales")
                                    .header("Authorization", "Bearer " + tokenStoreA)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(sale)))
                            .andReturn();

                    statuses.add(result.getResponse().getStatus());
                    if (result.getResponse().getStatus() == 200) {
                        Map map = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
                        Map data = (Map) map.get("data");
                        receiptNumbers.add((String) data.get("receiptNumber"));
                    }
                } catch (Exception e) {
                    statuses.add(500);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        // All successful checkouts must have resolved to status 200 (either initial or idempotent retry)
        for (int s : statuses) {
            assertTrue(s == 200 || s == 409, "Status should be 200 or 409, got: " + s);
        }

        // All successful requests must have the EXACT same receipt number!
        assertFalse(receiptNumbers.isEmpty());
        String expectedReceipt = receiptNumbers.get(0);
        for (String r : receiptNumbers) {
            assertEquals(expectedReceipt, r, "All idempotent responses must point to identical receipt");
        }

        // Product stock must only be deducted ONCE (20.0 - 2.0 = 18.0)
        Product finalProduct = productRepository.findById(p.getId()).orElseThrow();
        assertEquals(18.0, finalProduct.getCurrentStock(), 0.001);
    }

    @Test
    @DisplayName("Test 12: Recovery cleans up stock, customer and transactions on sale save failure")
    void testRecoveryOnSalePersistenceFailure() throws Exception {
        Product p = createProduct(storeA.getId(), "Cashew Nuts 500g", 10.0, 1.0, "ACTIVE");
        Customer c = customerRepository.save(new Customer("Test Loyal Customer", "9900011223", "loyal@test.local", "Bengaluru"));
        c.setStoreId(storeA.getId());
        c.setLoyaltyPoints(50);
        c.setTotalSpent(5000.0);
        c.setOrdersCount(5);
        c = customerRepository.save(c);

        // Spy on saleRepository to throw a persistence runtime exception when saving the sale
        doThrow(new RuntimeException("Simulated Database I/O Failure during sale save"))
                .when(saleRepository).save(any(Sale.class));

        Sale sale = new Sale();
        sale.setCustomerId(c.getId());
        sale.setGrandTotal(500.0);

        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(4.0);
        item.setUnitPrice(125.0);
        item.setLineTotal(500.0);
        sale.setItems(List.of(item));
        sale.setPaymentMethod("CASH");

        try {
            mockMvc.perform(post("/api/v1/sales")
                            .header("Authorization", "Bearer " + tokenStoreA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sale)))
                    .andExpect(status().is5xxServerError())
                    .andExpect(jsonPath("$.message", containsString("Checkout persistence failed")));

            // 1. Verify stock was safely restored to 10.0!
            Product restoredProduct = productRepository.findById(p.getId()).orElseThrow();
            assertEquals(10.0, restoredProduct.getCurrentStock(), 0.001);

            // 2. Verify orphaned inventory transactions were cleaned up!
            List<InventoryTransaction> txs = transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId());
            assertTrue(txs.isEmpty(), "Orphaned inventory transactions should have been deleted by recovery");

            // 3. Verify customer loyalty points, spend, and orders count were reverted!
            Customer restoredCustomer = customerRepository.findById(c.getId()).orElseThrow();
            assertEquals(50, restoredCustomer.getLoyaltyPoints());
            assertEquals(5000.0, restoredCustomer.getTotalSpent(), 0.001);
            assertEquals(5, restoredCustomer.getOrdersCount());
        } finally {
            org.mockito.Mockito.reset(saleRepository);
        }
    }

    @Test
    @DisplayName("Test 14: Recovery cleans up stock when inventory transaction saveAll fails")
    void testRecoveryOnTransactionPersistenceFailure() throws Exception {
        Product p = createProduct(storeA.getId(), "Cardamom 100g", 8.0, 1.0, "ACTIVE");

        doThrow(new RuntimeException("Simulated Transaction I/O Failure"))
                .when(transactionRepository).saveAll(any());

        Sale sale = new Sale();
        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(3.0);
        item.setUnitPrice(200.0);
        item.setLineTotal(600.0);
        sale.setItems(List.of(item));
        sale.setPaymentMethod("CASH");

        try {
            mockMvc.perform(post("/api/v1/sales")
                            .header("Authorization", "Bearer " + tokenStoreA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sale)))
                    .andExpect(status().is5xxServerError())
                    .andExpect(jsonPath("$.message", containsString("Checkout persistence failed")));

            // Stock must be restored to 8.0!
            Product restored = productRepository.findById(p.getId()).orElseThrow();
            assertEquals(8.0, restored.getCurrentStock(), 0.001);
        } finally {
            org.mockito.Mockito.reset(transactionRepository);
        }
    }

    @Test
    @DisplayName("Test 15: Recovery cleans up stock and transactions when customer update fails")
    void testRecoveryOnCustomerUpdateFailure() throws Exception {
        Product p = createProduct(storeA.getId(), "Almonds 500g", 12.0, 2.0, "ACTIVE");
        Customer c = customerRepository.save(new Customer("Customer Fail Test", "9900088776", "fail@test.local", "Bengaluru"));
        c.setStoreId(storeA.getId());
        c = customerRepository.save(c);

        doThrow(new RuntimeException("Simulated Customer Update Failure"))
                .when(customerRepository).save(any(Customer.class));

        Sale sale = new Sale();
        sale.setCustomerId(c.getId());
        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(2.0);
        item.setUnitPrice(300.0);
        item.setLineTotal(600.0);
        sale.setItems(List.of(item));
        sale.setPaymentMethod("UPI");

        try {
            mockMvc.perform(post("/api/v1/sales")
                            .header("Authorization", "Bearer " + tokenStoreA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sale)))
                    .andExpect(status().is5xxServerError())
                    .andExpect(jsonPath("$.message", containsString("Checkout persistence failed")));

            // Stock must be restored to 12.0!
            Product restored = productRepository.findById(p.getId()).orElseThrow();
            assertEquals(12.0, restored.getCurrentStock(), 0.001);

            // Transactions must be cleaned up!
            List<InventoryTransaction> txs = transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId());
            assertTrue(txs.isEmpty(), "Transactions should be deleted on customer update failure");
        } finally {
            org.mockito.Mockito.reset(customerRepository);
        }
    }

    @Test
    @DisplayName("Test 16: Non-idempotent sales without idempotency keys remain fully compatible")
    void testLegacyCheckoutWithoutIdempotencyKeyRemainsCompatible() throws Exception {
        Product p = createProduct(storeA.getId(), "Brown Bread", 12.0, 2.0, "ACTIVE");

        Sale sale = new Sale();
        sale.setIdempotencyKey(null);
        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(2.0);
        item.setUnitPrice(40.0);
        item.setLineTotal(80.0);

        sale.setItems(List.of(item));
        sale.setGrandTotal(80.0);
        sale.setPaymentMethod("CASH");

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sale)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.idempotencyKey").doesNotExist());

        Product updated = productRepository.findById(p.getId()).orElseThrow();
        assertEquals(10.0, updated.getCurrentStock(), 0.001);
    }

    @Test
    @DisplayName("Test 17: Verify sales collection partial unique index definition in MongoTemplate")
    void testVerifySalesCollectionPartialUniqueIndex() {
        var indexOps = mongoTemplate.indexOps(Sale.class);
        var indexInfoList = indexOps.getIndexInfo();

        boolean found = false;
        for (var info : indexInfoList) {
            if ("storeId_idempotencyKey_partial_idx".equals(info.getName())) {
                found = true;
                assertTrue(info.isUnique(), "storeId_idempotencyKey_partial_idx must be unique");
                assertNotNull(info.getPartialFilterExpression(), "storeId_idempotencyKey_partial_idx must have a partial filter expression");
                assertTrue(info.getPartialFilterExpression().contains("idempotencyKey"), "Filter expression must target idempotencyKey");

                boolean hasStoreId = info.getIndexFields().stream().anyMatch(f -> "storeId".equals(f.getKey()));
                boolean hasIdempotencyKey = info.getIndexFields().stream().anyMatch(f -> "idempotencyKey".equals(f.getKey()));
                assertTrue(hasStoreId, "Index must contain storeId field");
                assertTrue(hasIdempotencyKey, "Index must contain idempotencyKey field");
            }
        }
        assertTrue(found, "storeId_idempotencyKey_partial_idx must exist on sales collection");
    }

    @Test
    @DisplayName("Test 18: New shop starts receipt numbers from 1 and increments sequentially")
    void testNewShopStartsReceiptNumberFromOneAndIncrements() throws Exception {
        Product p = createProduct(storeA.getId(), "Basmati Rice", 20.0, 2.0, "ACTIVE");
        int year = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).getYear();

        for (int i = 1; i <= 3; i++) {
            Sale sale = new Sale();
            Sale.SaleItem item = new Sale.SaleItem(p.getId(), p.getName(), p.getSku(), 100.0, 1.0, 5.0);
            sale.setItems(List.of(item));
            sale.setGrandTotal(100.0);
            sale.setPaymentMethod("CASH");

            String expectedReceipt = String.format("RCP-%d-%05d", year, i);
            mockMvc.perform(post("/api/v1/sales")
                            .header("Authorization", "Bearer " + tokenStoreA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sale)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.receiptNumber").value(expectedReceipt));
        }

        // Verify lookup by receipt number is tenant-scoped
        mockMvc.perform(get("/api/v1/sales/receipt/" + String.format("RCP-%d-%05d", year, 1))
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber").value(String.format("RCP-%d-%05d", year, 1)));

        mockMvc.perform(get("/api/v1/sales/receipt/" + String.format("RCP-%d-%05d", year, 1))
                        .header("Authorization", "Bearer " + tokenStoreB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Test 19: Separate new shops each start receipt numbers from 1 without collision")
    void testMultipleNewShopsStartFromOneIndependentlyWithoutCollision() throws Exception {
        Product prodA = createProduct(storeA.getId(), "Milk A", 20.0, 2.0, "ACTIVE");
        Product prodB = createProduct(storeB.getId(), "Milk B", 20.0, 2.0, "ACTIVE");
        int year = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).getYear();

        for (int i = 1; i <= 2; i++) {
            String expectedReceipt = String.format("RCP-%d-%05d", year, i);

            Sale saleA = new Sale();
            saleA.setItems(List.of(new Sale.SaleItem(prodA.getId(), prodA.getName(), prodA.getSku(), 50.0, 1.0, 0.0)));
            saleA.setGrandTotal(50.0);
            saleA.setPaymentMethod("CASH");

            mockMvc.perform(post("/api/v1/sales")
                            .header("Authorization", "Bearer " + tokenStoreA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(saleA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.receiptNumber").value(expectedReceipt));

            Sale saleB = new Sale();
            saleB.setItems(List.of(new Sale.SaleItem(prodB.getId(), prodB.getName(), prodB.getSku(), 50.0, 1.0, 0.0)));
            saleB.setGrandTotal(50.0);
            saleB.setPaymentMethod("UPI");

            mockMvc.perform(post("/api/v1/sales")
                            .header("Authorization", "Bearer " + tokenStoreB)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(saleB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.receiptNumber").value(expectedReceipt));
        }
    }

    @Test
    @DisplayName("Test 20: Concurrent sales in same shop produce unique sequential receipt numbers")
    void testConcurrentSalesProduceUniqueSequentialReceiptNumbers() throws Exception {
        int threads = 10;
        Product p = createProduct(storeA.getId(), "Sugar Pack", 100.0, 5.0, "ACTIVE");
        int year = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).getYear();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        Set<String> receipts = ConcurrentHashMap.newKeySet();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    Sale sale = new Sale();
                    sale.setItems(List.of(new Sale.SaleItem(p.getId(), p.getName(), p.getSku(), 40.0, 1.0, 0.0)));
                    sale.setGrandTotal(40.0);
                    sale.setPaymentMethod("CASH");

                    MvcResult res = mockMvc.perform(post("/api/v1/sales")
                                    .header("Authorization", "Bearer " + tokenStoreA)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(sale)))
                            .andExpect(status().isOk())
                            .andReturn();

                    Map<?, ?> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
                    Map<?, ?> data = (Map<?, ?>) body.get("data");
                    receipts.add((String) data.get("receiptNumber"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(threads, receipts.size(), "Every concurrent sale must get a unique receipt number");
        for (int i = 1; i <= threads; i++) {
            assertTrue(receipts.contains(String.format("RCP-%d-%05d", year, i)),
                    "Missing sequential receipt number " + i + " in " + receipts);
        }
    }

    @Test
    @DisplayName("Test 21: Failed checkout does not consume a receipt number")
    void testFailedCheckoutDoesNotConsumeReceiptNumber() throws Exception {
        Product p = createProduct(storeA.getId(), "Olive Oil", 2.0, 1.0, "ACTIVE");
        int year = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).getYear();

        // Attempt sale exceeding available stock
        Sale failedSale = new Sale();
        failedSale.setItems(List.of(new Sale.SaleItem(p.getId(), p.getName(), p.getSku(), 500.0, 5.0, 5.0)));
        failedSale.setGrandTotal(2500.0);
        failedSale.setPaymentMethod("CASH");

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failedSale)))
                .andExpect(status().isBadRequest());

        // Subsequent valid sale must still start at 1
        Sale validSale = new Sale();
        validSale.setItems(List.of(new Sale.SaleItem(p.getId(), p.getName(), p.getSku(), 500.0, 1.0, 5.0)));
        validSale.setGrandTotal(500.0);
        validSale.setPaymentMethod("CASH");

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validSale)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptNumber").value(String.format("RCP-%d-%05d", year, 1)));
    }
}
