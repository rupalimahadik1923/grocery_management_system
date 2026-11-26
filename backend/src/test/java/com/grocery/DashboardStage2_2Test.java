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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardStage2_2Test {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

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
        reg.setStoreName("Rem Store " + s);
        reg.setStoreCode("rem" + s);
        reg.setOwnerName("Rem Owner");
        reg.setOwnerEmail("rem_" + s + "@test.local");
        reg.setCity("Pune");
        reg.setPassword("Pass1234!");
        reg.setAdminUsername("remadm_" + s);

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

    private Product saveProduct(String storeId, String name, String status, double stock, double costPrice, double sellPrice) {
        Product p = new Product();
        p.setStoreId(storeId);
        p.setName(name);
        p.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 6));
        p.setStatus(status);
        p.setCurrentStock(stock);
        p.setMinStockLevel(5);
        p.setCostPrice(costPrice);
        p.setSellingPrice(sellPrice);
        p.setUnit("pcs");
        return productRepository.save(p);
    }

    private JsonNode getDashboard(String token, String query) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/reports/dashboard" + (query == null ? "" : query))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Phase A: Server-authoritative costPrice overwrites client tampering and rejects invalid/inactive/cross-store products")
    void phaseA_serverAuthoritativeCostSnapshot() throws Exception {
        StoreAuth storeA = createIsolatedStore();
        StoreAuth storeB = createIsolatedStore();

        Product prod100 = saveProduct(storeA.storeId(), "Ghee", "ACTIVE", 50, 100.0, 150.0);
        Product inactiveProd = saveProduct(storeA.storeId(), "Old Tea", "INACTIVE", 10, 40.0, 60.0);
        Product zeroCostProd = saveProduct(storeA.storeId(), "Unpriced Cost Item", "ACTIVE", 10, 0.0, 50.0);
        Product storeBProd = saveProduct(storeB.storeId(), "StoreB Item", "ACTIVE", 10, 20.0, 30.0);

        // Client submits costPrice=1.0, 0.0, and 99999.0 -> backend MUST overwrite with 100.0
        for (double tamperedCost : List.of(1.0, 0.0, 99999.0)) {
            Sale req = new Sale();
            req.setSubtotal(150.0);
            req.setGrandTotal(150.0);
            Sale.SaleItem item = new Sale.SaleItem();
            item.setProductId(prod100.getId());
            item.setProductName(prod100.getName());
            item.setQuantity(1);
            item.setUnitPrice(150.0);
            item.setLineTotal(150.0);
            item.setCostPrice(tamperedCost);
            req.setItems(List.of(item));

            MvcResult res = mockMvc.perform(post("/api/v1/sales")
                            .header("Authorization", "Bearer " + storeA.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode savedItem = objectMapper.readTree(res.getResponse().getContentAsString())
                    .get("data").get("items").get(0);
            assertEquals(100.0, savedItem.get("costPrice").asDouble(), 0.001,
                    "Tampered costPrice " + tamperedCost + " must be overwritten by server product cost 100.0");
            assertFalse(savedItem.get("saleItemId").asText("").isBlank());
        }

        // Reject cross-store product, missing productId, and INACTIVE product
        for (String badProdId : List.of(storeBProd.getId(), "", inactiveProd.getId())) {
            Sale badReq = new Sale();
            badReq.setSubtotal(50.0);
            badReq.setGrandTotal(50.0);
            Sale.SaleItem item = new Sale.SaleItem();
            item.setProductId(badProdId);
            item.setQuantity(1);
            item.setUnitPrice(50.0);
            badReq.setItems(List.of(item));
            mockMvc.perform(post("/api/v1/sales")
                            .header("Authorization", "Bearer " + storeA.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badReq)))
                    .andExpect(status().isBadRequest());
        }

        // Missing product cost (0.0 on Product) must NOT substitute 0 -> saved as null and marks COGS unavailable
        Sale zeroReq = new Sale();
        zeroReq.setSubtotal(50.0);
        zeroReq.setGrandTotal(50.0);
        Sale.SaleItem zItem = new Sale.SaleItem();
        zItem.setProductId(zeroCostProd.getId());
        zItem.setQuantity(1);
        zItem.setUnitPrice(50.0);
        zItem.setCostPrice(10.0);
        zeroReq.setItems(List.of(zItem));

        MvcResult zRes = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + storeA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroReq)))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(objectMapper.readTree(zRes.getResponse().getContentAsString())
                .get("data").get("items").get(0).get("costPrice").isNull());
    }

    @Test
    @DisplayName("Phase B & C: Return lifecycle PENDING->COMPLETED, idempotencyKey, no double-restock, null/unknown status ignored, concurrent over-refund guard")
    void phaseBC_refundLifecycleIdempotencyAndConcurrency() throws Exception {
        StoreAuth auth = createIsolatedStore();
        Product prod = saveProduct(auth.storeId(), "Almonds", "ACTIVE", 10.0, 200.0, 500.0);

        // Checkout 2 units = 1000.0 -> stock drops to 8.0
        Sale saleReq = new Sale();
        saleReq.setSubtotal(1000.0);
        saleReq.setGrandTotal(1000.0);
        Sale.SaleItem sItem = new Sale.SaleItem();
        sItem.setProductId(prod.getId());
        sItem.setProductName(prod.getName());
        sItem.setQuantity(2);
        sItem.setUnitPrice(500.0);
        sItem.setLineTotal(1000.0);
        saleReq.setItems(List.of(sItem));

        MvcResult saleRes = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saleReq)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode saleData = objectMapper.readTree(saleRes.getResponse().getContentAsString()).get("data");
        String saleId = saleData.get("id").asText();
        String saleItemId = saleData.get("items").get(0).get("saleItemId").asText();

        // Zero refund rejected
        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleId\":\"" + saleId + "\",\"totalRefund\":0}"))
                .andExpect(status().isBadRequest());

        // Submit return with idempotencyKey -> created as PENDING, stock restocked once (8 + 1 = 9)
        String idemKey = "idem-" + UUID.randomUUID();
        String retBody = String.format(
                "{\"saleId\":\"%s\",\"idempotencyKey\":\"%s\",\"totalRefund\":500.0,\"refundMethod\":\"CASH\",\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"productName\":\"Almonds\",\"quantity\":1,\"refundAmount\":500.0,\"restocked\":true}]}",
                saleId, idemKey, saleItemId, prod.getId());

        MvcResult ret1 = mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(retBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ret1Data = objectMapper.readTree(ret1.getResponse().getContentAsString()).get("data");
        String retId = ret1Data.get("id").asText();
        assertEquals("PENDING", ret1Data.get("status").asText());
        assertEquals(9.0, productRepository.findById(prod.getId()).orElseThrow().getCurrentStock(), 0.001);

        // Duplicate submission with same idempotencyKey -> returns same record, stock stays 9.0
        MvcResult retDup = mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(retBody))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(retId, objectMapper.readTree(retDup.getResponse().getContentAsString()).get("data").get("id").asText());
        assertEquals(9.0, productRepository.findById(prod.getId()).orElseThrow().getCurrentStock(), 0.001);

        // While PENDING, dashboard Net Sales must still be 1000.0
        assertEquals(1000.0, getDashboard(auth.token(), "?days=1").get("todayNetSales").asDouble(), 0.01);

        // Null-status and UNKNOWN-status ReturnRecords in DB must NOT reduce Net Sales
        for (String badStatus : new String[]{null, "UNKNOWN_STATE"}) {
            ReturnRecord legacyNull = new ReturnRecord();
            legacyNull.setStoreId(auth.storeId());
            legacyNull.setSaleId(saleId);
            legacyNull.setReturnNumber("RET-" + UUID.randomUUID());
            legacyNull.setStatus(badStatus);
            legacyNull.setTotalRefund(100.0);
            returnRepository.save(legacyNull);
        }
        assertEquals(1000.0, getDashboard(auth.token(), "?days=1").get("todayNetSales").asDouble(), 0.01);

        // Explicitly confirm cash refund -> PENDING -> COMPLETED -> Net Sales becomes 500.0
        mockMvc.perform(post("/api/v1/returns/" + retId + "/confirm")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());
        assertEquals(500.0, getDashboard(auth.token(), "?days=1").get("todayNetSales").asDouble(), 0.01);

        // Concurrent requests for remaining 500.0 (two threads each requesting 400.0 -> 400+400 > 500 remaining)
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Integer> c1 = () -> mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleId\":\"" + saleId + "\",\"idempotencyKey\":\"c1-" + UUID.randomUUID() + "\",\"totalRefund\":400.0,\"refundMethod\":\"CASH\"}"))
                .andReturn().getResponse().getStatus();
        Callable<Integer> c2 = () -> mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleId\":\"" + saleId + "\",\"idempotencyKey\":\"c2-" + UUID.randomUUID() + "\",\"totalRefund\":400.0,\"refundMethod\":\"CASH\"}"))
                .andReturn().getResponse().getStatus();
        List<Future<Integer>> futures = pool.invokeAll(List.of(c1, c2));
        pool.shutdown();
        int sA = futures.get(0).get();
        int sB = futures.get(1).get();
        assertTrue((sA == 200 && sB == 400) || (sA == 400 && sB == 200),
                "Exactly one concurrent over-refund must succeed (200) and one must fail (400), got: " + sA + ", " + sB);
    }

    @Test
    @DisplayName("Phase D: Return-aware historical COGS (Scenarios A, B, C, D)")
    void phaseD_returnAwareHistoricalCogs() throws Exception {
        StoreAuth auth = createIsolatedStore();
        Product p = saveProduct(auth.storeId(), "Coffee", "ACTIVE", 50, 300.0, 500.0);

        // Sale: 2 units * 500 = 1000, cost = 2 * 300 = 600
        Sale saleReq = new Sale();
        saleReq.setSubtotal(1000.0);
        saleReq.setGrandTotal(1000.0);
        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(2);
        item.setUnitPrice(500.0);
        item.setLineTotal(1000.0);
        saleReq.setItems(List.of(item));

        MvcResult sRes = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saleReq)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode sData = objectMapper.readTree(sRes.getResponse().getContentAsString()).get("data");
        String saleId = sData.get("id").asText();
        String saleItemId = sData.get("items").get(0).get("saleItemId").asText();

        // Change current Product.costPrice to 999.0 -> historical calculations must ignore it
        p.setCostPrice(999.0);
        productRepository.save(p);

        // Scenario B: Partial refund 500, 1 unit restocked -> Net Sales = 500, Net COGS = 300, Gross Profit = 200
        MvcResult r1 = mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":500.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":1,\"refundAmount\":500.0,\"restocked\":true}]}",
                                saleId, saleItemId, p.getId())))
                .andExpect(status().isOk())
                .andReturn();
        String r1Id = objectMapper.readTree(r1.getResponse().getContentAsString()).get("data").get("id").asText();
        mockMvc.perform(post("/api/v1/returns/" + r1Id + "/confirm").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        JsonNode dashB = getDashboard(auth.token(), "?days=1");
        assertTrue(dashB.get("cogsAvailable").asBoolean());
        assertEquals(500.0, dashB.get("periodNetSales").asDouble(), 0.01);
        assertEquals(300.0, dashB.get("cogs").asDouble(), 0.01);
        assertEquals(200.0, dashB.get("grossProfit").asDouble(), 0.01);

        // Scenario C: Return remaining 500 with restocked=false -> Net Sales = 0, COGS stays 300, Gross Profit = -300
        MvcResult r2 = mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":500.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":1,\"refundAmount\":500.0,\"restocked\":false}]}",
                                saleId, saleItemId, p.getId())))
                .andExpect(status().isOk())
                .andReturn();
        String r2Id = objectMapper.readTree(r2.getResponse().getContentAsString()).get("data").get("id").asText();
        mockMvc.perform(post("/api/v1/returns/" + r2Id + "/confirm").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        JsonNode dashC = getDashboard(auth.token(), "?days=1");
        assertTrue(dashC.get("cogsAvailable").asBoolean());
        assertEquals(0.0, dashC.get("periodNetSales").asDouble(), 0.01);
        assertEquals(300.0, dashC.get("cogs").asDouble(), 0.01);
        assertEquals(-300.0, dashC.get("grossProfit").asDouble(), 0.01);

        // Scenario D: Monetary COMPLETED return with missing item-level info -> mark cogsAvailable=false
        ReturnRecord rNoItems = new ReturnRecord();
        rNoItems.setStoreId(auth.storeId());
        rNoItems.setSaleId(saleId);
        rNoItems.setReturnNumber("RET-NOITEMS-" + UUID.randomUUID());
        rNoItems.setStatus("COMPLETED");
        rNoItems.setTotalRefund(10.0);
        returnRepository.save(rNoItems);

        JsonNode dashD = getDashboard(auth.token(), "?days=1");
        assertFalse(dashD.get("cogsAvailable").asBoolean());
        assertTrue(dashD.get("cogs").isNull());
    }

    @Test
    @DisplayName("Phase E & F: AOV breakdown metrics, JVM UTC vs IST timezone invariance, invalid date range 400")
    void phaseEF_aovBreakdownAndTimezoneInvariance() throws Exception {
        StoreAuth auth = createIsolatedStore();
        TimeZone orig = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            // 2026-11-26 00:05 IST == 2026-11-25 18:35 UTC
            LocalDate todayIst = LocalDate.now(IST);
            Instant istMidnightPlus5m = ZonedDateTime.of(todayIst.atTime(0, 5), IST).toInstant();
            Instant istPrev2355 = ZonedDateTime.of(todayIst.minusDays(1).atTime(23, 55), IST).toInstant();

            Sale sToday = new Sale();
            sToday.setStoreId(auth.storeId());
            sToday.setReceiptNumber("RCP-IST-TODAY");
            sToday.setGrandTotal(1000.0);
            sToday.setStatus("COMPLETED");
            sToday.setCreatedAt(LocalDateTime.ofInstant(istMidnightPlus5m, IST));
            sToday.setCreatedAtInstant(istMidnightPlus5m);
            saleRepository.save(sToday);

            Sale sYest = new Sale();
            sYest.setStoreId(auth.storeId());
            sYest.setReceiptNumber("RCP-IST-YEST");
            sYest.setGrandTotal(500.0);
            sYest.setStatus("COMPLETED");
            sYest.setCreatedAt(LocalDateTime.ofInstant(istPrev2355, IST));
            sYest.setCreatedAtInstant(istPrev2355);
            saleRepository.save(sYest);

            JsonNode dashUtc = getDashboard(auth.token(), "?days=7");
            assertEquals(1000.0, dashUtc.get("todayNetSales").asDouble(), 0.01);
            assertEquals(1L, dashUtc.get("todayOrdersCount").asLong());
            assertEquals(0L, dashUtc.get("fullyRefundedOrders").asLong());
            assertEquals(0L, dashUtc.get("partiallyRefundedOrders").asLong());

            // Invalid custom range endDate < startDate -> 400
            mockMvc.perform(get("/api/v1/reports/dashboard?startDate=2026-11-20&endDate=2026-11-10")
                            .header("Authorization", "Bearer " + auth.token()))
                    .andExpect(status().isBadRequest());
        } finally {
            TimeZone.setDefault(orig);
        }
    }
}
