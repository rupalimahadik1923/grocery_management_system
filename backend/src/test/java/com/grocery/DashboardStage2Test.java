package com.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocery.dto.StoreRegistrationRequest;
import com.grocery.model.*;
import com.grocery.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardStage2Test {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StoreRepository storeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private ReturnRepository returnRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InventoryBatchRepository batchRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private StoreSettingsRepository settingsRepository;

    private final List<String> storeIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String storeId : storeIds) {
            notificationRepository.deleteAll(notificationRepository.findByStoreIdOrderByCreatedAtDesc(storeId));
            returnRepository.deleteAll(returnRepository.findByStoreIdOrderByCreatedAtDesc(storeId));
            saleRepository.deleteAll(saleRepository.findByStoreIdOrderByCreatedAtDesc(storeId));
            batchRepository.deleteAll(batchRepository.findByStoreId(storeId));
            expenseRepository.deleteAll(expenseRepository.findByStoreIdOrderByExpenseDateDesc(storeId));
            productRepository.deleteAll(productRepository.findByStoreId(storeId));
            categoryRepository.deleteAll(categoryRepository.findByStoreId(storeId));
            settingsRepository.deleteById("STORE_SETTINGS_" + storeId);
            userRepository.deleteAll(userRepository.findByStoreId(storeId));
            storeRepository.deleteById(storeId);
        }
        storeIds.clear();
    }

    private record StoreAuth(String storeId, String token) {}

    private StoreAuth createIsolatedStore() throws Exception {
        String s = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        StoreRegistrationRequest reg = new StoreRegistrationRequest();
        reg.setStoreName("Dash Store " + s);
        reg.setStoreCode("dsh" + s);
        reg.setOwnerName("Dash Owner");
        reg.setOwnerEmail("dash_" + s + "@test.local");
        reg.setCity("Bengaluru");
        reg.setPassword("Pass1234!");
        reg.setAdminUsername("dashadm_" + s);

        MvcResult res = mockMvc.perform(post("/api/v1/auth/register-store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
        String storeId = data.get("storeId").asText();
        storeIds.add(storeId);
        return new StoreAuth(storeId, data.get("token").asText());
    }

    private JsonNode getDashboard(String token, String query) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/reports/dashboard" + (query == null ? "" : query))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private Product saveProduct(String storeId, String name, String status, double stock, double minStock, double costPrice, double sellPrice) {
        Product p = new Product();
        p.setStoreId(storeId);
        p.setName(name);
        p.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 6));
        p.setStatus(status);
        p.setCurrentStock(stock);
        p.setMinStockLevel(minStock);
        p.setCostPrice(costPrice);
        p.setSellingPrice(sellPrice);
        p.setUnit("pcs");
        return productRepository.save(p);
    }

    private Sale saveSale(String storeId, String receipt, double grandTotal, String status, LocalDateTime createdAt, List<Sale.SaleItem> items) {
        Sale s = new Sale();
        s.setStoreId(storeId);
        s.setReceiptNumber(receipt);
        s.setCustomerName("Customer " + receipt);
        s.setGrandTotal(grandTotal);
        s.setSubtotal(grandTotal);
        s.setStatus(status);
        s.setCreatedAt(createdAt);
        s.setItems(items);
        return saleRepository.save(s);
    }

    private Sale.SaleItem saleItem(Product p, double qty, double unitPrice, Double costPrice) {
        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(qty);
        item.setUnitPrice(unitPrice);
        item.setLineTotal(qty * unitPrice);
        item.setCostPrice(costPrice);
        return item;
    }

    @Test
    @DisplayName("DASH-SALES-01..12: Gross vs Net Sales, Completed vs Non-Completed Refunds, Over-Refund Cap, Cancelled/Voided Exclusion, AOV")
    void salesRefundsAndAovVerification() throws Exception {
        StoreAuth auth = createIsolatedStore();
        LocalDateTime nowIst = LocalDateTime.now(KOLKATA);
        Product rice = saveProduct(auth.storeId(), "Basmati Rice", "ACTIVE", 50, 10, 80.0, 120.0);

        // Eligible completed sales today: 1000 + 500 = 1500 gross
        Sale s1 = saveSale(auth.storeId(), "RCP-101", 1000.0, "COMPLETED", nowIst, List.of(saleItem(rice, 5, 200.0, 80.0)));
        Sale s2 = saveSale(auth.storeId(), "RCP-102", 500.0, "PARTIALLY_RETURNED", nowIst, List.of(saleItem(rice, 2, 250.0, 80.0)));

        // Cancelled & Voided sales must be excluded from gross, net, and orders count
        saveSale(auth.storeId(), "RCP-103", 999.0, "CANCELLED", nowIst, List.of(saleItem(rice, 1, 999.0, 80.0)));
        saveSale(auth.storeId(), "RCP-104", 888.0, "VOIDED", nowIst, List.of(saleItem(rice, 1, 888.0, 80.0)));

        // Completed partial refund on s2: 200.0
        ReturnRecord rCompleted = new ReturnRecord();
        rCompleted.setStoreId(auth.storeId());
        rCompleted.setSaleId(s2.getId());
        rCompleted.setReturnNumber("RET-201");
        rCompleted.setStatus("COMPLETED");
        rCompleted.setTotalRefund(200.0);
        rCompleted.setCreatedAt(nowIst);
        returnRepository.save(rCompleted);

        // Pending & Rejected refunds must NOT be deducted
        for (String st : List.of("PENDING", "FAILED", "REJECTED")) {
            ReturnRecord rIgnored = new ReturnRecord();
            rIgnored.setStoreId(auth.storeId());
            rIgnored.setSaleId(s1.getId());
            rIgnored.setReturnNumber("RET-" + st);
            rIgnored.setStatus(st);
            rIgnored.setTotalRefund(300.0);
            rIgnored.setCreatedAt(nowIst);
            returnRepository.save(rIgnored);
        }

        // Over-refund attempt via API must be rejected with 400
        ReturnRecord overRefund = new ReturnRecord();
        overRefund.setSaleId(s2.getId());
        overRefund.setTotalRefund(400.0); // 200 + 400 > 500
        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overRefund)))
                .andExpect(status().isBadRequest());

        JsonNode dash = getDashboard(auth.token(), "?days=1");
        assertEquals(1500.0, dash.get("todayGrossSales").asDouble(), 0.01);
        assertEquals(1300.0, dash.get("todayNetSales").asDouble(), 0.01);
        assertEquals(1300.0, dash.get("todaySales").asDouble(), 0.01);
        assertEquals(2L, dash.get("todayOrdersCount").asLong());
        assertEquals(650.0, dash.get("aov").asDouble(), 0.01);
    }

    @Test
    @DisplayName("DASH-COGS-01..07: Checkout Cost Snapshot, Historical COGS/Profit, Missing Cost Snapshot Explicit Unavailable")
    void historicalCogsAndCheckoutSnapshot() throws Exception {
        StoreAuth auth = createIsolatedStore();
        Product oil = saveProduct(auth.storeId(), "Olive Oil", "ACTIVE", 20, 5, 100.0, 150.0);

        // Checkout via API without passing costPrice -> backend must snapshot product.costPrice (100.0)
        Sale checkoutReq = new Sale();
        checkoutReq.setSubtotal(300.0);
        checkoutReq.setGrandTotal(300.0);
        checkoutReq.setPaymentMethod("CASH");
        Sale.SaleItem checkoutItem = new Sale.SaleItem();
        checkoutItem.setProductId(oil.getId());
        checkoutItem.setProductName(oil.getName());
        checkoutItem.setQuantity(2);
        checkoutItem.setUnitPrice(150.0);
        checkoutItem.setLineTotal(300.0);
        checkoutReq.setItems(List.of(checkoutItem));

        mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isOk());

        // Change product costPrice later to 200.0 -> historical COGS must stay 2 * 100.0 = 200.0!
        oil.setCostPrice(200.0);
        productRepository.save(oil);

        Expense rent = new Expense();
        rent.setStoreId(auth.storeId());
        rent.setTitle("Shop Electricity");
        rent.setAmount(40.0);
        rent.setExpenseDate(LocalDate.now(KOLKATA));
        expenseRepository.save(rent);

        JsonNode dashWithCogs = getDashboard(auth.token(), "?days=1");
        assertTrue(dashWithCogs.get("cogsAvailable").asBoolean());
        assertEquals(200.0, dashWithCogs.get("cogs").asDouble(), 0.01);
        assertEquals(100.0, dashWithCogs.get("grossProfit").asDouble(), 0.01);
        assertEquals(60.0, dashWithCogs.get("netProfit").asDouble(), 0.01);

        // Now add a legacy sale WITHOUT costPrice snapshot -> must mark cogsAvailable=false, no fallback!
        saveSale(auth.storeId(), "RCP-LEGACY", 150.0, "COMPLETED", LocalDateTime.now(KOLKATA),
                List.of(saleItem(oil, 1, 150.0, null)));

        JsonNode dashNoCogs = getDashboard(auth.token(), "?days=1");
        assertFalse(dashNoCogs.get("cogsAvailable").asBoolean());
        assertTrue(dashNoCogs.get("cogs").isNull());
        assertTrue(dashNoCogs.get("grossProfit").isNull());
        assertTrue(dashNoCogs.get("netProfit").isNull());
        assertFalse(dashNoCogs.get("cogsUnavailableReason").asText().isBlank());
    }

    @Test
    @DisplayName("DASH-DATE-01..07: Asia/Kolkata Boundaries for Today, Month Expenses, and Trend Buckets")
    void kolkataDateBoundariesAndExpenses() throws Exception {
        StoreAuth auth = createIsolatedStore();
        LocalDate today = LocalDate.now(KOLKATA);
        Product sugar = saveProduct(auth.storeId(), "Sugar", "ACTIVE", 40, 5, 30.0, 45.0);

        // Sale at 00:05 today and 23:55 yesterday
        saveSale(auth.storeId(), "RCP-TODAY", 450.0, "COMPLETED", today.atTime(0, 5), List.of(saleItem(sugar, 10, 45.0, 30.0)));
        saveSale(auth.storeId(), "RCP-YEST", 900.0, "COMPLETED", today.minusDays(1).atTime(23, 55), List.of(saleItem(sugar, 20, 45.0, 30.0)));

        // Expenses: current month vs previous month
        Expense currExp = new Expense();
        currExp.setStoreId(auth.storeId());
        currExp.setTitle("Current Month Expense");
        currExp.setAmount(1200.0);
        currExp.setExpenseDate(today.withDayOfMonth(1));
        expenseRepository.save(currExp);

        Expense prevExp = new Expense();
        prevExp.setStoreId(auth.storeId());
        prevExp.setTitle("Previous Month Expense");
        prevExp.setAmount(5000.0);
        prevExp.setExpenseDate(today.withDayOfMonth(1).minusDays(1));
        expenseRepository.save(prevExp);

        JsonNode dash = getDashboard(auth.token(), "?days=7");
        assertEquals(450.0, dash.get("todayNetSales").asDouble(), 0.01);
        assertEquals(1L, dash.get("todayOrdersCount").asLong());
        assertEquals(1350.0, dash.get("periodNetSales").asDouble(), 0.01);
        assertEquals(1200.0, dash.get("monthExpenses").asDouble(), 0.01);
        assertEquals(7, dash.get("salesTrend").size());
    }

    @Test
    @DisplayName("DASH-PROD-01..06 & Two-Store Isolation: Active/Low/Out-of-Stock, Top Products, Alerts, Tenant Isolation")
    void productCountsAlertsAndTwoStoreIsolation() throws Exception {
        StoreAuth storeA = createIsolatedStore();
        StoreAuth storeB = createIsolatedStore();
        LocalDate today = LocalDate.now(KOLKATA);

        Product pNormal = saveProduct(storeA.storeId(), "A-Normal", "ACTIVE", 25, 5, 10, 20);
        Product pLow = saveProduct(storeA.storeId(), "B-Low", "ACTIVE", 5, 5, 10, 20);
        saveProduct(storeA.storeId(), "C-Out", "ACTIVE", 0, 5, 10, 20);
        saveProduct(storeA.storeId(), "D-Inactive", "INACTIVE", 0, 5, 10, 20);

        // Expiring batch in 3 days
        InventoryBatch batch = new InventoryBatch();
        batch.setStoreId(storeA.storeId());
        batch.setProductId(pNormal.getId());
        batch.setBatchNumber("BATCH-EXP-1");
        batch.setExpiryDate(today.plusDays(3));
        batch.setCurrentQty(10);
        batch.setStatus("ACTIVE");
        batchRepository.save(batch);

        // Sale in store A and big sale in store B (must never leak into store A)
        saveSale(storeA.storeId(), "RCP-A1", 200.0, "COMPLETED", today.atTime(10, 0),
                List.of(saleItem(pNormal, 6, 20.0, 10.0), saleItem(pLow, 4, 20.0, 10.0)));
        Product bProd = saveProduct(storeB.storeId(), "StoreB-Only", "ACTIVE", 100, 10, 50, 100);
        saveSale(storeB.storeId(), "RCP-B1", 99999.0, "COMPLETED", today.atTime(11, 0),
                List.of(saleItem(bProd, 500, 100.0, 50.0)));

        JsonNode dashA = getDashboard(storeA.token(), "?days=7");
        assertEquals(3L, dashA.get("totalProducts").asLong());
        assertEquals(1L, dashA.get("lowStockCount").asLong());
        assertEquals(1L, dashA.get("outOfStockCount").asLong());
        assertEquals(1L, dashA.get("expiringCount").asLong());
        assertEquals(200.0, dashA.get("todayNetSales").asDouble(), 0.01);
        assertEquals("A-Normal", dashA.get("topProducts").get(0).get("productName").asText());
        assertTrue(dashA.get("alerts").size() >= 3);
        assertEquals(1, dashA.get("recentSales").size());
        assertEquals("RCP-A1", dashA.get("recentSales").get(0).get("receiptNumber").asText());
    }
}
