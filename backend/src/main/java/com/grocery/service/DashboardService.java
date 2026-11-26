package com.grocery.service;

import com.grocery.dto.DashboardMetricsResponse;
import com.grocery.dto.DashboardMetricsResponse.DashboardAlertItem;
import com.grocery.dto.DashboardMetricsResponse.RecentSaleItem;
import com.grocery.dto.DashboardMetricsResponse.SalesTrendPoint;
import com.grocery.dto.DashboardMetricsResponse.TopProductItem;
import com.grocery.model.Expense;
import com.grocery.model.InventoryBatch;
import com.grocery.model.Notification;
import com.grocery.model.Product;
import com.grocery.model.ReturnRecord;
import com.grocery.model.Sale;
import com.grocery.repository.ExpenseRepository;
import com.grocery.repository.InventoryBatchRepository;
import com.grocery.repository.NotificationRepository;
import com.grocery.repository.ProductRepository;
import com.grocery.repository.ReturnRepository;
import com.grocery.repository.SaleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final SaleRepository saleRepository;
    private final ReturnRepository returnRepository;
    private final ProductRepository productRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final ExpenseRepository expenseRepository;
    private final NotificationRepository notificationRepository;

    public DashboardService(
            SaleRepository saleRepository,
            ReturnRepository returnRepository,
            ProductRepository productRepository,
            InventoryBatchRepository inventoryBatchRepository,
            ExpenseRepository expenseRepository,
            NotificationRepository notificationRepository) {
        this.saleRepository = saleRepository;
        this.returnRepository = returnRepository;
        this.productRepository = productRepository;
        this.inventoryBatchRepository = inventoryBatchRepository;
        this.expenseRepository = expenseRepository;
        this.notificationRepository = notificationRepository;
    }

    public DashboardMetricsResponse getDashboardMetrics(
            String storeId,
            LocalDate startDate,
            LocalDate endDate,
            int days) {
        LocalDate today = LocalDate.now(IST);
        int safeDays = days > 0 ? Math.min(days, 365) : 7;

        LocalDate periodStart = startDate != null ? startDate : today.minusDays(safeDays - 1L);
        LocalDate periodEnd = endDate != null ? endDate : today;
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("endDate cannot be earlier than startDate");
        }

        LocalDateTime todayStartDt = today.atStartOfDay();
        LocalDateTime tomorrowStartDt = today.plusDays(1).atStartOfDay();
        Instant todayStartInst = todayStartDt.atZone(IST).toInstant();
        Instant tomorrowStartInst = tomorrowStartDt.atZone(IST).toInstant();

        LocalDateTime periodStartDt = periodStart.atStartOfDay();
        LocalDateTime periodEndExclDt = periodEnd.plusDays(1).atStartOfDay();
        Instant periodStartInst = periodStartDt.atZone(IST).toInstant();
        Instant periodEndExclInst = periodEndExclDt.atZone(IST).toInstant();

        // Today sales & refunds
        List<Sale> todayEligibleSales = filterEligibleSales(
                saleRepository.findByStoreIdAndIstRange(
                        storeId, todayStartInst, tomorrowStartInst, todayStartDt, tomorrowStartDt));
        Map<String, List<ReturnRecord>> todayReturnsBySale = loadCompletedReturnsBySale(storeId, todayEligibleSales);
        SalesSummary todaySummary = summarizeSales(todayEligibleSales, todayReturnsBySale);

        // Period sales & refunds
        List<Sale> periodEligibleSales = filterEligibleSales(
                saleRepository.findByStoreIdAndIstRange(
                        storeId, periodStartInst, periodEndExclInst, periodStartDt, periodEndExclDt));
        Map<String, List<ReturnRecord>> periodReturnsBySale = loadCompletedReturnsBySale(storeId, periodEligibleSales);
        SalesSummary periodSummary = summarizeSales(periodEligibleSales, periodReturnsBySale);

        // Expenses: current Asia/Kolkata calendar month and selected period
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate nextMonthStart = monthStart.plusMonths(1);
        double monthExpenses = round2(expenseRepository
                .findByStoreIdAndExpenseDateGreaterThanEqualAndExpenseDateLessThanOrderByExpenseDateDesc(
                        storeId, monthStart, nextMonthStart)
                .stream()
                .mapToDouble(Expense::getAmount)
                .sum());

        double periodExpenses = round2(expenseRepository
                .findByStoreIdAndExpenseDateGreaterThanEqualAndExpenseDateLessThanOrderByExpenseDateDesc(
                        storeId, periodStart, periodEnd.plusDays(1))
                .stream()
                .mapToDouble(Expense::getAmount)
                .sum());

        // Historical COGS & Profit (strictly from SaleItem.costPrice snapshots, adjusted for restocked returns)
        CogsResult cogsResult = computeCogs(periodEligibleSales, periodReturnsBySale);

        // Products & stock counts (ACTIVE products only)
        List<Product> activeProducts = productRepository.findByStoreIdAndStatus(storeId, "ACTIVE");
        long totalProducts = activeProducts.size();
        long outOfStockCount = activeProducts.stream()
                .filter(p -> p.getCurrentStock() <= 0)
                .count();
        long lowStockCount = activeProducts.stream()
                .filter(p -> p.getCurrentStock() > 0 && p.getCurrentStock() <= p.getMinStockLevel())
                .count();

        // Expiring batches within next 7 days
        List<InventoryBatch> expiringBatches = inventoryBatchRepository
                .findByStoreIdAndExpiryDateBetweenAndStatus(storeId, today, today.plusDays(7), "ACTIVE");

        DashboardMetricsResponse response = new DashboardMetricsResponse();
        response.setTodayGrossSales(todaySummary.grossSales);
        response.setTodayNetSales(todaySummary.netSales);
        response.setTodaySales(todaySummary.netSales);
        response.setTodayOrdersCount(todaySummary.ordersCount);
        response.setFullyRefundedOrders(todaySummary.fullyRefundedOrders);
        response.setPartiallyRefundedOrders(todaySummary.partiallyRefundedOrders);
        response.setAov(todaySummary.ordersCount > 0 ? round2(todaySummary.netSales / todaySummary.ordersCount) : 0.0);

        response.setPeriodStartDate(periodStart);
        response.setPeriodEndDate(periodEnd);
        response.setPeriodGrossSales(periodSummary.grossSales);
        response.setPeriodNetSales(periodSummary.netSales);
        response.setPeriodOrdersCount(periodSummary.ordersCount);
        response.setPeriodAov(periodSummary.ordersCount > 0 ? round2(periodSummary.netSales / periodSummary.ordersCount) : 0.0);
        response.setTotalRevenue(periodSummary.netSales);

        response.setMonthExpenses(monthExpenses);
        response.setPeriodExpenses(periodExpenses);

        response.setCogsAvailable(cogsResult.available);
        if (cogsResult.available) {
            double cogs = round2(cogsResult.totalCogs);
            double grossProfit = round2(periodSummary.netSales - cogs);
            double netProfit = round2(grossProfit - periodExpenses);
            response.setCogs(cogs);
            response.setGrossProfit(grossProfit);
            response.setNetProfit(netProfit);
        } else {
            response.setCogs(null);
            response.setGrossProfit(null);
            response.setNetProfit(null);
            response.setCogsUnavailableReason(cogsResult.reason);
        }

        response.setTotalProducts(totalProducts);
        response.setOutOfStockCount(outOfStockCount);
        response.setLowStockCount(lowStockCount);
        response.setExpiringCount(expiringBatches.size());

        response.setRecentSales(buildRecentSales(storeId));
        response.setTopProducts(buildTopProducts(periodEligibleSales, periodReturnsBySale));
        response.setSalesTrend(buildSalesTrend(periodStart, periodEnd, periodEligibleSales, periodReturnsBySale));
        response.setAlerts(buildAlerts(storeId, activeProducts, expiringBatches));

        return response;
    }

    private List<Sale> filterEligibleSales(List<Sale> sales) {
        return sales.stream()
                .filter(s -> {
                    String st = s.getStatus();
                    return st == null || (!"CANCELLED".equalsIgnoreCase(st) && !"VOIDED".equalsIgnoreCase(st));
                })
                .collect(Collectors.toList());
    }

    private Map<String, List<ReturnRecord>> loadCompletedReturnsBySale(String storeId, List<Sale> sales) {
        List<String> saleIds = sales.stream()
                .map(Sale::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (saleIds.isEmpty()) {
            return Map.of();
        }
        List<ReturnRecord> returns = returnRepository.findByStoreIdAndSaleIdIn(storeId, saleIds);
        Map<String, List<ReturnRecord>> grouped = new HashMap<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (ReturnRecord r : returns) {
            if (r.getSaleId() == null) continue;
            // Never treat null or unknown status as COMPLETED
            if (!"COMPLETED".equalsIgnoreCase(r.getStatus())) continue;
            // Never treat an incompletely settled return (reservationId present, saleSettled=false) as settled
            if (r.getReservationId() != null && !r.isSaleSettled()) continue;
            String dedupKey = r.getId() != null ? r.getId() : (r.getReturnNumber() + ":" + r.getSaleId());
            if (!seenIds.add(dedupKey)) continue;
            grouped.computeIfAbsent(r.getSaleId(), k -> new ArrayList<>()).add(r);
        }
        return grouped;
    }

    private SalesSummary summarizeSales(List<Sale> sales, Map<String, List<ReturnRecord>> returnsBySale) {
        double gross = 0.0;
        double refunds = 0.0;
        long fullyRefunded = 0;
        long partiallyRefunded = 0;
        for (Sale sale : sales) {
            double saleGross = Math.max(0.0, sale.getGrandTotal());
            gross += saleGross;
            double saleRefund = computeCappedSaleRefund(sale, returnsBySale.get(sale.getId()));
            refunds += saleRefund;
            if (saleRefund > 0) {
                if (saleRefund >= saleGross - 0.01) {
                    fullyRefunded++;
                } else {
                    partiallyRefunded++;
                }
            }
        }
        SalesSummary summary = new SalesSummary();
        summary.grossSales = round2(gross);
        summary.netSales = round2(Math.max(0.0, gross - refunds));
        summary.ordersCount = sales.size();
        summary.fullyRefundedOrders = fullyRefunded;
        summary.partiallyRefundedOrders = partiallyRefunded;
        return summary;
    }

    private double computeCappedSaleRefund(Sale sale, List<ReturnRecord> returns) {
        if (returns == null || returns.isEmpty()) return 0.0;
        double rawRefund = returns.stream().mapToDouble(r -> Math.max(0.0, r.getTotalRefund())).sum();
        return Math.min(Math.max(0.0, sale.getGrandTotal()), rawRefund);
    }

    private CogsResult computeCogs(List<Sale> sales, Map<String, List<ReturnRecord>> returnsBySale) {
        double totalCogs = 0.0;
        for (Sale sale : sales) {
            if (sale.getItems() == null) continue;
            for (Sale.SaleItem item : sale.getItems()) {
                if (item.getCostPrice() == null) {
                    return CogsResult.unavailable("Missing historical cost snapshot on one or more sold items");
                }
            }

            Map<String, Double> restockedBySaleItemId = new HashMap<>();
            Map<String, Double> restockedByProductId = new HashMap<>();
            List<ReturnRecord> saleReturns = returnsBySale.get(sale.getId());
            if (saleReturns != null) {
                for (ReturnRecord ret : saleReturns) {
                    if (ret.getItems() == null || ret.getItems().isEmpty()) {
                        return CogsResult.unavailable("Missing item-level return details on completed refund");
                    }
                    for (ReturnRecord.ReturnItem rItem : ret.getItems()) {
                        if (!rItem.isRestocked() || rItem.getQuantity() <= 0) continue;
                        if (rItem.getSaleItemId() != null && !rItem.getSaleItemId().isBlank()) {
                            restockedBySaleItemId.merge(rItem.getSaleItemId(), rItem.getQuantity(), Double::sum);
                        } else if (rItem.getProductId() != null && !rItem.getProductId().isBlank()) {
                            long matchingLines = sale.getItems().stream()
                                    .filter(si -> rItem.getProductId().equals(si.getProductId()))
                                    .count();
                            if (matchingLines != 1) {
                                return CogsResult.unavailable("Ambiguous return item without saleItemId for multi-line product");
                            }
                            restockedByProductId.merge(rItem.getProductId(), rItem.getQuantity(), Double::sum);
                        } else {
                            return CogsResult.unavailable("Return item missing saleItemId and productId");
                        }
                    }
                }
            }

            for (Sale.SaleItem item : sale.getItems()) {
                double restockedQty = 0.0;
                if (item.getSaleItemId() != null && restockedBySaleItemId.containsKey(item.getSaleItemId())) {
                    restockedQty = restockedBySaleItemId.get(item.getSaleItemId());
                } else if (item.getProductId() != null && restockedByProductId.containsKey(item.getProductId())) {
                    restockedQty = restockedByProductId.get(item.getProductId());
                }
                double netSoldQty = Math.max(0.0, item.getQuantity() - Math.min(item.getQuantity(), restockedQty));
                totalCogs += item.getCostPrice() * netSoldQty;
            }
        }
        return CogsResult.available(totalCogs);
    }

    private List<RecentSaleItem> buildRecentSales(String storeId) {
        return filterEligibleSales(saleRepository.findByStoreIdOrderByCreatedAtDesc(storeId)).stream()
                .limit(5)
                .map(s -> {
                    RecentSaleItem dto = new RecentSaleItem();
                    dto.setId(s.getId());
                    dto.setReceiptNumber(s.getReceiptNumber());
                    dto.setCustomerName(s.getCustomerName() != null && !s.getCustomerName().isBlank() ? s.getCustomerName() : "Walk-in Customer");
                    dto.setGrandTotal(round2(s.getGrandTotal()));
                    dto.setStatus(s.getStatus() != null ? s.getStatus() : "COMPLETED");
                    dto.setPaymentMethod(s.getPaymentMethod());
                    dto.setCreatedAt(s.getCreatedAt());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<TopProductItem> buildTopProducts(List<Sale> sales, Map<String, List<ReturnRecord>> returnsBySale) {
        Map<String, TopProductItem> byKey = new HashMap<>();
        for (Sale sale : sales) {
            if (sale.getItems() == null) continue;
            for (Sale.SaleItem item : sale.getItems()) {
                String key = item.getProductId() != null && !item.getProductId().isBlank()
                        ? item.getProductId()
                        : ("name:" + (item.getProductName() != null ? item.getProductName() : "Unknown"));
                TopProductItem agg = byKey.computeIfAbsent(key, k -> {
                    TopProductItem t = new TopProductItem();
                    t.setProductId(item.getProductId());
                    t.setProductName(item.getProductName() != null ? item.getProductName() : "Unknown Product");
                    return t;
                });
                agg.setNetQuantity(agg.getNetQuantity() + item.getQuantity());
                double lineRev = item.getLineTotal() > 0 ? item.getLineTotal() : item.getUnitPrice() * item.getQuantity();
                agg.setRevenue(agg.getRevenue() + lineRev);
            }
            List<ReturnRecord> saleReturns = returnsBySale.get(sale.getId());
            if (saleReturns != null) {
                for (ReturnRecord ret : saleReturns) {
                    if (ret.getItems() == null) continue;
                    for (ReturnRecord.ReturnItem rItem : ret.getItems()) {
                        String key = rItem.getProductId() != null && !rItem.getProductId().isBlank()
                                ? rItem.getProductId()
                                : ("name:" + (rItem.getProductName() != null ? rItem.getProductName() : "Unknown"));
                        TopProductItem agg = byKey.get(key);
                        if (agg != null) {
                            agg.setNetQuantity(Math.max(0.0, agg.getNetQuantity() - rItem.getQuantity()));
                            agg.setRevenue(Math.max(0.0, agg.getRevenue() - rItem.getRefundAmount()));
                        }
                    }
                }
            }
        }
        return byKey.values().stream()
                .filter(p -> p.getNetQuantity() > 0)
                .peek(p -> {
                    p.setNetQuantity(round2(p.getNetQuantity()));
                    p.setRevenue(round2(p.getRevenue()));
                })
                .sorted(Comparator.comparingDouble(TopProductItem::getNetQuantity).reversed()
                        .thenComparing(Comparator.comparingDouble(TopProductItem::getRevenue).reversed())
                        .thenComparing(TopProductItem::getProductName, String.CASE_INSENSITIVE_ORDER))
                .limit(5)
                .collect(Collectors.toList());
    }

    private List<SalesTrendPoint> buildSalesTrend(
            LocalDate startDate,
            LocalDate endDate,
            List<Sale> sales,
            Map<String, List<ReturnRecord>> returnsBySale) {
        Map<LocalDate, SalesTrendPoint> buckets = new LinkedHashMap<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            SalesTrendPoint pt = new SalesTrendPoint();
            pt.setDate(d.toString());
            buckets.put(d, pt);
        }
        for (Sale sale : sales) {
            LocalDate day;
            if (sale.getCreatedAtInstant() != null) {
                day = sale.getCreatedAtInstant().atZone(IST).toLocalDate();
            } else if (sale.getCreatedAt() != null) {
                day = sale.getCreatedAt().toLocalDate();
            } else {
                continue;
            }
            SalesTrendPoint pt = buckets.get(day);
            if (pt == null) continue;
            double gross = Math.max(0.0, sale.getGrandTotal());
            double refund = computeCappedSaleRefund(sale, returnsBySale.get(sale.getId()));
            pt.setGrossSales(round2(pt.getGrossSales() + gross));
            pt.setNetSales(round2(pt.getNetSales() + Math.max(0.0, gross - refund)));
            pt.setOrdersCount(pt.getOrdersCount() + 1);
        }
        return new ArrayList<>(buckets.values());
    }

    private List<DashboardAlertItem> buildAlerts(
            String storeId,
            List<Product> activeProducts,
            List<InventoryBatch> expiringBatches) {
        Map<String, DashboardAlertItem> dedup = new LinkedHashMap<>();

        for (Product p : activeProducts) {
            if (p.getCurrentStock() <= 0) {
                String key = "OUT_OF_STOCK:" + p.getId();
                DashboardAlertItem item = new DashboardAlertItem();
                item.setId(key);
                item.setType("OUT_OF_STOCK");
                item.setPriority("CRITICAL");
                item.setTitle("Out of Stock: " + p.getName());
                item.setMessage("Current stock is 0 " + safeUnit(p.getUnit()));
                item.setTargetLink("inventory.html");
                dedup.putIfAbsent(key, item);
            } else if (p.getCurrentStock() <= p.getMinStockLevel()) {
                String key = "LOW_STOCK:" + p.getId();
                DashboardAlertItem item = new DashboardAlertItem();
                item.setId(key);
                item.setType("LOW_STOCK");
                item.setPriority("HIGH");
                item.setTitle("Low Stock: " + p.getName());
                item.setMessage("Remaining " + p.getCurrentStock() + " " + safeUnit(p.getUnit()) + " (Min: " + p.getMinStockLevel() + ")");
                item.setTargetLink("inventory.html");
                dedup.putIfAbsent(key, item);
            }
        }

        for (InventoryBatch b : expiringBatches) {
            String key = "EXPIRING_SOON:" + b.getId();
            DashboardAlertItem item = new DashboardAlertItem();
            item.setId(key);
            item.setType("EXPIRING_SOON");
            item.setPriority("HIGH");
            item.setTitle("Expiring Batch: " + (b.getBatchNumber() != null ? b.getBatchNumber() : b.getProductId()));
            item.setMessage("Expires on " + b.getExpiryDate() + " (Qty: " + b.getCurrentQty() + ")");
            item.setTargetLink("inventory.html");
            dedup.putIfAbsent(key, item);
        }

        List<Notification> unread = notificationRepository.findByStoreIdAndReadOrderByCreatedAtDesc(storeId, false);
        for (Notification n : unread) {
            String title = n.getTitle() != null ? n.getTitle().trim() : "Notification";
            String key = "NOTIF:" + title.toLowerCase();
            if (dedup.containsKey(key)) continue;
            // Avoid duplicating low-stock notification if product-based alert already covers it
            if ("LOW_STOCK".equalsIgnoreCase(n.getType()) && title.startsWith("Low Stock Alert: ")) {
                String productName = title.substring("Low Stock Alert: ".length()).trim();
                boolean covered = dedup.values().stream()
                        .anyMatch(a -> ("Low Stock: " + productName).equalsIgnoreCase(a.getTitle())
                                || ("Out of Stock: " + productName).equalsIgnoreCase(a.getTitle()));
                if (covered) continue;
            }
            DashboardAlertItem item = new DashboardAlertItem();
            item.setId(n.getId());
            item.setType(n.getType() != null ? n.getType() : "SYSTEM");
            item.setPriority(n.getPriority() != null ? n.getPriority() : "MEDIUM");
            item.setTitle(title);
            item.setMessage(n.getMessage());
            item.setTargetLink(n.getTargetLink() != null ? n.getTargetLink() : "dashboard.html");
            dedup.put(key, item);
        }

        return dedup.values().stream().limit(10).collect(Collectors.toList());
    }

    private String safeUnit(String unit) {
        return unit != null && !unit.isBlank() ? unit : "units";
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static class SalesSummary {
        double grossSales;
        double netSales;
        long ordersCount;
        long fullyRefundedOrders;
        long partiallyRefundedOrders;
    }

    private static class CogsResult {
        boolean available;
        double totalCogs;
        String reason;

        static CogsResult available(double totalCogs) {
            CogsResult r = new CogsResult();
            r.available = true;
            r.totalCogs = totalCogs;
            return r;
        }

        static CogsResult unavailable(String reason) {
            CogsResult r = new CogsResult();
            r.available = false;
            r.reason = reason;
            return r;
        }
    }
}
