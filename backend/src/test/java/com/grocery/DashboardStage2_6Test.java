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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
class DashboardStage2_6Test {

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
        reg.setStoreName("S26 Store " + s);
        reg.setStoreCode("s26" + s);
        reg.setOwnerName("S26 Owner");
        reg.setOwnerEmail("s26_" + s + "@test.local");
        reg.setCity("Pune");
        reg.setPassword("Pass1234!");
        reg.setAdminUsername("s26adm_" + s);

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

    private Product saveProduct(String storeId, String name, double stock, double costPrice, double sellPrice) {
        Product p = new Product();
        p.setStoreId(storeId);
        p.setName(name);
        p.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 6));
        p.setStatus("ACTIVE");
        p.setCurrentStock(stock);
        p.setMinStockLevel(2);
        p.setCostPrice(costPrice);
        p.setSellingPrice(sellPrice);
        p.setUnit("pcs");
        return productRepository.save(p);
    }

    private JsonNode checkoutSingle(String token, Product p, double qty, double unitPrice) throws Exception {
        Sale saleReq = new Sale();
        saleReq.setSubtotal(qty * unitPrice);
        saleReq.setGrandTotal(qty * unitPrice);
        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(qty);
        item.setUnitPrice(unitPrice);
        item.setLineTotal(qty * unitPrice);
        saleReq.setItems(List.of(item));

        MvcResult res = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saleReq)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
    }

    @Test
    @DisplayName("AUD-26-01 Tests 1-6: In-flight preservation, abandoned recovery, repeated & concurrent reconcile, processReturn race, unsettled COMPLETED")
    void aud26_01_reservationLifecycleAndReconciliation() throws Exception {
        StoreAuth auth = createIsolatedStore();
        Product p = saveProduct(auth.storeId(), "Cashews", 30, 100, 200);
        JsonNode saleData = checkoutSingle(auth.token(), p, 5, 200.0); // 1000.0
        String saleId = saleData.get("id").asText();
        String saleItemId = saleData.get("items").get(0).get("saleItemId").asText();

        // TEST 1: In-flight reservation (status=RESERVED, created just now) MUST NOT be released by reconcile
        Sale sale = saleRepository.findByStoreIdAndId(auth.storeId(), saleId).orElseThrow();
        String inFlightResId = "inflight-" + UUID.randomUUID();
        sale.setPendingRefundTotal(200.0);
        sale.getActiveReservations().put(inFlightResId, 200.0);
        sale.getReservationMetadata().put(inFlightResId,
                new Sale.ReservationMetadata("RESERVED", System.currentTimeMillis(), Map.of()));
        saleRepository.save(sale);

        mockMvc.perform(post("/api/v1/returns/reconcile").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());
        Sale afterInFlight = saleRepository.findByStoreIdAndId(auth.storeId(), saleId).orElseThrow();
        assertTrue(afterInFlight.getActiveReservations().containsKey(inFlightResId));
        assertEquals(200.0, afterInFlight.getPendingRefundTotal(), 0.001);

        // TEST 2 & 3 & 4: Abandoned reservation (age > grace period, no ReturnRecord) -> released once, idempotent across repeated & concurrent reconciles
        String abandonedResId = "abandoned-" + UUID.randomUUID();
        afterInFlight.setPendingRefundTotal(afterInFlight.getPendingRefundTotal() + 300.0);
        afterInFlight.getActiveReservations().put(abandonedResId, 300.0);
        afterInFlight.getReservationMetadata().put(abandonedResId,
                new Sale.ReservationMetadata("RESERVED", System.currentTimeMillis() - 120_000L, Map.of()));
        saleRepository.save(afterInFlight);

        // Concurrent reconcile calls (TEST 4) + repeated reconcile (TEST 3)
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Integer> rec1 = () -> mockMvc.perform(post("/api/v1/returns/reconcile")
                .header("Authorization", "Bearer " + auth.token())).andReturn().getResponse().getStatus();
        Callable<Integer> rec2 = () -> mockMvc.perform(post("/api/v1/returns/reconcile")
                .header("Authorization", "Bearer " + auth.token())).andReturn().getResponse().getStatus();
        List<Future<Integer>> recResults = pool.invokeAll(List.of(rec1, rec2));
        pool.shutdown();
        assertEquals(200, recResults.get(0).get());
        assertEquals(200, recResults.get(1).get());

        // Repeated reconcile
        mockMvc.perform(post("/api/v1/returns/reconcile").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        Sale afterAbandoned = saleRepository.findByStoreIdAndId(auth.storeId(), saleId).orElseThrow();
        assertFalse(afterAbandoned.getActiveReservations().containsKey(abandonedResId));
        assertTrue(afterAbandoned.getActiveReservations().containsKey(inFlightResId), "Unrelated in-flight reservation must remain");
        assertEquals(200.0, afterAbandoned.getPendingRefundTotal(), 0.001);

        // Clean up inFlightResId for TEST 5 & 6
        afterAbandoned.setPendingRefundTotal(0.0);
        afterAbandoned.getActiveReservations().clear();
        afterAbandoned.getReservationMetadata().clear();
        saleRepository.save(afterAbandoned);

        // TEST 5: Concurrent processReturn + reconcile does not prematurely release active reservation
        ExecutorService racePool = Executors.newFixedThreadPool(2);
        Callable<Integer> doReturn = () -> mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":200.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":1,\"refundAmount\":200.0,\"restocked\":true}]}",
                                saleId, saleItemId, p.getId())))
                .andReturn().getResponse().getStatus();
        Callable<Integer> doReconcile = () -> mockMvc.perform(post("/api/v1/returns/reconcile")
                .header("Authorization", "Bearer " + auth.token())).andReturn().getResponse().getStatus();
        List<Future<Integer>> raceRes = racePool.invokeAll(List.of(doReturn, doReconcile));
        racePool.shutdown();
        assertEquals(200, raceRes.get(0).get());
        assertEquals(200, raceRes.get(1).get());
        assertEquals(200.0, saleRepository.findByStoreIdAndId(auth.storeId(), saleId).orElseThrow().getPendingRefundTotal(), 0.001);

        // TEST 6: Completed but unsettled ReturnRecord -> reconcile applies settlement once, releases reservation, updates dashboard
        ReturnRecord pendingRet = returnRepository.findByStoreIdAndSaleId(auth.storeId(), saleId).get(0);
        pendingRet.setStatus("COMPLETED");
        pendingRet.setSaleSettled(false);
        returnRepository.save(pendingRet);

        mockMvc.perform(post("/api/v1/returns/reconcile").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        Sale settledSale = saleRepository.findByStoreIdAndId(auth.storeId(), saleId).orElseThrow();
        ReturnRecord settledRet = returnRepository.findByStoreIdAndId(auth.storeId(), pendingRet.getId()).orElseThrow();
        assertTrue(settledRet.isSaleSettled());
        assertEquals(200.0, settledSale.getRefundedTotal(), 0.001);
        assertEquals(0.0, settledSale.getPendingRefundTotal(), 0.001);
        assertTrue(settledSale.getActiveReservations().isEmpty());

        MvcResult dashRes = mockMvc.perform(get("/api/v1/reports/dashboard?days=1")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(800.0, objectMapper.readTree(dashRes.getResponse().getContentAsString()).get("todayNetSales").asDouble(), 0.01);
    }

    @Test
    @DisplayName("AUD-26-02 Tests 7-14: Item quantity bounds, prior & pending returns, multi-line disambiguation, concurrent item race, invalid saleItemId, tenant isolation")
    void aud26_02_returnQuantityValidationAndConcurrency() throws Exception {
        StoreAuth storeA = createIsolatedStore();
        StoreAuth storeB = createIsolatedStore();
        Product p1 = saveProduct(storeA.storeId(), "Pistachios", 50, 100, 200);

        // TEST 7: Single item (qty 1) -> return qty 2 rejected
        JsonNode sale1 = checkoutSingle(storeA.token(), p1, 1, 200.0);
        String s1Id = sale1.get("id").asText();
        String s1ItemId = sale1.get("items").get(0).get("saleItemId").asText();
        double stockBefore = productRepository.findById(p1.getId()).orElseThrow().getCurrentStock();

        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + storeA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":200.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":2,\"refundAmount\":200.0,\"restocked\":true}]}",
                                s1Id, s1ItemId, p1.getId())))
                .andExpect(status().isBadRequest());
        assertEquals(stockBefore, productRepository.findById(p1.getId()).orElseThrow().getCurrentStock(), 0.001);
        assertEquals(0, returnRepository.findByStoreIdAndSaleId(storeA.storeId(), s1Id).size());

        // TEST 13: Unknown saleItemId & saleItemId belonging to another Sale rejected
        JsonNode sale5 = checkoutSingle(storeA.token(), p1, 5, 200.0); // 1000.0
        String s5Id = sale5.get("id").asText();
        String s5ItemId = sale5.get("items").get(0).get("saleItemId").asText();

        for (String badSaleItemId : List.of("nonexistent-id", s1ItemId)) {
            mockMvc.perform(post("/api/v1/returns")
                            .header("Authorization", "Bearer " + storeA.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"saleId\":\"%s\",\"totalRefund\":200.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":1,\"refundAmount\":200.0}]}",
                                    s5Id, badSaleItemId, p1.getId())))
                    .andExpect(status().isBadRequest());
        }

        // TEST 14: Cross-store tenant isolation
        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + storeB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":200.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":1,\"refundAmount\":200.0}]}",
                                s5Id, s5ItemId, p1.getId())))
                .andExpect(status().isBadRequest());

        // TEST 8 & 9 & 10: SaleItem 5 units -> return 2 (PENDING), verify qty 4 rejected (TEST 9); confirm 2, verify qty 4 still rejected (TEST 8); return 3 succeeds (TEST 10)
        MvcResult r2 = mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + storeA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":400.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":2,\"refundAmount\":400.0,\"restocked\":true}]}",
                                s5Id, s5ItemId, p1.getId())))
                .andExpect(status().isOk())
                .andReturn();
        String r2Id = objectMapper.readTree(r2.getResponse().getContentAsString()).get("data").get("id").asText();

        // TEST 9: While 2 units are PENDING, requesting 4 must fail
        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + storeA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":600.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":4,\"refundAmount\":600.0}]}",
                                s5Id, s5ItemId, p1.getId())))
                .andExpect(status().isBadRequest());

        // Confirm the 2 units -> COMPLETED
        mockMvc.perform(post("/api/v1/returns/" + r2Id + "/confirm").header("Authorization", "Bearer " + storeA.token()))
                .andExpect(status().isOk());

        // TEST 8: After 2 units COMPLETED, requesting 4 must fail
        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + storeA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":600.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":4,\"refundAmount\":600.0}]}",
                                s5Id, s5ItemId, p1.getId())))
                .andExpect(status().isBadRequest());

        // TEST 10: Requesting remaining 3 units succeeds
        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + storeA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":600.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":3,\"refundAmount\":600.0,\"restocked\":true}]}",
                                s5Id, s5ItemId, p1.getId())))
                .andExpect(status().isOk());

        // TEST 11: Duplicate product across two sale lines (qty 2 and qty 3)
        Sale mlSale = new Sale();
        mlSale.setSubtotal(1000.0);
        mlSale.setGrandTotal(1000.0);
        Sale.SaleItem lineA = new Sale.SaleItem();
        lineA.setProductId(p1.getId());
        lineA.setProductName(p1.getName());
        lineA.setQuantity(2);
        lineA.setUnitPrice(200.0);
        lineA.setLineTotal(400.0);
        Sale.SaleItem lineB = new Sale.SaleItem();
        lineB.setProductId(p1.getId());
        lineB.setProductName(p1.getName());
        lineB.setQuantity(3);
        lineB.setUnitPrice(200.0);
        lineB.setLineTotal(600.0);
        mlSale.setItems(List.of(lineA, lineB));
        MvcResult mlRes = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + storeA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mlSale)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode mlData = objectMapper.readTree(mlRes.getResponse().getContentAsString()).get("data");
        String mlSaleId = mlData.get("id").asText();
        String lineAId = mlData.get("items").get(0).get("saleItemId").asText();
        String lineBId = mlData.get("items").get(1).get("saleItemId").asText();

        // Ambiguous productId without saleItemId must be rejected
        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + storeA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":200.0,\"items\":[{\"productId\":\"%s\",\"quantity\":1,\"refundAmount\":200.0}]}",
                                mlSaleId, p1.getId())))
                .andExpect(status().isBadRequest());

        // Returning 2 from lineA and 3 from lineB succeeds
        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + storeA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":1000.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":2,\"refundAmount\":400.0,\"restocked\":true},{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":3,\"refundAmount\":600.0,\"restocked\":true}]}",
                                mlSaleId, lineAId, p1.getId(), lineBId, p1.getId())))
                .andExpect(status().isOk());

        // TEST 12: Concurrent return requests (5 units sold, 2 already returned -> 3 remaining; 2 concurrent requests for 2 units each)
        JsonNode saleConc = checkoutSingle(storeA.token(), p1, 5, 200.0);
        String scId = saleConc.get("id").asText();
        String scItemId = saleConc.get("items").get(0).get("saleItemId").asText();
        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + storeA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":400.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":2,\"refundAmount\":400.0,\"restocked\":true}]}",
                                scId, scItemId, p1.getId())))
                .andExpect(status().isOk());

        ExecutorService concPool = Executors.newFixedThreadPool(2);
        String concPayload = String.format(
                "{\"saleId\":\"%s\",\"totalRefund\":400.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":2,\"refundAmount\":400.0,\"restocked\":true}]}",
                scId, scItemId, p1.getId());
        Callable<Integer> t1 = () -> mockMvc.perform(post("/api/v1/returns")
                .header("Authorization", "Bearer " + storeA.token())
                .contentType(MediaType.APPLICATION_JSON).content(concPayload)).andReturn().getResponse().getStatus();
        Callable<Integer> t2 = () -> mockMvc.perform(post("/api/v1/returns")
                .header("Authorization", "Bearer " + storeA.token())
                .contentType(MediaType.APPLICATION_JSON).content(concPayload)).andReturn().getResponse().getStatus();
        List<Future<Integer>> concRes = concPool.invokeAll(List.of(t1, t2));
        concPool.shutdown();
        int rA = concRes.get(0).get();
        int rB = concRes.get(1).get();
        assertTrue((rA == 200 && rB == 400) || (rA == 400 && rB == 200),
                "Exactly one concurrent item-quantity return must succeed (200) and one fail (400), got: " + rA + ", " + rB);
        Sale scAfter = saleRepository.findByStoreIdAndId(storeA.storeId(), scId).orElseThrow();
        assertTrue(scAfter.getItemPendingReturnQty().get(scItemId) <= 4.001);
    }
}
