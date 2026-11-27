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
class PurchaseReceivingTest {

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
    private PurchaseOrderRepository purchaseOrderRepository;

    @SpyBean
    private InventoryBatchRepository batchRepository;

    @SpyBean
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Store storeA;
    private Store storeB;
    private String tokenManagerA;
    private String tokenCashierA;
    private String tokenInvStaffA;
    private String tokenManagerB;
    private String usernameManagerA;
    private String suffix;

    @BeforeEach
    void setUp() {
        reset(purchaseOrderRepository, batchRepository, transactionRepository);
        suffix = UUID.randomUUID().toString().substring(0, 8);

        storeA = storeRepository.save(new Store(
                "store-po-a-" + suffix,
                "PO Store A",
                "Owner A",
                "ownerA_" + suffix + "@test.local",
                "9800005001",
                "Bengaluru"
        ));

        usernameManagerA = "po_mgr_a_" + suffix;
        User managerA = userRepository.save(new User(
                usernameManagerA,
                passwordEncoder.encode("password123"),
                "Manager A",
                "mgrA_" + suffix + "@test.local",
                "9800005001",
                UserRole.STORE_MANAGER,
                storeA.getId(),
                storeA.getStoreCode()
        ));
        tokenManagerA = generateToken(managerA);

        User cashierA = userRepository.save(new User(
                "po_cashier_a_" + suffix,
                passwordEncoder.encode("password123"),
                "Cashier A",
                "cashierA_" + suffix + "@test.local",
                "9800005002",
                UserRole.CASHIER,
                storeA.getId(),
                storeA.getStoreCode()
        ));
        tokenCashierA = generateToken(cashierA);

        User invStaffA = userRepository.save(new User(
                "po_inv_a_" + suffix,
                passwordEncoder.encode("password123"),
                "Inv Staff A",
                "invA_" + suffix + "@test.local",
                "9800005003",
                UserRole.INVENTORY_STAFF,
                storeA.getId(),
                storeA.getStoreCode()
        ));
        tokenInvStaffA = generateToken(invStaffA);

        storeB = storeRepository.save(new Store(
                "store-po-b-" + suffix,
                "PO Store B",
                "Owner B",
                "ownerB_" + suffix + "@test.local",
                "9800006001",
                "Bengaluru"
        ));

        User managerB = userRepository.save(new User(
                "po_mgr_b_" + suffix,
                passwordEncoder.encode("password123"),
                "Manager B",
                "mgrB_" + suffix + "@test.local",
                "9800006001",
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
                "Wheat Flour " + skuSuffix,
                "PO-SKU-" + suffix + "-" + skuSuffix,
                null,
                null,
                "Grains",
                "KG",
                35.0,
                50.0,
                currentStock,
                5.0
        );
        p.setStoreId(storeId);
        p.setStatus(status);
        return productRepository.save(p);
    }

    private Supplier createSupplier(String storeId, String codeSuffix, String name) {
        Supplier s = new Supplier(
                "POSUP-" + suffix + "-" + codeSuffix,
                name,
                "Contact",
                "9822222222",
                "posup_" + codeSuffix + "@test.local",
                "29ABCDE1234F1Z5",
                "Bengaluru",
                "Net 30"
        );
        s.setStoreId(storeId);
        return supplierRepository.save(s);
    }

    private PurchaseOrder createAndOrderPo(String storeId, Supplier sup, List<PurchaseOrder.PurchaseOrderItem> items, String status) {
        PurchaseOrder po = new PurchaseOrder(
                "PO-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6),
                sup != null ? sup.getId() : null,
                sup != null ? sup.getName() : null,
                LocalDate.now().plusDays(2),
                items,
                "Test PO"
        );
        po.setStoreId(storeId);
        po.setStatus(status);
        return purchaseOrderRepository.save(po);
    }

    @Test
    @DisplayName("Valid receiving updates stock, creates batches and ledger entries, and marks PO RECEIVED")
    void validReceiving_updatesStockCreatesBatchAndLedgerAndMarksReceived() throws Exception {
        Product p1 = createProduct(storeA.getId(), "P1", 0.0, "ACTIVE");
        Product p2 = createProduct(storeA.getId(), "P2", 10.0, "ACTIVE");
        Supplier sup = createSupplier(storeA.getId(), "S1", "Grain Mills Co");

        PurchaseOrder po = createAndOrderPo(storeA.getId(), sup, List.of(
                new PurchaseOrder.PurchaseOrderItem(p1.getId(), p1.getName(), 25.0, 32.0),
                new PurchaseOrder.PurchaseOrderItem(p2.getId(), p2.getName(), 15.0, 40.0)
        ), "ORDERED");

        LocalDate expiry = LocalDate.now().plusDays(90);
        mockMvc.perform(post("/api/v1/purchases/" + po.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("expiryDate", expiry.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.receivedBy").value(usernameManagerA));

        assertEquals(25.0, productRepository.findByStoreIdAndId(storeA.getId(), p1.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertEquals(25.0, productRepository.findByStoreIdAndId(storeA.getId(), p2.getId()).orElseThrow().getCurrentStock(), 0.001);

        List<InventoryBatch> b1 = batchRepository.findByProductId(p1.getId());
        assertEquals(1, b1.size());
        assertEquals(25.0, b1.get(0).getInitialQty(), 0.001);
        assertEquals(25.0, b1.get(0).getCurrentQty(), 0.001);
        assertEquals(32.0, b1.get(0).getPurchaseCost(), 0.001);
        assertEquals(expiry, b1.get(0).getExpiryDate());
        assertEquals("Grain Mills Co", b1.get(0).getSupplierName());

        List<InventoryTransaction> txs1 = transactionRepository.findByProductIdOrderByCreatedAtDesc(p1.getId());
        assertEquals(1, txs1.size());
        assertEquals("PURCHASE_RECEIPT", txs1.get(0).getType());
        assertEquals(25.0, txs1.get(0).getQuantity(), 0.001);
        assertEquals(25.0, txs1.get(0).getBalanceAfter(), 0.001);
        assertEquals("PURCHASE_ORDER", txs1.get(0).getReferenceType());
        assertEquals(po.getOrderNumber(), txs1.get(0).getReferenceId());
        assertEquals(usernameManagerA, txs1.get(0).getPerformedBy());

        // Repeated receive without idempotency key must be rejected
        mockMvc.perform(post("/api/v1/purchases/" + po.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenManagerA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already received")));
        assertEquals(25.0, productRepository.findByStoreIdAndId(storeA.getId(), p1.getId()).orElseThrow().getCurrentStock(), 0.001);
    }

    @Test
    @DisplayName("Validation, security, and tenant isolation checks for PO creation and receiving")
    void validationSecurityAndTenantIsolation() throws Exception {
        Product pA = createProduct(storeA.getId(), "VA", 10.0, "ACTIVE");
        Product archivedA = createProduct(storeA.getId(), "ARCH", 0.0, "ARCHIVED");
        Product pB = createProduct(storeB.getId(), "VB", 10.0, "ACTIVE");
        Supplier supA = createSupplier(storeA.getId(), "SA", "Sup A");
        Supplier supB = createSupplier(storeB.getId(), "SB", "Sup B");

        // PO creation rejects zero qty, negative qty, negative cost, archived product, cross-store supplier
        mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"" + supA.getId() + "\",\"items\":[{\"productId\":\"" + pA.getId() + "\",\"orderQty\":0,\"unitCost\":10}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"" + supA.getId() + "\",\"items\":[{\"productId\":\"" + pA.getId() + "\",\"orderQty\":-5,\"unitCost\":10}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"" + supA.getId() + "\",\"items\":[{\"productId\":\"" + pA.getId() + "\",\"orderQty\":5,\"unitCost\":-1}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"" + supA.getId() + "\",\"items\":[{\"productId\":\"" + archivedA.getId() + "\",\"orderQty\":5,\"unitCost\":10}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"" + supB.getId() + "\",\"items\":[{\"productId\":\"" + pA.getId() + "\",\"orderQty\":5,\"unitCost\":10}]}"))
                .andExpect(status().isBadRequest());

        // DRAFT and CANCELLED POs cannot be received
        PurchaseOrder draftPo = createAndOrderPo(storeA.getId(), supA, List.of(
                new PurchaseOrder.PurchaseOrderItem(pA.getId(), pA.getName(), 5.0, 20.0)), "DRAFT");
        PurchaseOrder cancelledPo = createAndOrderPo(storeA.getId(), supA, List.of(
                new PurchaseOrder.PurchaseOrderItem(pA.getId(), pA.getName(), 5.0, 20.0)), "CANCELLED");

        mockMvc.perform(post("/api/v1/purchases/" + draftPo.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenManagerA))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/purchases/" + cancelledPo.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenManagerA))
                .andExpect(status().isBadRequest());

        // Invalid expiryDate
        PurchaseOrder orderedPo = createAndOrderPo(storeA.getId(), supA, List.of(
                new PurchaseOrder.PurchaseOrderItem(pA.getId(), pA.getName(), 5.0, 20.0)), "ORDERED");
        mockMvc.perform(post("/api/v1/purchases/" + orderedPo.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiryDate\":\"not-a-date\"}"))
                .andExpect(status().isBadRequest());

        // Non-existent PO & cross-store PO -> 404
        mockMvc.perform(post("/api/v1/purchases/non-existent-po/receive")
                        .header("Authorization", "Bearer " + tokenManagerA))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/purchases/" + orderedPo.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenManagerB))
                .andExpect(status().isNotFound());

        // Unauthenticated -> 401; CASHIER / INVENTORY_STAFF -> 403
        mockMvc.perform(post("/api/v1/purchases/" + orderedPo.getId() + "/receive"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/purchases/" + orderedPo.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenCashierA))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/purchases/" + orderedPo.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenInvStaffA))
                .andExpect(status().isForbidden());

        // Product archived after PO ordered cannot be received
        archivedA.setStatus("ACTIVE");
        productRepository.save(archivedA);
        PurchaseOrder poWithLaterArchived = createAndOrderPo(storeA.getId(), supA, List.of(
                new PurchaseOrder.PurchaseOrderItem(archivedA.getId(), archivedA.getName(), 5.0, 20.0)), "ORDERED");
        archivedA.setStatus("ARCHIVED");
        productRepository.save(archivedA);

        mockMvc.perform(post("/api/v1/purchases/" + poWithLaterArchived.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenManagerA))
                .andExpect(status().isBadRequest());

        // Verify untouched stock
        assertEquals(10.0, productRepository.findByStoreIdAndId(storeA.getId(), pA.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertEquals(10.0, productRepository.findByStoreIdAndId(storeB.getId(), pB.getId()).orElseThrow().getCurrentStock(), 0.001);
    }

    @Test
    @DisplayName("Idempotency: sequential retry returns 200 without duplicating stock; mismatch returns 409; concurrent duplicate claims once")
    void idempotentAndConcurrentReceiving() throws Exception {
        Product p = createProduct(storeA.getId(), "IDEM", 0.0, "ACTIVE");
        Supplier sup = createSupplier(storeA.getId(), "SI", "Idem Sup");
        PurchaseOrder po = createAndOrderPo(storeA.getId(), sup, List.of(
                new PurchaseOrder.PurchaseOrderItem(p.getId(), p.getName(), 30.0, 25.0)), "ORDERED");

        String idemKey = "po-rcv-key-" + suffix;
        String payload = objectMapper.writeValueAsString(Map.of("idempotencyKey", idemKey));

        // Concurrent duplicate receive on same PO
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/v1/purchases/" + po.getId() + "/receive")
                                .header("Authorization", "Bearer " + tokenManagerA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andReturn().getResponse().getStatus();
            }));
        }

        startLatch.countDown();
        int okCount = 0;
        for (Future<Integer> f : futures) {
            int status = f.get(15, TimeUnit.SECONDS);
            assertTrue(status == 200 || status == 409, "Expected 200 or 409, got " + status);
            if (status == 200) okCount++;
        }
        executor.shutdown();

        assertTrue(okCount >= 1, "At least one request must succeed");
        assertEquals(30.0, productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertEquals(1, batchRepository.findByProductId(p.getId()).size());
        assertEquals(1, transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId()).size());

        // Sequential idempotent retry -> 200 OK, no extra stock
        mockMvc.perform(post("/api/v1/purchases/" + po.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("idempotent")));
        assertEquals(30.0, productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow().getCurrentStock(), 0.001);

        // Same key with different expiryDate -> 409 Conflict
        mockMvc.perform(post("/api/v1/purchases/" + po.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenManagerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "idempotencyKey", idemKey,
                                "expiryDate", LocalDate.now().plusDays(10).toString()))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Concurrent receiving of two separate POs for the same product accumulates stock atomically")
    void concurrentReceivingDifferentPos_accumulatesStockAtomically() throws Exception {
        Product p = createProduct(storeA.getId(), "CONC_PO", 5.0, "ACTIVE");
        Supplier sup = createSupplier(storeA.getId(), "SC", "Conc Sup");
        PurchaseOrder po1 = createAndOrderPo(storeA.getId(), sup, List.of(
                new PurchaseOrder.PurchaseOrderItem(p.getId(), p.getName(), 12.0, 20.0)), "ORDERED");
        PurchaseOrder po2 = createAndOrderPo(storeA.getId(), sup, List.of(
                new PurchaseOrder.PurchaseOrderItem(p.getId(), p.getName(), 18.0, 20.0)), "ORDERED");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        Future<Integer> f1 = executor.submit(() -> {
            startLatch.await();
            return mockMvc.perform(post("/api/v1/purchases/" + po1.getId() + "/receive")
                            .header("Authorization", "Bearer " + tokenManagerA))
                    .andReturn().getResponse().getStatus();
        });
        Future<Integer> f2 = executor.submit(() -> {
            startLatch.await();
            return mockMvc.perform(post("/api/v1/purchases/" + po2.getId() + "/receive")
                            .header("Authorization", "Bearer " + tokenManagerA))
                    .andReturn().getResponse().getStatus();
        });

        startLatch.countDown();
        assertEquals(200, f1.get(15, TimeUnit.SECONDS));
        assertEquals(200, f2.get(15, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(35.0, productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertEquals(2, batchRepository.findByProductId(p.getId()).size());
        assertEquals(2, transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId()).size());
    }

    @Test
    @DisplayName("Failure recovery: batch, ledger, and PO persistence failures roll back cleanly; compensation failure reported")
    void failureRecoveryAndCompensation() throws Exception {
        Product p = createProduct(storeA.getId(), "FAIL", 10.0, "ACTIVE");
        Supplier sup = createSupplier(storeA.getId(), "SF", "Fail Sup");
        PurchaseOrder po = createAndOrderPo(storeA.getId(), sup, List.of(
                new PurchaseOrder.PurchaseOrderItem(p.getId(), p.getName(), 20.0, 30.0)), "ORDERED");

        // 1. Batch save failure -> stock rolled back, PO reverted to ORDERED
        doThrow(new RuntimeException("Simulated batch write failure"))
                .when(batchRepository).save(any(InventoryBatch.class));
        mockMvc.perform(post("/api/v1/purchases/" + po.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenManagerA))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", containsString("rolled back")));
        assertEquals(10.0, productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertEquals("ORDERED", purchaseOrderRepository.findByStoreIdAndId(storeA.getId(), po.getId()).orElseThrow().getStatus());
        assertTrue(batchRepository.findByProductId(p.getId()).isEmpty());
        assertTrue(transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId()).isEmpty());

        // 2. Ledger save failure -> stock rolled back, created batch deleted, PO reverted to ORDERED
        reset(batchRepository, transactionRepository, purchaseOrderRepository);
        doThrow(new RuntimeException("Simulated ledger write failure"))
                .when(transactionRepository).save(any(InventoryTransaction.class));
        mockMvc.perform(post("/api/v1/purchases/" + po.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenManagerA))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", containsString("rolled back")));
        assertEquals(10.0, productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertEquals("ORDERED", purchaseOrderRepository.findByStoreIdAndId(storeA.getId(), po.getId()).orElseThrow().getStatus());
        assertTrue(batchRepository.findByProductId(p.getId()).isEmpty());
        assertTrue(transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId()).isEmpty());

        // 3. PO final save failure -> stock rolled back, batch & ledger deleted, PO reverted to ORDERED
        reset(batchRepository, transactionRepository, purchaseOrderRepository);
        doThrow(new RuntimeException("Simulated PO save failure"))
                .when(purchaseOrderRepository).save(any(PurchaseOrder.class));
        mockMvc.perform(post("/api/v1/purchases/" + po.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenManagerA))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", containsString("rolled back")));
        assertEquals(10.0, productRepository.findByStoreIdAndId(storeA.getId(), p.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertEquals("ORDERED", purchaseOrderRepository.findByStoreIdAndId(storeA.getId(), po.getId()).orElseThrow().getStatus());
        assertTrue(batchRepository.findByProductId(p.getId()).isEmpty());
        assertTrue(transactionRepository.findByProductIdOrderByCreatedAtDesc(p.getId()).isEmpty());

        // 4. Compensation failure -> product deleted before rollback runs -> reports manual reconciliation required
        reset(batchRepository, transactionRepository, purchaseOrderRepository);
        doAnswer(invocation -> {
            productRepository.deleteById(p.getId());
            throw new RuntimeException("Simulated batch write failure");
        }).when(batchRepository).save(any(InventoryBatch.class));
        mockMvc.perform(post("/api/v1/purchases/" + po.getId() + "/receive")
                        .header("Authorization", "Bearer " + tokenManagerA))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", containsString("manual reconciliation required")));
    }
}
