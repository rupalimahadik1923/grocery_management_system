package com.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocery.dto.LoginRequest;
import com.grocery.dto.StoreRegistrationRequest;
import com.grocery.model.*;
import com.grocery.repository.*;
import com.grocery.service.TenantMigrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MultiTenantIsolationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StoreRepository storeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private ReturnRepository returnRepository;
    @Autowired private InventoryBatchRepository batchRepository;
    @Autowired private InventoryTransactionRepository transactionRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private StoreSettingsRepository settingsRepository;
    @Autowired private TenantMigrationService migrationService;
    @Autowired private MongoTemplate mongoTemplate;

    private final List<String> storeIds = new ArrayList<>();
    private final List<String> extraSaleIds = new ArrayList<>();
    private final List<String> extraExpenseIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String id : extraSaleIds) saleRepository.deleteById(id);
        for (String id : extraExpenseIds) expenseRepository.deleteById(id);
        for (String storeId : storeIds) {
            returnRepository.deleteAll(returnRepository.findByStoreIdOrderByCreatedAtDesc(storeId));
            saleRepository.deleteAll(saleRepository.findByStoreIdOrderByCreatedAtDesc(storeId));
            purchaseOrderRepository.deleteAll(purchaseOrderRepository.findByStoreIdOrderByCreatedAtDesc(storeId));
            batchRepository.deleteAll(batchRepository.findByStoreId(storeId));
            transactionRepository.deleteAll(transactionRepository.findTop50ByStoreIdOrderByCreatedAtDesc(storeId));
            notificationRepository.deleteAll(notificationRepository.findByStoreIdOrderByCreatedAtDesc(storeId));
            expenseRepository.deleteAll(expenseRepository.findByStoreIdOrderByExpenseDateDesc(storeId));
            supplierRepository.deleteAll(supplierRepository.findByStoreId(storeId));
            customerRepository.deleteAll(customerRepository.findByStoreId(storeId));
            productRepository.deleteAll(productRepository.findByStoreId(storeId));
            categoryRepository.deleteAll(categoryRepository.findByStoreId(storeId));
            settingsRepository.deleteById("STORE_SETTINGS_" + storeId);
            userRepository.deleteAll(userRepository.findByStoreId(storeId));
            storeRepository.deleteById(storeId);
        }
        extraSaleIds.clear();
        extraExpenseIds.clear();
        storeIds.clear();
    }

    private record StoreContext(String storeId, String storeCode, String adminToken, String managerToken, String cashierToken, String invToken) {}

    private StoreContext provisionStoreWithAllRoles(String label) throws Exception {
        String s = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        StoreRegistrationRequest reg = new StoreRegistrationRequest();
        reg.setStoreName("Store " + label + " " + s);
        reg.setStoreCode("st" + label + s);
        reg.setOwnerName("Owner " + label);
        reg.setOwnerEmail("owner_" + label + "_" + s + "@test.local");
        reg.setCity("Bengaluru");
        reg.setPassword("Pass1234!");
        reg.setAdminUsername("adm_" + label + "_" + s);

        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register-store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(regRes.getResponse().getContentAsString()).get("data");
        String storeId = data.get("storeId").asText();
        String storeCode = data.get("storeCode").asText();
        String adminToken = data.get("token").asText();
        storeIds.add(storeId);

        String managerToken = createStaffAndLogin(adminToken, storeCode, "mgr_" + label + "_" + s, "STORE_MANAGER");
        String cashierToken = createStaffAndLogin(adminToken, storeCode, "csh_" + label + "_" + s, "CASHIER");
        String invToken = createStaffAndLogin(adminToken, storeCode, "inv_" + label + "_" + s, "INVENTORY_STAFF");
        return new StoreContext(storeId, storeCode, adminToken, managerToken, cashierToken, invToken);
    }

    private String createStaffAndLogin(String adminToken, String storeCode, String username, String role) throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"username\":\"%s\",\"password\":\"Pass1234!\",\"fullName\":\"%s\",\"email\":\"%s@test.local\",\"role\":\"%s\"}",
                                username, username, username, role)))
                .andExpect(status().isOk());

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "Pass1234!", storeCode))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginRes.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("All 9 domains: Store A & Store B are completely isolated across CRUD, relationships, IDOR, and reports")
    void testCompleteMultiTenantIsolationAcrossAllDomains() throws Exception {
        StoreContext a = provisionStoreWithAllRoles("a");
        StoreContext b = provisionStoreWithAllRoles("b");

        // 1. Product in Store A (with low stock threshold so sale triggers Store A notification)
        MvcResult prodARes = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + a.invToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"StoreA Milk\",\"sku\":\"MILK-A1\",\"unit\":\"PACK\",\"costPrice\":20,\"sellingPrice\":30,\"currentStock\":5,\"minStockLevel\":4,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String prodAId = objectMapper.readTree(prodARes.getResponse().getContentAsString()).get("data").get("id").asText();

        // 2. Customer in Store A
        MvcResult custARes = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + a.cashierToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cust A\",\"phone\":\"9000011111\",\"email\":\"ca@test.local\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String custAId = objectMapper.readTree(custARes.getResponse().getContentAsString()).get("data").get("id").asText();

        // Store B cannot see or update Store A's customer
        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + b.cashierToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(custAId))));
        mockMvc.perform(get("/api/v1/customers/by-phone/9000011111").header("Authorization", "Bearer " + b.cashierToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/customers/" + custAId).header("Authorization", "Bearer " + b.cashierToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isNotFound());

        // 3. Supplier & PurchaseOrder in Store A -> Batch & Transaction in Store A
        MvcResult supARes = mockMvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", "Bearer " + a.managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Supplier A\",\"phone\":\"9111122222\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String supAId = objectMapper.readTree(supARes.getResponse().getContentAsString()).get("data").get("id").asText();

        // Store B cannot list, update, or delete Store A's supplier
        mockMvc.perform(get("/api/v1/suppliers").header("Authorization", "Bearer " + b.managerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(supAId))));
        mockMvc.perform(delete("/api/v1/suppliers/" + supAId).header("Authorization", "Bearer " + b.managerToken()))
                .andExpect(status().isNotFound());

        // Relationship integrity: Store B cannot create PO referencing Store A's supplier or product
        mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + b.managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"" + supAId + "\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + b.managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + prodAId + "\",\"orderQty\":2,\"unitCost\":20,\"totalCost\":40}]}"))
                .andExpect(status().isBadRequest());

        // Store A creates, submits, and receives PO -> creates Batch + Transaction
        MvcResult poARes = mockMvc.perform(post("/api/v1/purchases")
                        .header("Authorization", "Bearer " + a.managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"" + supAId + "\",\"supplierName\":\"Supplier A\",\"items\":[{\"productId\":\"" + prodAId + "\",\"productName\":\"StoreA Milk\",\"orderQty\":2,\"unitCost\":20,\"totalCost\":40}]}"))
                .andExpect(status().isOk())
                .andReturn();
        String poAId = objectMapper.readTree(poARes.getResponse().getContentAsString()).get("data").get("id").asText();

        mockMvc.perform(post("/api/v1/purchases/" + poAId + "/submit").header("Authorization", "Bearer " + b.managerToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/purchases/" + poAId + "/submit").header("Authorization", "Bearer " + a.managerToken()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/purchases/" + poAId + "/receive").header("Authorization", "Bearer " + b.managerToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/purchases/" + poAId + "/receive").header("Authorization", "Bearer " + a.managerToken()))
                .andExpect(status().isOk());

        // Store B cannot see Store A's batches, transactions, or adjust Store A's product
        mockMvc.perform(get("/api/v1/inventory/batches").header("Authorization", "Bearer " + b.invToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/inventory/transactions").header("Authorization", "Bearer " + b.invToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(post("/api/v1/inventory/adjustments").header("Authorization", "Bearer " + b.invToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + prodAId + "\",\"quantity\":-1}"))
                .andExpect(status().isBadRequest());

        // 4. Sale & Return in Store A (qty 4 drops stock 7 -> 3 <= minStockLevel 4 -> triggers Notification)
        // First verify Store B cannot create a Sale referencing Store A's product or customer
        mockMvc.perform(post("/api/v1/sales").header("Authorization", "Bearer " + b.cashierToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + prodAId + "\",\"quantity\":1}],\"grandTotal\":30}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/sales").header("Authorization", "Bearer " + b.cashierToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + custAId + "\",\"items\":[],\"grandTotal\":30}"))
                .andExpect(status().isBadRequest());

        MvcResult saleARes = mockMvc.perform(post("/api/v1/sales").header("Authorization", "Bearer " + a.cashierToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + custAId + "\",\"items\":[{\"productId\":\"" + prodAId + "\",\"quantity\":4,\"unitPrice\":30,\"lineTotal\":120}],\"subtotal\":120,\"grandTotal\":120}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode saleAData = objectMapper.readTree(saleARes.getResponse().getContentAsString()).get("data");
        String saleAId = saleAData.get("id").asText();
        String receiptA = saleAData.get("receiptNumber").asText();

        // Store B cannot list or retrieve Store A's sale or receipt
        mockMvc.perform(get("/api/v1/sales").header("Authorization", "Bearer " + b.cashierToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/sales/" + saleAId).header("Authorization", "Bearer " + b.cashierToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/sales/receipt/" + receiptA).header("Authorization", "Bearer " + b.cashierToken()))
                .andExpect(status().isNotFound());

        // Store B cannot create Return referencing Store A's sale
        mockMvc.perform(post("/api/v1/returns").header("Authorization", "Bearer " + b.cashierToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleId\":\"" + saleAId + "\",\"totalRefund\":30}"))
                .andExpect(status().isBadRequest());

        // Store A processes return; Store B cannot see it
        mockMvc.perform(post("/api/v1/returns").header("Authorization", "Bearer " + a.cashierToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleId\":\"" + saleAId + "\",\"items\":[{\"productId\":\"" + prodAId + "\",\"quantity\":1,\"restocked\":true}],\"totalRefund\":30}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/returns").header("Authorization", "Bearer " + b.cashierToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 5. Notifications in Store A are invisible to Store B
        MvcResult notifARes = mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + a.invToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andReturn();
        String notifAId = objectMapper.readTree(notifARes.getResponse().getContentAsString()).get(0).get("id").asText();

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + b.invToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer " + b.invToken()))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
        mockMvc.perform(patch("/api/v1/notifications/" + notifAId + "/read").header("Authorization", "Bearer " + b.invToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/notifications/" + notifAId).header("Authorization", "Bearer " + b.invToken()))
                .andExpect(status().isNotFound());

        // 6. Expense in Store A + spoofed storeId ignored in Store B
        MvcResult expARes = mockMvc.perform(post("/api/v1/expenses").header("Authorization", "Bearer " + a.managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"" + b.storeId() + "\",\"title\":\"Rent A\",\"category\":\"Rent\",\"amount\":500}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode expAData = objectMapper.readTree(expARes.getResponse().getContentAsString()).get("data");
        assertEquals(a.storeId(), expAData.get("storeId").asText(), "Client-supplied storeId must be ignored");
        String expAId = expAData.get("id").asText();

        mockMvc.perform(get("/api/v1/expenses").header("Authorization", "Bearer " + b.managerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(delete("/api/v1/expenses/" + expAId).header("Authorization", "Bearer " + b.managerToken()))
                .andExpect(status().isNotFound());

        // 7. Reports & CSV Exports: Store B must report 0 revenue, 0 expenses, 0 products, and exclude Store A data
        mockMvc.perform(get("/api/v1/reports/dashboard").header("Authorization", "Bearer " + b.managerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts").value(0))
                .andExpect(jsonPath("$.totalRevenue").value(0.0))
                .andExpect(jsonPath("$.monthExpenses").value(0.0));
        mockMvc.perform(get("/api/v1/reports/sales").header("Authorization", "Bearer " + b.managerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(0))
                .andExpect(jsonPath("$.totalRevenue").value(0.0));
        mockMvc.perform(get("/api/v1/reports/profit-loss").header("Authorization", "Bearer " + b.managerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue").value(0.0))
                .andExpect(jsonPath("$.expenses").value(0.0));
        mockMvc.perform(get("/api/v1/reports/export/sales").header("Authorization", "Bearer " + b.managerToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(receiptA))));
    }

    @Test
    @DisplayName("Safe Data Migration: dry-run vs execute backfills inferred records and leaves ambiguous records untouched")
    void testSafeMigrationDryRunAndUnresolvedHandling() throws Exception {
        StoreContext a = provisionStoreWithAllRoles("mig");
        MvcResult prodARes = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + a.invToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mig Prod\",\"sku\":\"MIG-1\",\"unit\":\"PACK\",\"costPrice\":10,\"sellingPrice\":15,\"currentStock\":10,\"minStockLevel\":2,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String prodAId = objectMapper.readTree(prodARes.getResponse().getContentAsString()).get("data").get("id").asText();

        // Create legacy unowned Sale referencing Store A product + unowned Expense with no store link
        Sale legacySale = new Sale();
        legacySale.setReceiptNumber("RCP-LEG-" + UUID.randomUUID().toString().substring(0, 6));
        legacySale.setItems(List.of(new Sale.SaleItem(prodAId, "Mig Prod", "MIG-1", 15, 1, 0)));
        legacySale.setGrandTotal(15);
        Sale savedLegacySale = saleRepository.save(legacySale);
        extraSaleIds.add(savedLegacySale.getId());

        Expense ambiguousExpense = new Expense();
        ambiguousExpense.setTitle("Ambiguous Legacy Expense");
        ambiguousExpense.setAmount(100);
        Expense savedAmbiguousExpense = expenseRepository.save(ambiguousExpense);
        extraExpenseIds.add(savedAmbiguousExpense.getId());

        // Dry-run: identifies migratable sale without modifying DB
        TenantMigrationService.MigrationReport dry = migrationService.runMigration(true);
        assertTrue(dry.migratedCounts.getOrDefault("sales", 0) >= 1);
        assertNull(saleRepository.findById(savedLegacySale.getId()).orElseThrow().getStoreId());

        // Execute: backfills sale.storeId, leaves ambiguous expense untouched & reported
        TenantMigrationService.MigrationReport exec = migrationService.runMigration(false);
        assertEquals(a.storeId(), saleRepository.findById(savedLegacySale.getId()).orElseThrow().getStoreId());
        assertNull(expenseRepository.findById(savedAmbiguousExpense.getId()).orElseThrow().getStoreId());
        assertTrue(exec.unresolvedIds.getOrDefault("expenses", List.of()).contains(savedAmbiguousExpense.getId()));

        // Idempotent re-run: 0 sales migrated on second pass
        TenantMigrationService.MigrationReport second = migrationService.runMigration(false);
        assertEquals(0, second.migratedCounts.getOrDefault("sales", 0));

        // Contradictory ownership: Sale with Item 1 (Store A) + Item 2 (Store B) must NOT be migrated
        StoreContext b = provisionStoreWithAllRoles("migb");
        MvcResult prodBRes = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + b.invToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mig Prod B\",\"sku\":\"MIG-B1\",\"unit\":\"PACK\",\"costPrice\":10,\"sellingPrice\":15,\"currentStock\":10,\"minStockLevel\":2,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String prodBId = objectMapper.readTree(prodBRes.getResponse().getContentAsString()).get("data").get("id").asText();

        Sale conflictingSale = new Sale();
        conflictingSale.setReceiptNumber("RCP-CONF-" + UUID.randomUUID().toString().substring(0, 6));
        conflictingSale.setItems(List.of(
                new Sale.SaleItem(prodAId, "Mig Prod", "MIG-1", 15, 1, 0),
                new Sale.SaleItem(prodBId, "Mig Prod B", "MIG-B1", 15, 1, 0)
        ));
        conflictingSale.setGrandTotal(30);
        Sale savedConflict = saleRepository.save(conflictingSale);
        extraSaleIds.add(savedConflict.getId());

        TenantMigrationService.MigrationReport conflictReport = migrationService.runMigration(false);
        assertTrue(conflictReport.conflictingCount >= 1);
        assertTrue(conflictReport.conflictingIds.getOrDefault("sales", List.of()).contains(savedConflict.getId()));
        assertNull(saleRepository.findById(savedConflict.getId()).orElseThrow().getStoreId(),
                "Conflicting sale must remain untouched with null storeId");
    }

    @Test
    @DisplayName("Compound unique indexes: same Customer phone & Supplier code allowed across stores, blocked within same store")
    void testCompoundUniqueIndexesAndCrossTenantCategoryGuard() throws Exception {
        StoreContext a = provisionStoreWithAllRoles("idxa");
        StoreContext b = provisionStoreWithAllRoles("idxb");

        // Verify MongoDB compound unique indexes exist and legacy global unique indexes are absent
        assertTrue(mongoTemplate.indexOps(Customer.class).getIndexInfo().stream()
                .anyMatch(i -> "storeId_phone_unique_idx".equals(i.getName()) && i.isUnique()));
        assertFalse(mongoTemplate.indexOps(Customer.class).getIndexInfo().stream()
                .anyMatch(i -> "phone".equals(i.getName()) && i.isUnique()));
        assertTrue(mongoTemplate.indexOps(Supplier.class).getIndexInfo().stream()
                .anyMatch(i -> "storeId_code_unique_idx".equals(i.getName()) && i.isUnique()));
        assertFalse(mongoTemplate.indexOps(Supplier.class).getIndexInfo().stream()
                .anyMatch(i -> "code".equals(i.getName()) && i.isUnique()));

        // Same customer phone allowed in Store A and Store B, rejected twice in Store A
        String sharedPhone = "9898989898";
        mockMvc.perform(post("/api/v1/customers").header("Authorization", "Bearer " + a.cashierToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cust A\",\"phone\":\"" + sharedPhone + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/customers").header("Authorization", "Bearer " + b.cashierToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cust B\",\"phone\":\"" + sharedPhone + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/customers").header("Authorization", "Bearer " + a.cashierToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cust A Dup\",\"phone\":\"" + sharedPhone + "\"}"))
                .andExpect(status().isBadRequest());

        // Same supplier code allowed in Store A and Store B, rejected twice in Store A
        String sharedCode = "SUP-SHARED-01";
        mockMvc.perform(post("/api/v1/suppliers").header("Authorization", "Bearer " + a.managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sup A\",\"code\":\"" + sharedCode + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/suppliers").header("Authorization", "Bearer " + b.managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sup B\",\"code\":\"" + sharedCode + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/suppliers").header("Authorization", "Bearer " + a.managerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sup A Dup\",\"code\":\"" + sharedCode + "\"}"))
                .andExpect(status().isBadRequest());

        // Cross-tenant categoryId rejected when creating a Product in Store B
        String catAId = categoryRepository.findByStoreId(a.storeId()).get(0).getId();
        mockMvc.perform(post("/api/v1/products").header("Authorization", "Bearer " + b.invToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CrossCatProd\",\"sku\":\"CCP-1\",\"categoryId\":\"" + catAId + "\",\"unit\":\"PACK\",\"costPrice\":10,\"sellingPrice\":15}"))
                .andExpect(status().isBadRequest());

        // Unauthenticated requests rejected with 401
        mockMvc.perform(get("/api/v1/sales")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/suppliers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/purchases")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/expenses")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/inventory")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/reports/dashboard")).andExpect(status().isUnauthorized());
    }
}
