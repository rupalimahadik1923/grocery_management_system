package com.grocery.controller;

import com.grocery.model.*;
import com.grocery.repository.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public ReportController(SaleRepository saleRepository,
                            ProductRepository productRepository,
                            InventoryBatchRepository batchRepository,
                            ExpenseRepository expenseRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
        this.expenseRepository = expenseRepository;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardMetrics() {
        LocalDateTime startOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        List<Sale> todaySalesList = saleRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startOfToday, endOfToday);
        double todayTotal = todaySalesList.stream().mapToDouble(Sale::getGrandTotal).sum();

        long totalProducts = productRepository.count();
        long lowStockCount = productRepository.findLowStockProducts().size();

        LocalDate now = LocalDate.now();
        LocalDate in30Days = now.plusDays(30);
        long expiringCount = batchRepository.findByExpiryDateBetweenAndStatus(now, in30Days, "ACTIVE").size();

        double totalRevenue = saleRepository.findAll().stream().mapToDouble(Sale::getGrandTotal).sum();
        double monthExpenses = expenseRepository.findAll().stream().mapToDouble(Expense::getAmount).sum();

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("todaySales", todayTotal);
        metrics.put("todayOrdersCount", todaySalesList.size());
        metrics.put("totalProducts", totalProducts);
        metrics.put("lowStockCount", lowStockCount);
        metrics.put("expiringCount", expiringCount);
        metrics.put("totalRevenue", totalRevenue);
        metrics.put("monthExpenses", monthExpenses);
        metrics.put("recentSales", saleRepository.findTop20ByOrderByCreatedAtDesc());

        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/sales")
    public ResponseEntity<Map<String, Object>> getSalesReport() {
        List<Sale> allSales = saleRepository.findAll();
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
    public ResponseEntity<Map<String, Object>> getProfitLoss() {
        List<Sale> sales = saleRepository.findAll();
        double revenue = sales.stream().mapToDouble(Sale::getGrandTotal).sum();
        double expenses = expenseRepository.findAll().stream().mapToDouble(Expense::getAmount).sum();
        // Estimated COGS (~70% of revenue in retail grocery)
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
    public ResponseEntity<byte[]> exportCsv(@PathVariable String type) {
        StringBuilder csv = new StringBuilder();
        String filename = type + "_export.csv";

        if ("products".equalsIgnoreCase(type)) {
            csv.append("ID,SKU,Barcode,Name,Category,Unit,CostPrice,SellingPrice,Stock,MinStock,Status\n");
            for (Product p : productRepository.findAll()) {
                csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%.2f,%.2f,%.2f,%.2f,\"%s\"\n",
                        p.getId(), p.getSku(), p.getBarcode(), p.getName(), p.getCategoryName(),
                        p.getUnit(), p.getCostPrice(), p.getSellingPrice(), p.getCurrentStock(),
                        p.getMinStockLevel(), p.getStatus()));
            }
        } else if ("sales".equalsIgnoreCase(type)) {
            csv.append("ReceiptNumber,Date,Cashier,CustomerPhone,ItemsCount,Subtotal,Discount,Tax,GrandTotal,PaymentMethod,Status\n");
            for (Sale s : saleRepository.findAll()) {
                csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%d,%.2f,%.2f,%.2f,%.2f,\"%s\",\"%s\"\n",
                        s.getReceiptNumber(), s.getCreatedAt(), s.getCashierName(), s.getCustomerPhone(),
                        s.getItems() != null ? s.getItems().size() : 0, s.getSubtotal(), s.getDiscountAmount(),
                        s.getCgst() + s.getSgst(), s.getGrandTotal(), s.getPaymentMethod(), s.getStatus()));
            }
        } else if ("expenses".equalsIgnoreCase(type)) {
            csv.append("ID,Date,Title,Category,Amount,PaymentMethod,VoucherRef,RecordedBy\n");
            for (Expense e : expenseRepository.findAll()) {
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
