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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

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
    private SaleRepository saleRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CategoryRepository categoryRepository;

    private Store storeA;
    private Store storeB;
    private String tokenStoreA;
    private String tokenStoreB;
    private String cashierTokenA;
    private String managerTokenA;
    private String invStaffTokenA;

    @BeforeEach
    void setUp() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);

        // Create Store A
        storeA = storeRepository.save(new Store(
                "store-a-" + testSuffix,
                "Store A",
                "Owner A",
                "ownerA_" + testSuffix + "@test.local",
                "9800000001",
                "Bengaluru"
        ));

        User userA = userRepository.save(new User(
                "user_a_" + testSuffix,
                passwordEncoder.encode("password123"),
                "User A",
                "userA_" + testSuffix + "@test.local",
                "9800000001",
                UserRole.ADMIN,
                storeA.getId(),
                storeA.getStoreCode()
        ));

        UserPrincipal principalA = new UserPrincipal(userA);
        UsernamePasswordAuthenticationToken authA =
                new UsernamePasswordAuthenticationToken(principalA, null, principalA.getAuthorities());
        tokenStoreA = jwtTokenProvider.generateToken(authA);

        // Manager in Store A
        User managerA = userRepository.save(new User(
                "mgr_a_" + testSuffix,
                passwordEncoder.encode("password123"),
                "Manager A",
                "mgrA_" + testSuffix + "@test.local",
                "9800000011",
                UserRole.STORE_MANAGER,
                storeA.getId(),
                storeA.getStoreCode()
        ));
        UserPrincipal principalMgrA = new UserPrincipal(managerA);
        managerTokenA = jwtTokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(principalMgrA, null, principalMgrA.getAuthorities()));

        // Inventory Staff in Store A
        User invStaffA = userRepository.save(new User(
                "inv_a_" + testSuffix,
                passwordEncoder.encode("password123"),
                "Inv Staff A",
                "invA_" + testSuffix + "@test.local",
                "9800000012",
                UserRole.INVENTORY_STAFF,
                storeA.getId(),
                storeA.getStoreCode()
        ));
        UserPrincipal principalInvA = new UserPrincipal(invStaffA);
        invStaffTokenA = jwtTokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(principalInvA, null, principalInvA.getAuthorities()));

        // Cashier in Store A
        User cashierA = userRepository.save(new User(
                "cashier_a_" + testSuffix,
                passwordEncoder.encode("password123"),
                "Cashier A",
                "cashierA_" + testSuffix + "@test.local",
                "9800000013",
                UserRole.CASHIER,
                storeA.getId(),
                storeA.getStoreCode()
        ));
        UserPrincipal principalCashierA = new UserPrincipal(cashierA);
        cashierTokenA = jwtTokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(principalCashierA, null, principalCashierA.getAuthorities()));

        // Create Store B
        storeB = storeRepository.save(new Store(
                "store-b-" + testSuffix,
                "Store B",
                "Owner B",
                "ownerB_" + testSuffix + "@test.local",
                "9800000002",
                "Mumbai"
        ));

        User userB = userRepository.save(new User(
                "user_b_" + testSuffix,
                passwordEncoder.encode("password123"),
                "User B",
                "userB_" + testSuffix + "@test.local",
                "9800000002",
                UserRole.ADMIN,
                storeB.getId(),
                storeB.getStoreCode()
        ));

        UserPrincipal principalB = new UserPrincipal(userB);
        UsernamePasswordAuthenticationToken authB =
                new UsernamePasswordAuthenticationToken(principalB, null, principalB.getAuthorities());
        tokenStoreB = jwtTokenProvider.generateToken(authB);
    }

    @Test
    @DisplayName("Should reject unauthenticated requests with 401")
    void shouldRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());

        Product p = new Product();
        p.setName("Unauthorized Item");
        p.setSku("UNAUTH-01");
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should strictly isolate product reads between Store A and Store B")
    void shouldIsolateProductsBetweenStoresOnRead() throws Exception {
        // 1. Store A creates Product A
        Product productA = new Product();
        productA.setName("Exclusive Store A Milk");
        productA.setSku("SKU-STORE-A-" + UUID.randomUUID().toString().substring(0, 6));
        productA.setCostPrice(20.0);
        productA.setSellingPrice(25.0);
        productA.setUnit("PACK");

        MvcResult resultA = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.storeId").value(storeA.getId()))
                .andReturn();

        String idA = objectMapper.readTree(resultA.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 2. Store A lists products -> Product A MUST be present
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(idA)));

        // 3. Store B lists products -> Product A MUST NOT be present
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(idA))));

        // 4. Store B attempts direct read by ID -> Must return 404 Not Found
        mockMvc.perform(get("/api/v1/products/" + idA)
                        .header("Authorization", "Bearer " + tokenStoreB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should block Store B from modifying Store A's product")
    void shouldBlockStoreBFromModifyingStoreAProduct() throws Exception {
        // 1. Store A creates product
        Product productA = new Product();
        productA.setName("Store A Original Item");
        productA.setSku("SKU-ORIG-" + UUID.randomUUID().toString().substring(0, 6));
        productA.setCostPrice(50.0);
        productA.setSellingPrice(75.0);
        productA.setUnit("PCS");

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productA)))
                .andExpect(status().isOk())
                .andReturn();

        String idA = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 2. Store B attempts to update Product A -> Must return 404 Not Found
        Product maliciousUpdate = new Product();
        maliciousUpdate.setName("Tampered Item Name");
        maliciousUpdate.setSku(productA.getSku());
        maliciousUpdate.setCostPrice(1.0);
        maliciousUpdate.setSellingPrice(2.0);

        mockMvc.perform(put("/api/v1/products/" + idA)
                        .header("Authorization", "Bearer " + tokenStoreB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousUpdate)))
                .andExpect(status().isNotFound());

        // 3. Verify Product A remains unchanged in Store A
        mockMvc.perform(get("/api/v1/products/" + idA)
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Store A Original Item"))
                .andExpect(jsonPath("$.sellingPrice").value(75.0));
    }

    @Test
    @DisplayName("Should block Store B from deleting Store A's product")
    void shouldBlockStoreBFromDeletingStoreAProduct() throws Exception {
        // 1. Store A creates product
        Product productA = new Product();
        productA.setName("Store A Protected Item");
        productA.setSku("SKU-PROT-" + UUID.randomUUID().toString().substring(0, 6));
        productA.setCostPrice(10.0);
        productA.setSellingPrice(15.0);
        productA.setUnit("PACK");

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productA)))
                .andExpect(status().isOk())
                .andReturn();

        String idA = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 2. Store B attempts to delete Product A -> Must return 404 Not Found
        mockMvc.perform(delete("/api/v1/products/" + idA)
                        .header("Authorization", "Bearer " + tokenStoreB))
                .andExpect(status().isNotFound());

        // 3. Verify Product A still exists in Store A
        mockMvc.perform(get("/api/v1/products/" + idA)
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idA));
    }

    @Test
    @DisplayName("Should permit same SKU in different stores, but reject duplicate within same store")
    void shouldAllowSameSkuInDifferentStoresButRejectWithinSameStore() throws Exception {
        String sharedSku = "SKU-SHARED-" + UUID.randomUUID().toString().substring(0, 6);

        // 1. Store A creates product with sharedSku -> 200 OK
        Product prodA1 = new Product();
        prodA1.setName("Store A Item");
        prodA1.setSku(sharedSku);
        prodA1.setCostPrice(10.0);
        prodA1.setSellingPrice(15.0);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodA1)))
                .andExpect(status().isOk());

        // 2. Store A attempts duplicate with same sharedSku -> 400 Bad Request
        Product prodA2 = new Product();
        prodA2.setName("Store A Duplicate Item");
        prodA2.setSku(sharedSku);
        prodA2.setCostPrice(12.0);
        prodA2.setSellingPrice(18.0);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodA2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("SKU already exists")));

        // 3. Store B creates product with SAME sharedSku -> MUST SUCCEED (200 OK)
        Product prodB = new Product();
        prodB.setName("Store B Item with Same SKU");
        prodB.setSku(sharedSku);
        prodB.setCostPrice(14.0);
        prodB.setSellingPrice(20.0);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeId").value(storeB.getId()))
                .andExpect(jsonPath("$.data.sku").value(sharedSku.toUpperCase()));
    }

    @Test
    @DisplayName("Should reject invalid negative prices with 400")
    void shouldRejectInvalidNegativePrices() throws Exception {
        Product invalidProd = new Product();
        invalidProd.setName("Negative Price Item");
        invalidProd.setSku("SKU-NEG-" + UUID.randomUUID().toString().substring(0, 6));
        invalidProd.setCostPrice(-10.0);
        invalidProd.setSellingPrice(20.0);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidProd)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        invalidProd.setCostPrice(10.0);
        invalidProd.setSellingPrice(-5.0);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidProd)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // =========================================================================
    // AUD-04-CRIT-01: Product ID Ownership Protection Tests
    // =========================================================================

    @Test
    @DisplayName("AUD-04-CRIT-01: Should ignore client-supplied ID and generate server-authoritative ID on creation")
    void shouldGenerateServerIdAndIgnoreClientSuppliedIdOnCreation() throws Exception {
        String fabricatedId = "malicious-client-id-" + UUID.randomUUID();
        Product product = new Product();
        product.setId(fabricatedId);
        product.setName("Fabricated ID Item");
        product.setSku("SKU-FAB-" + UUID.randomUUID().toString().substring(0, 6));
        product.setCostPrice(30.0);
        product.setSellingPrice(45.0);

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.id", not(equalTo(fabricatedId))))
                .andExpect(jsonPath("$.data.storeId").value(storeA.getId()))
                .andReturn();

        String generatedId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Verify in MongoDB: document exists under server ID, fabricated ID does not exist
        assertTrue(productRepository.findById(generatedId).isPresent());
        assertFalse(productRepository.findById(fabricatedId).isPresent());
    }

    @Test
    @DisplayName("AUD-04-CRIT-01: Should prevent cross-tenant document overwrite when Store B supplies Store A product ID")
    void shouldPreventCrossTenantDocumentOverwriteViaClientSuppliedId() throws Exception {
        // 1. Store A creates Product A
        Product originalProdA = new Product();
        originalProdA.setName("Original Store A Golden Product");
        originalProdA.setSku("SKU-ORIG-A-" + UUID.randomUUID().toString().substring(0, 6));
        originalProdA.setCostPrice(100.0);
        originalProdA.setSellingPrice(150.0);

        MvcResult resA = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(originalProdA)))
                .andExpect(status().isOk())
                .andReturn();

        String idA = objectMapper.readTree(resA.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 2. Store B maliciously attempts to create a product supplying idA
        Product hijackingProd = new Product();
        hijackingProd.setId(idA); // Attempt to overwrite Store A's document
        hijackingProd.setName("Hijacked Store B Name");
        hijackingProd.setSku("SKU-HIJACK-" + UUID.randomUUID().toString().substring(0, 6));
        hijackingProd.setCostPrice(1.0);
        hijackingProd.setSellingPrice(2.0);

        MvcResult resB = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hijackingProd)))
                .andExpect(status().isOk())
                .andReturn();

        String idB = objectMapper.readTree(resB.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 3. Assertions:
        // - Store B was assigned a NEW ID, distinct from idA
        assertNotEquals(idA, idB);

        // - Store A's product remains 100% unchanged in Store A
        Product preservedA = productRepository.findByStoreIdAndId(storeA.getId(), idA).orElseThrow();
        assertEquals("Original Store A Golden Product", preservedA.getName());
        assertEquals(storeA.getId(), preservedA.getStoreId());
        assertEquals(150.0, preservedA.getSellingPrice());

        // - Store B's product is in Store B
        Product createdB = productRepository.findByStoreIdAndId(storeB.getId(), idB).orElseThrow();
        assertEquals("Hijacked Store B Name", createdB.getName());
        assertEquals(storeB.getId(), createdB.getStoreId());
    }

    @Test
    @DisplayName("AUD-04-CRIT-01: Should enforce authenticated store ownership over client-supplied storeId")
    void shouldEnforceAuthenticatedStoreOwnershipOverClientSuppliedStoreId() throws Exception {
        Product prod = new Product();
        prod.setStoreId(storeB.getId()); // Maliciously claim Store B
        prod.setName("Spoofed Store Item");
        prod.setSku("SKU-SPOOF-" + UUID.randomUUID().toString().substring(0, 6));
        prod.setCostPrice(10.0);
        prod.setSellingPrice(20.0);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeId").value(storeA.getId()));
    }

    // =========================================================================
    // AUD-04-CRIT-02: Safe Product Deletion & Archival Tests
    // =========================================================================

    @Test
    @DisplayName("AUD-04-CRIT-02: Should physically delete product if it has NO historical references")
    void shouldSafelyPhysicallyDeleteUnreferencedProduct() throws Exception {
        Product prod = new Product();
        prod.setName("Ephemeral Item");
        prod.setSku("SKU-EPH-" + UUID.randomUUID().toString().substring(0, 6));
        prod.setCostPrice(10.0);
        prod.setSellingPrice(15.0);

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Delete unreferenced product
        mockMvc.perform(delete("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("deleted successfully")));

        // Verify document is physically deleted
        assertTrue(productRepository.findByStoreIdAndId(storeA.getId(), id).isEmpty());
        mockMvc.perform(get("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AUD-04-CRIT-02: Should archive product instead of physically deleting if historical sales exist")
    void shouldArchiveProductWithHistoricalSaleReferencesInsteadOfDeleting() throws Exception {
        // 1. Create Product
        Product prod = new Product();
        prod.setName("Historically Sold Item");
        prod.setSku("SKU-SOLD-" + UUID.randomUUID().toString().substring(0, 6));
        prod.setCostPrice(20.0);
        prod.setSellingPrice(30.0);

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 2. Create historical Sale referencing this product
        Sale sale = new Sale();
        sale.setStoreId(storeA.getId());
        sale.setReceiptNumber("REC-" + UUID.randomUUID().toString().substring(0, 8));
        sale.setSubtotal(30.0);
        sale.setGrandTotal(30.0);
        sale.setStatus("COMPLETED");
        Sale.SaleItem saleItem = new Sale.SaleItem(id, prod.getName(), prod.getSku(), 30.0, 1.0, 5.0);
        sale.setItems(List.of(saleItem));
        saleRepository.save(sale);

        // 3. Attempt to delete product
        mockMvc.perform(delete("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("archived")));

        // 4. Verify product STILL exists in MongoDB with status ARCHIVED
        Product archived = productRepository.findByStoreIdAndId(storeA.getId(), id).orElseThrow();
        assertEquals("ARCHIVED", archived.getStatus());

        // 5. Verify product is still readable via GET /{id} for historical workflows
        mockMvc.perform(get("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("AUD-04-CRIT-02: Should archive product with historical PurchaseOrder references")
    void shouldArchiveProductWithHistoricalPurchaseReferences() throws Exception {
        Product prod = new Product();
        prod.setName("Historically Purchased Item");
        prod.setSku("SKU-PURCH-" + UUID.randomUUID().toString().substring(0, 6));
        prod.setCostPrice(50.0);
        prod.setSellingPrice(70.0);

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Create PO referencing this product
        PurchaseOrder po = new PurchaseOrder();
        po.setStoreId(storeA.getId());
        po.setOrderNumber("PO-TEST-" + UUID.randomUUID().toString().substring(0, 6));
        po.setStatus("RECEIVED");
        PurchaseOrder.PurchaseOrderItem poItem = new PurchaseOrder.PurchaseOrderItem(id, prod.getName(), 10.0, 50.0);
        po.setItems(List.of(poItem));
        purchaseOrderRepository.save(po);

        // Delete product -> must archive
        mockMvc.perform(delete("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("archived")));

        Product archived = productRepository.findByStoreIdAndId(storeA.getId(), id).orElseThrow();
        assertEquals("ARCHIVED", archived.getStatus());
    }

    @Test
    @DisplayName("AUD-04-CRIT-02: Should archive product with historical InventoryBatch references")
    void shouldArchiveProductWithHistoricalInventoryBatchReferences() throws Exception {
        Product prod = new Product();
        prod.setName("Batched Item");
        prod.setSku("SKU-BATCH-" + UUID.randomUUID().toString().substring(0, 6));
        prod.setCostPrice(40.0);
        prod.setSellingPrice(60.0);

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Create Batch referencing product
        InventoryBatch batch = new InventoryBatch(id, prod.getName(), "BATCH-01", "SUPP-01", "Supplier 1",
                LocalDate.now(), LocalDate.now().plusMonths(6), 50.0, 50.0, 40.0);
        batch.setStoreId(storeA.getId());
        inventoryBatchRepository.save(batch);

        // Delete product -> must archive
        mockMvc.perform(delete("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("archived")));

        Product archived = productRepository.findByStoreIdAndId(storeA.getId(), id).orElseThrow();
        assertEquals("ARCHIVED", archived.getStatus());
    }

    @Test
    @DisplayName("AUD-04-CRIT-02: Should archive product with historical InventoryTransaction references")
    void shouldArchiveProductWithHistoricalInventoryTransactionReferences() throws Exception {
        Product prod = new Product();
        prod.setName("Transacted Item");
        prod.setSku("SKU-TX-" + UUID.randomUUID().toString().substring(0, 6));
        prod.setCostPrice(15.0);
        prod.setSellingPrice(25.0);

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Create Inventory Transaction
        InventoryTransaction tx = new InventoryTransaction(
                id, prod.getName(), "ADJUSTMENT", 5.0, 15.0, "ADJUSTMENT", "REF-01", "admin", "audit stock");
        tx.setStoreId(storeA.getId());
        inventoryTransactionRepository.save(tx);

        // Delete product -> must archive
        mockMvc.perform(delete("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("archived")));

        Product archived = productRepository.findByStoreIdAndId(storeA.getId(), id).orElseThrow();
        assertEquals("ARCHIVED", archived.getStatus());
    }

    @Test
    @DisplayName("AUD-04-CRIT-02: Should be safe and idempotent on repeated deletion of an archived product")
    void shouldBeSafeAndIdempotentOnRepeatedDeletionOfArchivedProduct() throws Exception {
        Product prod = new Product();
        prod.setName("Idempotent Archive Item");
        prod.setSku("SKU-IDEM-" + UUID.randomUUID().toString().substring(0, 6));
        prod.setCostPrice(10.0);
        prod.setSellingPrice(20.0);

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Attach transaction
        InventoryTransaction tx = new InventoryTransaction(
                id, prod.getName(), "ADJUSTMENT", 1.0, 1.0, "ADJUSTMENT", "REF-02", "admin", "test");
        tx.setStoreId(storeA.getId());
        inventoryTransactionRepository.save(tx);

        // 1st Delete -> archives
        mockMvc.perform(delete("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("archived")));

        // 2nd Delete -> idempotent success
        mockMvc.perform(delete("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("already archived")));

        // Product remains ARCHIVED
        assertEquals("ARCHIVED", productRepository.findByStoreIdAndId(storeA.getId(), id).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("AUD-04-CRIT-02: Should strictly reject sale of an archived product")
    void shouldStrictlyRejectSaleOfArchivedProduct() throws Exception {
        Product prod = new Product();
        prod.setName("Archived No-Sale Item");
        prod.setSku("SKU-NOSALE-" + UUID.randomUUID().toString().substring(0, 6));
        prod.setCostPrice(10.0);
        prod.setSellingPrice(20.0);

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Manually archive product
        mockMvc.perform(patch("/api/v1/products/" + id + "/status")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ARCHIVED"))))
                .andExpect(status().isOk());

        // Attempt Sale with archived product
        Sale sale = new Sale();
        sale.setSubtotal(20.0);
        sale.setGrandTotal(20.0);
        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(id);
        item.setQuantity(1.0);
        item.setUnitPrice(20.0);
        sale.setItems(List.of(item));

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sale)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("cannot be sold")));
    }

    // =========================================================================
    // AUD-04-HIGH-02: Product Status Endpoint RBAC & Validation Tests
    // =========================================================================

    @Test
    @DisplayName("AUD-04-HIGH-02: Should reject unauthenticated status update with 401")
    void shouldRejectUnauthenticatedStatusUpdate() throws Exception {
        mockMvc.perform(patch("/api/v1/products/any-id/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AUD-04-HIGH-02: Should block CASHIER role from updating product status with 403 Forbidden")
    void shouldBlockCashierRoleFromUpdatingProductStatus() throws Exception {
        Product prod = new Product();
        prod.setName("Protected Status Item");
        prod.setSku("SKU-PROT-ST-" + UUID.randomUUID().toString().substring(0, 6));
        prod.setCostPrice(10.0);
        prod.setSellingPrice(20.0);

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Cashier attempts status patch -> 403 Forbidden
        mockMvc.perform(patch("/api/v1/products/" + id + "/status")
                        .header("Authorization", "Bearer " + cashierTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isForbidden());

        // Verify status remains ACTIVE
        assertEquals("ACTIVE", productRepository.findByStoreIdAndId(storeA.getId(), id).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("AUD-04-HIGH-02: Should permit ADMIN, STORE_MANAGER, and INVENTORY_STAFF to update product status")
    void shouldPermitPrivilegedRolesToUpdateProductStatus() throws Exception {
        Product prod = new Product();
        prod.setName("Multi-Role Status Item");
        prod.setSku("SKU-MROLE-" + UUID.randomUUID().toString().substring(0, 6));
        prod.setCostPrice(10.0);
        prod.setSellingPrice(20.0);

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 1. ADMIN sets INACTIVE
        mockMvc.perform(patch("/api/v1/products/" + id + "/status")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        // 2. STORE_MANAGER sets ACTIVE
        mockMvc.perform(patch("/api/v1/products/" + id + "/status")
                        .header("Authorization", "Bearer " + managerTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // 3. INVENTORY_STAFF sets ARCHIVED
        mockMvc.perform(patch("/api/v1/products/" + id + "/status")
                        .header("Authorization", "Bearer " + invStaffTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ARCHIVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    @Test
    @DisplayName("AUD-04-HIGH-02: Should reject invalid status values with 400 Bad Request")
    void shouldRejectInvalidStatusValues() throws Exception {
        Product prod = new Product();
        prod.setName("Validate Status Item");
        prod.setSku("SKU-VAL-ST-" + UUID.randomUUID().toString().substring(0, 6));
        prod.setCostPrice(10.0);
        prod.setSellingPrice(20.0);

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Invalid status string
        mockMvc.perform(patch("/api/v1/products/" + id + "/status")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CORRUPTED_STATUS"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Invalid status")));

        // Empty status string
        mockMvc.perform(patch("/api/v1/products/" + id + "/status")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Status value is required")));
    }

    @Test
    @DisplayName("AUD-04-HIGH-02: Should block cross-tenant status update with 404 Not Found")
    void shouldBlockCrossTenantStatusUpdate() throws Exception {
        Product prodA = new Product();
        prodA.setName("Store A Status Prod");
        prodA.setSku("SKU-ST-A-" + UUID.randomUUID().toString().substring(0, 6));
        prodA.setCostPrice(10.0);
        prodA.setSellingPrice(20.0);

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodA)))
                .andExpect(status().isOk())
                .andReturn();

        String idA = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Store B tries to toggle Store A product status
        mockMvc.perform(patch("/api/v1/products/" + idA + "/status")
                        .header("Authorization", "Bearer " + tokenStoreB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isNotFound());

        // Store A product is still ACTIVE
        assertEquals("ACTIVE", productRepository.findByStoreIdAndId(storeA.getId(), idA).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Phase 3B: SKU should be normalized to uppercase and trimmed on creation")
    void shouldNormalizeSkuOnCreation() throws Exception {
        Product prod = new Product();
        prod.setName("Normalized SKU Product");
        prod.setSku("  sku-norm-" + UUID.randomUUID().toString().substring(0, 4) + "  ");
        prod.setCostPrice(15.0);
        prod.setSellingPrice(25.0);

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
        Product saved = productRepository.findByStoreIdAndId(storeA.getId(), id).orElseThrow();
        assertEquals(prod.getSku().trim().toUpperCase(), saved.getSku());
    }

    @Test
    @DisplayName("Phase 3B: Blank barcode should be normalized to null on create and update")
    void shouldNormalizeBlankBarcodeToNull() throws Exception {
        Product prod = new Product();
        prod.setName("Blank Barcode Product");
        prod.setSku("SKU-BC-" + UUID.randomUUID().toString().substring(0, 4));
        prod.setBarcode("   ");
        prod.setCostPrice(10.0);
        prod.setSellingPrice(20.0);

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
        Product saved = productRepository.findByStoreIdAndId(storeA.getId(), id).orElseThrow();
        assertNull(saved.getBarcode());

        // Update with non-empty barcode
        com.grocery.dto.ProductUpdateRequest update1 = new com.grocery.dto.ProductUpdateRequest();
        update1.setBarcode("  890123456789  ");
        mockMvc.perform(put("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update1)))
                .andExpect(status().isOk());

        assertEquals("890123456789", productRepository.findByStoreIdAndId(storeA.getId(), id).orElseThrow().getBarcode());

        // Update back with empty barcode -> should be normalized to null
        com.grocery.dto.ProductUpdateRequest update2 = new com.grocery.dto.ProductUpdateRequest();
        update2.setBarcode("");
        mockMvc.perform(put("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update2)))
                .andExpect(status().isOk());

        assertNull(productRepository.findByStoreIdAndId(storeA.getId(), id).orElseThrow().getBarcode());
    }

    @Test
    @DisplayName("Phase 3B: Category should be resolved authoritatively from DB and reject cross-store categories")
    void shouldValidateCategoryAndDeriveName() throws Exception {
        Category catStoreA = categoryRepository.save(new Category("Dairy A", "dairy-a", "🥛", "Desc", 1, storeA.getId()));
        Category catStoreB = categoryRepository.save(new Category("Dairy B", "dairy-b", "🥛", "Desc", 1, storeB.getId()));

        Product prod = new Product();
        prod.setName("Category Test Product");
        prod.setSku("SKU-CAT-" + UUID.randomUUID().toString().substring(0, 4));
        prod.setCostPrice(10.0);
        prod.setSellingPrice(20.0);
        prod.setCategoryId(catStoreA.getId());
        prod.setCategoryName("Client Supplied Fake Name"); // Should be ignored & derived

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();
        Product saved = productRepository.findByStoreIdAndId(storeA.getId(), id).orElseThrow();
        assertEquals("Dairy A", saved.getCategoryName());

        // Try to update with Category from Store B -> Reject 400
        com.grocery.dto.ProductUpdateRequest updateCrossStore = new com.grocery.dto.ProductUpdateRequest();
        updateCrossStore.setCategoryId(catStoreB.getId());
        mockMvc.perform(put("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateCrossStore)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Category does not belong to your store")));

        // Clear category explicitly with empty string
        com.grocery.dto.ProductUpdateRequest updateClear = new com.grocery.dto.ProductUpdateRequest();
        updateClear.setCategoryId("");
        mockMvc.perform(put("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateClear)))
                .andExpect(status().isOk());

        Product cleared = productRepository.findByStoreIdAndId(storeA.getId(), id).orElseThrow();
        assertNull(cleared.getCategoryId());
        assertNull(cleared.getCategoryName());
    }

    @Test
    @DisplayName("Phase 3B: Partial update via DTO should preserve omitted fields and protect currentStock")
    void shouldPreserveOmittedFieldsAndStockOnUpdate() throws Exception {
        Product prod = new Product();
        prod.setName("Full Product");
        prod.setSku("SKU-FULL-" + UUID.randomUUID().toString().substring(0, 4));
        prod.setCostPrice(50.0);
        prod.setSellingPrice(80.0);
        prod.setCurrentStock(100.0);
        prod.setMinStockLevel(25.0);
        prod.setTaxRate(12.0);
        prod.setUnit("KG");

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();

        // Send partial update changing ONLY selling price
        com.grocery.dto.ProductUpdateRequest partialUpdate = new com.grocery.dto.ProductUpdateRequest();
        partialUpdate.setSellingPrice(95.0);

        mockMvc.perform(put("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(partialUpdate)))
                .andExpect(status().isOk());

        Product updated = productRepository.findByStoreIdAndId(storeA.getId(), id).orElseThrow();
        assertEquals(95.0, updated.getSellingPrice());
        assertEquals(50.0, updated.getCostPrice(), "Cost price should be preserved");
        assertEquals(100.0, updated.getCurrentStock(), "Current stock should be preserved");
        assertEquals(25.0, updated.getMinStockLevel(), "Min stock level should be preserved");
        assertEquals(12.0, updated.getTaxRate(), "Tax rate should be preserved");
        assertEquals("KG", updated.getUnit(), "Unit should be preserved");
    }

    @Test
    @DisplayName("Phase 3B: PUT cannot bypass lifecycle to ARCHIVED or edit archived product")
    void shouldProtectStatusTransitionsOnPut() throws Exception {
        Product prod = new Product();
        prod.setName("Status Protect Product");
        prod.setSku("SKU-SP-" + UUID.randomUUID().toString().substring(0, 4));
        prod.setCostPrice(10.0);
        prod.setSellingPrice(20.0);

        MvcResult res = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prod)))
                .andExpect(status().isOk())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText();

        // 1. Cannot set ARCHIVED directly via PUT
        com.grocery.dto.ProductUpdateRequest archivePut = new com.grocery.dto.ProductUpdateRequest();
        archivePut.setStatus("ARCHIVED");
        mockMvc.perform(put("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(archivePut)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Archival must be performed through the delete/archive endpoint")));

        // Archive product via status PATCH endpoint
        mockMvc.perform(patch("/api/v1/products/" + id + "/status")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ARCHIVED"))))
                .andExpect(status().isOk());

        // 2. Cannot edit an ARCHIVED product via PUT
        com.grocery.dto.ProductUpdateRequest editArchived = new com.grocery.dto.ProductUpdateRequest();
        editArchived.setName("Changed Name");
        mockMvc.perform(put("/api/v1/products/" + id)
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editArchived)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Cannot edit an archived product")));
    }
}
