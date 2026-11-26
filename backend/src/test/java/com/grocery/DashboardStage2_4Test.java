package com.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocery.controller.SaleController;
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
class DashboardStage2_4Test {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SaleController saleController;
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
        reg.setStoreName("Rec Store " + s);
        reg.setStoreCode("rec" + s);
        reg.setOwnerName("Rec Owner");
        reg.setOwnerEmail("rec_" + s + "@test.local");
        reg.setCity("Pune");
        reg.setPassword("Pass1234!");
        reg.setAdminUsername("recadm_" + s);

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
        p.setMinStockLevel(5);
        p.setCostPrice(costPrice);
        p.setSellingPrice(sellPrice);
        p.setUnit("pcs");
        return productRepository.save(p);
    }

    private JsonNode checkout(String token, Product p, double qty, double grandTotal) throws Exception {
        Sale saleReq = new Sale();
        saleReq.setSubtotal(grandTotal);
        saleReq.setGrandTotal(grandTotal);
        Sale.SaleItem item = new Sale.SaleItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(qty);
        item.setUnitPrice(grandTotal / qty);
        item.setLineTotal(grandTotal);
        saleReq.setItems(List.of(item));

        MvcResult res = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saleReq)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("data");
    }

    private JsonNode getDashboard(String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/reports/dashboard?days=1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("AUD-24-01: Orphaned reservation recovery, idempotent release, and live-reservation preservation")
    void reservationFailureRecoveryAndIdempotentRelease() throws Exception {
        StoreAuth auth = createIsolatedStore();
        Product p = saveProduct(auth.storeId(), "Dates", 20, 200, 500);
        String saleId = checkout(auth.token(), p, 2, 1000.0).get("id").asText();

        // Simulate a stranded reservation (Sale reserved 400.0, ReturnRecord save crashed before persisting)
        String orphanResId = "orphan-" + UUID.randomUUID();
        Sale sale = saleRepository.findByStoreIdAndId(auth.storeId(), saleId).orElseThrow();
        sale.setPendingRefundTotal(400.0);
        sale.getActiveReservations().put(orphanResId, 400.0);
        saleRepository.save(sale);

        // Also create a real PENDING return whose reservation MUST be preserved by reconciliation
        mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleId\":\"" + saleId + "\",\"totalRefund\":300.0,\"refundMethod\":\"CASH\"}"))
                .andExpect(status().isOk());

        // Reconcile releases orphan (400.0) while preserving live PENDING reservation (300.0)
        mockMvc.perform(post("/api/v1/returns/reconcile")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        Sale afterReconcile = saleRepository.findByStoreIdAndId(auth.storeId(), saleId).orElseThrow();
        assertEquals(300.0, afterReconcile.getPendingRefundTotal(), 0.001);
        assertFalse(afterReconcile.getActiveReservations().containsKey(orphanResId));
        assertEquals(1, afterReconcile.getActiveReservations().size());

        // Second reconcile call is idempotent and does not double-release pendingRefundTotal
        mockMvc.perform(post("/api/v1/returns/reconcile")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());
        assertEquals(300.0, saleRepository.findByStoreIdAndId(auth.storeId(), saleId).orElseThrow().getPendingRefundTotal(), 0.001);
    }

    @Test
    @DisplayName("AUD-24-02: Crash between ReturnRecord COMPLETED and Sale settlement, idempotent recovery, and concurrent /confirm")
    void settlementCrashRecoveryAndConcurrentConfirm() throws Exception {
        StoreAuth auth = createIsolatedStore();
        Product p = saveProduct(auth.storeId(), "Honey", 20, 300, 500);
        JsonNode saleData = checkout(auth.token(), p, 2, 1000.0);
        String saleId = saleData.get("id").asText();
        String saleItemId = saleData.get("items").get(0).get("saleItemId").asText();

        // Create PENDING return of 500.0
        MvcResult retRes = mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":500.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":1,\"refundAmount\":500.0,\"restocked\":true}]}",
                                saleId, saleItemId, p.getId())))
                .andExpect(status().isOk())
                .andReturn();
        String retId = objectMapper.readTree(retRes.getResponse().getContentAsString()).get("data").get("id").asText();

        // Simulate crash mid-confirm: ReturnRecord became COMPLETED with saleSettled=false, Sale untouched
        ReturnRecord crashed = returnRepository.findByStoreIdAndId(auth.storeId(), retId).orElseThrow();
        crashed.setStatus("COMPLETED");
        crashed.setSaleSettled(false);
        returnRepository.save(crashed);

        // Dashboard MUST NOT treat unsettled return as a completed financial refund
        assertEquals(1000.0, getDashboard(auth.token()).get("todayNetSales").asDouble(), 0.01);

        // Reconcile completes settlement idempotently
        mockMvc.perform(post("/api/v1/returns/reconcile")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());
        // Repeated reconcile & /confirm must not double-apply
        mockMvc.perform(post("/api/v1/returns/reconcile")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/returns/" + retId + "/confirm")
                        .header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        Sale settledSale = saleRepository.findByStoreIdAndId(auth.storeId(), saleId).orElseThrow();
        assertEquals(500.0, settledSale.getRefundedTotal(), 0.001);
        assertEquals(0.0, settledSale.getPendingRefundTotal(), 0.001);
        assertEquals("PARTIALLY_RETURNED", settledSale.getStatus());
        assertTrue(returnRepository.findByStoreIdAndId(auth.storeId(), retId).orElseThrow().isSaleSettled());
        assertEquals(500.0, getDashboard(auth.token()).get("todayNetSales").asDouble(), 0.01);

        // Concurrent /confirm on a second PENDING return (500.0) -> refundedTotal must become 1000.0, never 1500.0
        MvcResult ret2Res = mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":500.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":1,\"refundAmount\":500.0,\"restocked\":true}]}",
                                saleId, saleItemId, p.getId())))
                .andExpect(status().isOk())
                .andReturn();
        String ret2Id = objectMapper.readTree(ret2Res.getResponse().getContentAsString()).get("data").get("id").asText();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Integer> c1 = () -> mockMvc.perform(post("/api/v1/returns/" + ret2Id + "/confirm")
                .header("Authorization", "Bearer " + auth.token())).andReturn().getResponse().getStatus();
        Callable<Integer> c2 = () -> mockMvc.perform(post("/api/v1/returns/" + ret2Id + "/confirm")
                .header("Authorization", "Bearer " + auth.token())).andReturn().getResponse().getStatus();
        List<Future<Integer>> results = pool.invokeAll(List.of(c1, c2));
        pool.shutdown();
        assertEquals(200, results.get(0).get());
        assertEquals(200, results.get(1).get());

        Sale finalSale = saleRepository.findByStoreIdAndId(auth.storeId(), saleId).orElseThrow();
        assertEquals(1000.0, finalSale.getRefundedTotal(), 0.001);
        assertEquals(0.0, finalSale.getPendingRefundTotal(), 0.001);
        assertEquals("REFUNDED", finalSale.getStatus());
    }

    @Test
    @DisplayName("Stage 2.3 Gaps: Scenario A (Full Refund + Full Restock), /fail, /reject, and multi-line saleItemId COGS")
    void scenarioAFullRefundFailRejectAndMultiLineCogs() throws Exception {
        StoreAuth auth = createIsolatedStore();
        Product p = saveProduct(auth.storeId(), "Saffron", 20, 600, 1000);

        // Scenario A: Sale 1000, Cost 600 -> Full return restocked=true + /confirm -> Net Sales=0, COGS=0, Gross Profit=0
        JsonNode saleA = checkout(auth.token(), p, 1, 1000.0);
        String saleAId = saleA.get("id").asText();
        String saleAItemId = saleA.get("items").get(0).get("saleItemId").asText();

        // First test /fail and /reject release pendingRefundTotal and block later /confirm
        MvcResult rFail = mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleId\":\"" + saleAId + "\",\"totalRefund\":200.0}"))
                .andExpect(status().isOk())
                .andReturn();
        String rFailId = objectMapper.readTree(rFail.getResponse().getContentAsString()).get("data").get("id").asText();
        mockMvc.perform(post("/api/v1/returns/" + rFailId + "/fail").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/returns/" + rFailId + "/confirm").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isBadRequest());
        assertEquals(0.0, saleRepository.findByStoreIdAndId(auth.storeId(), saleAId).orElseThrow().getPendingRefundTotal(), 0.001);

        MvcResult rReject = mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleId\":\"" + saleAId + "\",\"totalRefund\":200.0}"))
                .andExpect(status().isOk())
                .andReturn();
        String rRejectId = objectMapper.readTree(rReject.getResponse().getContentAsString()).get("data").get("id").asText();
        mockMvc.perform(post("/api/v1/returns/" + rRejectId + "/reject").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());
        assertEquals(0.0, saleRepository.findByStoreIdAndId(auth.storeId(), saleAId).orElseThrow().getPendingRefundTotal(), 0.001);

        // Full refund + full restock
        MvcResult rFull = mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":1000.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":1,\"refundAmount\":1000.0,\"restocked\":true}]}",
                                saleAId, saleAItemId, p.getId())))
                .andExpect(status().isOk())
                .andReturn();
        String rFullId = objectMapper.readTree(rFull.getResponse().getContentAsString()).get("data").get("id").asText();
        mockMvc.perform(post("/api/v1/returns/" + rFullId + "/confirm").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        JsonNode dashA = getDashboard(auth.token());
        assertEquals(0.0, dashA.get("todayNetSales").asDouble(), 0.01);
        assertEquals(0.0, dashA.get("cogs").asDouble(), 0.01);
        assertEquals(0.0, dashA.get("grossProfit").asDouble(), 0.01);
        assertEquals(1L, dashA.get("fullyRefundedOrders").asLong());

        // Multi-line same productId disambiguated by saleItemId
        Sale multiLine = new Sale();
        multiLine.setSubtotal(3000.0);
        multiLine.setGrandTotal(3000.0);
        Sale.SaleItem l1 = new Sale.SaleItem();
        l1.setProductId(p.getId());
        l1.setProductName(p.getName());
        l1.setQuantity(1);
        l1.setUnitPrice(1000.0);
        l1.setLineTotal(1000.0);
        Sale.SaleItem l2 = new Sale.SaleItem();
        l2.setProductId(p.getId());
        l2.setProductName(p.getName());
        l2.setQuantity(2);
        l2.setUnitPrice(1000.0);
        l2.setLineTotal(2000.0);
        multiLine.setItems(List.of(l1, l2));

        MvcResult mlRes = mockMvc.perform(post("/api/v1/sales")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(multiLine)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode mlData = objectMapper.readTree(mlRes.getResponse().getContentAsString()).get("data");
        String mlSaleId = mlData.get("id").asText();
        String mlLine2Id = mlData.get("items").get(1).get("saleItemId").asText();

        MvcResult rMl = mockMvc.perform(post("/api/v1/returns")
                        .header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"saleId\":\"%s\",\"totalRefund\":1000.0,\"items\":[{\"saleItemId\":\"%s\",\"productId\":\"%s\",\"quantity\":1,\"refundAmount\":1000.0,\"restocked\":true}]}",
                                mlSaleId, mlLine2Id, p.getId())))
                .andExpect(status().isOk())
                .andReturn();
        String rMlId = objectMapper.readTree(rMl.getResponse().getContentAsString()).get("data").get("id").asText();
        mockMvc.perform(post("/api/v1/returns/" + rMlId + "/confirm").header("Authorization", "Bearer " + auth.token()))
                .andExpect(status().isOk());

        JsonNode dashMl = getDashboard(auth.token());
        assertTrue(dashMl.get("cogsAvailable").asBoolean());
        assertEquals(2000.0, dashMl.get("todayNetSales").asDouble(), 0.01);
        assertEquals(1200.0, dashMl.get("cogs").asDouble(), 0.01);
        assertEquals(800.0, dashMl.get("grossProfit").asDouble(), 0.01);
    }
}
