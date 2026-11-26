package com.grocery.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DashboardMetricsResponse {
    private double todaySales;
    private double todayGrossSales;
    private double todayNetSales;
    private long todayOrdersCount;
    private long fullyRefundedOrders;
    private long partiallyRefundedOrders;
    private String aovPopulationLabel = "Non-cancelled, non-voided orders (includes fully refunded)";
    private double aov;

    private double periodGrossSales;
    private double periodNetSales;
    private long periodOrdersCount;
    private double periodAov;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;

    private Double cogs;
    private Double grossProfit;
    private Double netProfit;
    private boolean cogsAvailable;
    private String cogsUnavailableReason;

    private long totalProducts;
    private long lowStockCount;
    private long outOfStockCount;
    private long expiringCount;
    private double totalRevenue;
    private double monthExpenses;
    private double periodExpenses;

    private List<RecentSaleItem> recentSales = new ArrayList<>();
    private List<TopProductItem> topProducts = new ArrayList<>();
    private List<SalesTrendPoint> salesTrend = new ArrayList<>();
    private List<DashboardAlertItem> alerts = new ArrayList<>();

    public static class RecentSaleItem {
        private String id;
        private String receiptNumber;
        private String customerName;
        private double grandTotal;
        private String status;
        private String paymentMethod;
        private LocalDateTime createdAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getReceiptNumber() { return receiptNumber; }
        public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public double getGrandTotal() { return grandTotal; }
        public void setGrandTotal(double grandTotal) { this.grandTotal = grandTotal; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class TopProductItem {
        private String productId;
        private String productName;
        private double netQuantity;
        private double revenue;

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public double getNetQuantity() { return netQuantity; }
        public void setNetQuantity(double netQuantity) { this.netQuantity = netQuantity; }
        public double getRevenue() { return revenue; }
        public void setRevenue(double revenue) { this.revenue = revenue; }
    }

    public static class SalesTrendPoint {
        private String date;
        private double grossSales;
        private double netSales;
        private long ordersCount;

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public double getGrossSales() { return grossSales; }
        public void setGrossSales(double grossSales) { this.grossSales = grossSales; }
        public double getNetSales() { return netSales; }
        public void setNetSales(double netSales) { this.netSales = netSales; }
        public long getOrdersCount() { return ordersCount; }
        public void setOrdersCount(long ordersCount) { this.ordersCount = ordersCount; }
    }

    public static class DashboardAlertItem {
        private String id;
        private String type;
        private String priority;
        private String title;
        private String message;
        private String targetLink;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getTargetLink() { return targetLink; }
        public void setTargetLink(String targetLink) { this.targetLink = targetLink; }
    }

    public double getTodaySales() { return todaySales; }
    public void setTodaySales(double todaySales) { this.todaySales = todaySales; }
    public double getTodayGrossSales() { return todayGrossSales; }
    public void setTodayGrossSales(double todayGrossSales) { this.todayGrossSales = todayGrossSales; }
    public double getTodayNetSales() { return todayNetSales; }
    public void setTodayNetSales(double todayNetSales) { this.todayNetSales = todayNetSales; }
    public long getTodayOrdersCount() { return todayOrdersCount; }
    public void setTodayOrdersCount(long todayOrdersCount) { this.todayOrdersCount = todayOrdersCount; }
    public long getFullyRefundedOrders() { return fullyRefundedOrders; }
    public void setFullyRefundedOrders(long fullyRefundedOrders) { this.fullyRefundedOrders = fullyRefundedOrders; }
    public long getPartiallyRefundedOrders() { return partiallyRefundedOrders; }
    public void setPartiallyRefundedOrders(long partiallyRefundedOrders) { this.partiallyRefundedOrders = partiallyRefundedOrders; }
    public String getAovPopulationLabel() { return aovPopulationLabel; }
    public void setAovPopulationLabel(String aovPopulationLabel) { this.aovPopulationLabel = aovPopulationLabel; }
    public double getAov() { return aov; }
    public void setAov(double aov) { this.aov = aov; }
    public double getPeriodGrossSales() { return periodGrossSales; }
    public void setPeriodGrossSales(double periodGrossSales) { this.periodGrossSales = periodGrossSales; }
    public double getPeriodNetSales() { return periodNetSales; }
    public void setPeriodNetSales(double periodNetSales) { this.periodNetSales = periodNetSales; }
    public long getPeriodOrdersCount() { return periodOrdersCount; }
    public void setPeriodOrdersCount(long periodOrdersCount) { this.periodOrdersCount = periodOrdersCount; }
    public double getPeriodAov() { return periodAov; }
    public void setPeriodAov(double periodAov) { this.periodAov = periodAov; }
    public LocalDate getPeriodStartDate() { return periodStartDate; }
    public void setPeriodStartDate(LocalDate periodStartDate) { this.periodStartDate = periodStartDate; }
    public LocalDate getPeriodEndDate() { return periodEndDate; }
    public void setPeriodEndDate(LocalDate periodEndDate) { this.periodEndDate = periodEndDate; }
    public Double getCogs() { return cogs; }
    public void setCogs(Double cogs) { this.cogs = cogs; }
    public Double getGrossProfit() { return grossProfit; }
    public void setGrossProfit(Double grossProfit) { this.grossProfit = grossProfit; }
    public Double getNetProfit() { return netProfit; }
    public void setNetProfit(Double netProfit) { this.netProfit = netProfit; }
    public boolean isCogsAvailable() { return cogsAvailable; }
    public void setCogsAvailable(boolean cogsAvailable) { this.cogsAvailable = cogsAvailable; }
    public String getCogsUnavailableReason() { return cogsUnavailableReason; }
    public void setCogsUnavailableReason(String cogsUnavailableReason) { this.cogsUnavailableReason = cogsUnavailableReason; }
    public long getTotalProducts() { return totalProducts; }
    public void setTotalProducts(long totalProducts) { this.totalProducts = totalProducts; }
    public long getLowStockCount() { return lowStockCount; }
    public void setLowStockCount(long lowStockCount) { this.lowStockCount = lowStockCount; }
    public long getOutOfStockCount() { return outOfStockCount; }
    public void setOutOfStockCount(long outOfStockCount) { this.outOfStockCount = outOfStockCount; }
    public long getExpiringCount() { return expiringCount; }
    public void setExpiringCount(long expiringCount) { this.expiringCount = expiringCount; }
    public double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }
    public double getMonthExpenses() { return monthExpenses; }
    public void setMonthExpenses(double monthExpenses) { this.monthExpenses = monthExpenses; }
    public double getPeriodExpenses() { return periodExpenses; }
    public void setPeriodExpenses(double periodExpenses) { this.periodExpenses = periodExpenses; }
    public List<RecentSaleItem> getRecentSales() { return recentSales; }
    public void setRecentSales(List<RecentSaleItem> recentSales) { this.recentSales = recentSales; }
    public List<TopProductItem> getTopProducts() { return topProducts; }
    public void setTopProducts(List<TopProductItem> topProducts) { this.topProducts = topProducts; }
    public List<SalesTrendPoint> getSalesTrend() { return salesTrend; }
    public void setSalesTrend(List<SalesTrendPoint> salesTrend) { this.salesTrend = salesTrend; }
    public List<DashboardAlertItem> getAlerts() { return alerts; }
    public void setAlerts(List<DashboardAlertItem> alerts) { this.alerts = alerts; }
}
