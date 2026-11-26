package com.grocery.controller;

import com.grocery.dto.DashboardMetricsResponse;
import com.grocery.model.*;
import com.grocery.repository.*;
import com.grocery.security.TenantContext;
import com.grocery.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final InventoryBatchRepository batchRepository;
    private final ExpenseRepository expenseRepository;
    private final TenantContext tenantContext;
    private final DashboardService dashboardService;

    public ReportController(SaleRepository saleRepository,
                            ProductRepository productRepository,
                            InventoryBatchRepository batchRepository,
                            ExpenseRepository expenseRepository,
                            TenantContext tenantContext,
                            DashboardService dashboardService) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.expenseRepository = expenseRepository;
        this.tenantContext = tenantContext;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','INVENTORY_STAFF')")
    public ResponseEntity<?> getDashboardMetrics(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "7") int days) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            return ResponseEntity.badRequest()
                    .body(com.grocery.dto.ApiResponse.error("endDate cannot be earlier than startDate"));
        }
        String storeId = tenantContext.requireStoreId(userDetails);
        return ResponseEntity.ok(dashboardService.getDashboardMetrics(storeId, startDate, endDate, days));
    }

    @GetMapping("/sales")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<Map<String, Object>> getSalesReport(@AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        List<Sale> allSales = saleRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        double totalRevenue = allSales.stream().mapToDouble(Sale::getGrandTotal).sum();
        double totalDiscount = allSales.stream().mapToDouble(Sale::getDiscountAmount).sum();
        double totalTax = allSales.stream().mapToDouble(s -> s.getCgst() + s.getSgst()).sum();

        Map<String, Object> res = new HashMap<>();
        res.put("totalRevenue", totalRevenue);
        res.put("totalDiscount", totalDiscount);
        res.put("totalTax", totalTax);
        res.put("totalOrders", allSales.size());
        res.put("sales", allSales);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/profit-loss")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<Map<String, Object>> getProfitLoss(@AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        List<Sale> sales = saleRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        double revenue = sales.stream().mapToDouble(Sale::getGrandTotal).sum();
        double expenses = expenseRepository.findByStoreIdOrderByExpenseDateDesc(storeId).stream().mapToDouble(Expense::getAmount).sum();
        double cogs = revenue * 0.70;
        double grossProfit = revenue - cogs;
        double netProfit = grossProfit - expenses;

        Map<String, Object> pnl = new HashMap<>();
        pnl.put("revenue", revenue);
        pnl.put("cogs", cogs);
        pnl.put("grossProfit", grossProfit);
        pnl.put("grossMarginPercent", revenue > 0 ? (grossProfit / revenue) * 100 : 0);
        pnl.put("expenses", expenses);
        pnl.put("netProfit", netProfit);
        pnl.put("netMarginPercent", revenue > 0 ? (netProfit / revenue) * 100 : 0);
        return ResponseEntity.ok(pnl);
    }

    @GetMapping("/export/{type}")
    @PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable String type,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        StringBuilder csv = new StringBuilder();
        String filename = type + "_export.csv";

        if ("products".equalsIgnoreCase(type)) {
            csv.append("ID,SKU,Barcode,Name,Category,Unit,CostPrice,SellingPrice,Stock,MinStock,Status\n");
            for (Product p : productRepository.findByStoreId(storeId)) {
                csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%.2f,%.2f,%.2f,%.2f,\"%s\"\n",
                        p.getId(), p.getSku(), p.getBarcode(), p.getName(), p.getCategoryName(),
                        p.getUnit(), p.getCostPrice(), p.getSellingPrice(), p.getCurrentStock(),
                        p.getMinStockLevel(), p.getStatus()));
            }
        } else if ("sales".equalsIgnoreCase(type)) {
            csv.append("ReceiptNumber,Date,Cashier,CustomerPhone,ItemsCount,Subtotal,Discount,Tax,GrandTotal,PaymentMethod,Status\n");
            for (Sale s : saleRepository.findByStoreIdOrderByCreatedAtDesc(storeId)) {
                csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%d,%.2f,%.2f,%.2f,%.2f,\"%s\",\"%s\"\n",
                        s.getReceiptNumber(), s.getCreatedAt(), s.getCashierName(), s.getCustomerPhone(),
                        s.getItems() != null ? s.getItems().size() : 0, s.getSubtotal(), s.getDiscountAmount(),
                        s.getCgst() + s.getSgst(), s.getGrandTotal(), s.getPaymentMethod(), s.getStatus()));
            }
        } else if ("expenses".equalsIgnoreCase(type)) {
            csv.append("ID,Date,Title,Category,Amount,PaymentMethod,VoucherRef,RecordedBy\n");
            for (Expense e : expenseRepository.findByStoreIdOrderByExpenseDateDesc(storeId)) {
                csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%.2f,\"%s\",\"%s\",\"%s\"\n",
                        e.getId(), e.getExpenseDate(), e.getTitle(), e.getCategory(),
                        e.getAmount(), e.getPaymentMethod(), e.getVoucherRef(), e.getRecordedBy()));
            }
        }

        byte[] bytes = csv.toString().getBytes();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }
}
