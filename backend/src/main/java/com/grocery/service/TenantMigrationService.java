package com.grocery.service;

import com.grocery.model.*;
import com.grocery.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Idempotent, dry-run-capable tenant backfill service.
 * Never overwrites existing storeId values, never deletes records,
 * and leaves records with ambiguous or contradictory ownership untouched.
 */
@Service
public class TenantMigrationService {

    private final SaleRepository saleRepository;
    private final ReturnRepository returnRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ExpenseRepository expenseRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final NotificationRepository notificationRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public TenantMigrationService(SaleRepository saleRepository,
                                  ReturnRepository returnRepository,
                                  CustomerRepository customerRepository,
                                  SupplierRepository supplierRepository,
                                  PurchaseOrderRepository purchaseOrderRepository,
                                  ExpenseRepository expenseRepository,
                                  InventoryBatchRepository batchRepository,
                                  InventoryTransactionRepository transactionRepository,
                                  NotificationRepository notificationRepository,
                                  ProductRepository productRepository,
                                  UserRepository userRepository) {
        this.saleRepository = saleRepository;
        this.returnRepository = returnRepository;
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.expenseRepository = expenseRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.notificationRepository = notificationRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public static class MigrationReport {
        public final boolean dryRun;
        public int inspectedCount = 0;
        public int eligibleCount = 0;
        public int migratedCount = 0;
        public int skippedCount = 0;
        public int unresolvedCount = 0;
        public int conflictingCount = 0;
        public final Map<String, Integer> migratedCounts = new LinkedHashMap<>();
        public final Map<String, List<String>> unresolvedIds = new LinkedHashMap<>();
        public final Map<String, List<String>> conflictingIds = new LinkedHashMap<>();

        public MigrationReport(boolean dryRun) {
            this.dryRun = dryRun;
        }

        public void recordSkipped() {
            skippedCount++;
        }

        public void recordMigrated(String collection) {
            eligibleCount++;
            migratedCount++;
            migratedCounts.merge(collection, 1, Integer::sum);
        }

        public void recordEligibleOnly(String collection) {
            eligibleCount++;
            migratedCounts.merge(collection, 1, Integer::sum);
        }

        public void recordUnresolved(String collection, String id) {
            unresolvedCount++;
            unresolvedIds.computeIfAbsent(collection, k -> new ArrayList<>()).add(id);
        }

        public void recordConflict(String collection, String id) {
            conflictingCount++;
            conflictingIds.computeIfAbsent(collection, k -> new ArrayList<>()).add(id);
        }
    }

    private boolean isBlank(String val) {
        return val == null || val.trim().isEmpty();
    }

    private void addIfPresent(Set<String> candidates, String storeId) {
        if (!isBlank(storeId)) candidates.add(storeId.trim());
    }

    public MigrationReport runMigration(boolean dryRun) {
        MigrationReport report = new MigrationReport(dryRun);

        // 1. Sales: verify all referenced items & cashier agree on a single storeId
        for (Sale sale : saleRepository.findAll()) {
            report.inspectedCount++;
            if (!isBlank(sale.getStoreId())) { report.recordSkipped(); continue; }
            Set<String> candidates = new LinkedHashSet<>();
            if (sale.getItems() != null) {
                for (Sale.SaleItem item : sale.getItems()) {
                    if (!isBlank(item.getProductId())) {
                        productRepository.findById(item.getProductId()).ifPresent(p -> addIfPresent(candidates, p.getStoreId()));
                    }
                }
            }
            if (!isBlank(sale.getCashierId())) {
                userRepository.findById(sale.getCashierId()).ifPresent(u -> addIfPresent(candidates, u.getStoreId()));
            }
            if (candidates.size() > 1) {
                report.recordConflict("sales", sale.getId());
            } else if (candidates.size() == 1) {
                String inferred = candidates.iterator().next();
                if (dryRun) report.recordEligibleOnly("sales");
                else {
                    sale.setStoreId(inferred);
                    saleRepository.save(sale);
                    report.recordMigrated("sales");
                }
            } else {
                report.recordUnresolved("sales", sale.getId());
            }
        }

        // 2. Returns: verify saleId and all returned items agree on a single storeId
        for (ReturnRecord ret : returnRepository.findAll()) {
            report.inspectedCount++;
            if (!isBlank(ret.getStoreId())) { report.recordSkipped(); continue; }
            Set<String> candidates = new LinkedHashSet<>();
            if (!isBlank(ret.getSaleId())) {
                saleRepository.findById(ret.getSaleId()).ifPresent(s -> addIfPresent(candidates, s.getStoreId()));
            }
            if (ret.getItems() != null) {
                for (ReturnRecord.ReturnItem item : ret.getItems()) {
                    if (!isBlank(item.getProductId())) {
                        productRepository.findById(item.getProductId()).ifPresent(p -> addIfPresent(candidates, p.getStoreId()));
                    }
                }
            }
            if (candidates.size() > 1) {
                report.recordConflict("returns", ret.getId());
            } else if (candidates.size() == 1) {
                String inferred = candidates.iterator().next();
                if (dryRun) report.recordEligibleOnly("returns");
                else {
                    ret.setStoreId(inferred);
                    returnRepository.save(ret);
                    report.recordMigrated("returns");
                }
            } else {
                report.recordUnresolved("returns", ret.getId());
            }
        }

        // 3. InventoryBatches: verify productId & supplierId agree
        for (InventoryBatch batch : batchRepository.findAll()) {
            report.inspectedCount++;
            if (!isBlank(batch.getStoreId())) { report.recordSkipped(); continue; }
            Set<String> candidates = new LinkedHashSet<>();
            if (!isBlank(batch.getProductId())) {
                productRepository.findById(batch.getProductId()).ifPresent(p -> addIfPresent(candidates, p.getStoreId()));
            }
            if (!isBlank(batch.getSupplierId())) {
                supplierRepository.findById(batch.getSupplierId()).ifPresent(s -> addIfPresent(candidates, s.getStoreId()));
            }
            if (candidates.size() > 1) {
                report.recordConflict("inventory_batches", batch.getId());
            } else if (candidates.size() == 1) {
                String inferred = candidates.iterator().next();
                if (dryRun) report.recordEligibleOnly("inventory_batches");
                else {
                    batch.setStoreId(inferred);
                    batchRepository.save(batch);
                    report.recordMigrated("inventory_batches");
                }
            } else {
                report.recordUnresolved("inventory_batches", batch.getId());
            }
        }

        // 4. InventoryTransactions: infer from productId
        for (InventoryTransaction tx : transactionRepository.findAll()) {
            report.inspectedCount++;
            if (!isBlank(tx.getStoreId())) { report.recordSkipped(); continue; }
            String inferred = !isBlank(tx.getProductId())
                    ? productRepository.findById(tx.getProductId()).map(Product::getStoreId).orElse(null)
                    : null;
            if (!isBlank(inferred)) {
                if (dryRun) report.recordEligibleOnly("inventory_transactions");
                else {
                    tx.setStoreId(inferred);
                    transactionRepository.save(tx);
                    report.recordMigrated("inventory_transactions");
                }
            } else {
                report.recordUnresolved("inventory_transactions", tx.getId());
            }
        }

        // 5. PurchaseOrders: verify supplierId and all item productIds agree
        for (PurchaseOrder po : purchaseOrderRepository.findAll()) {
            report.inspectedCount++;
            if (!isBlank(po.getStoreId())) { report.recordSkipped(); continue; }
            Set<String> candidates = new LinkedHashSet<>();
            if (!isBlank(po.getSupplierId())) {
                supplierRepository.findById(po.getSupplierId()).ifPresent(s -> addIfPresent(candidates, s.getStoreId()));
            }
            if (po.getItems() != null) {
                for (PurchaseOrder.PurchaseOrderItem item : po.getItems()) {
                    if (!isBlank(item.getProductId())) {
                        productRepository.findById(item.getProductId()).ifPresent(p -> addIfPresent(candidates, p.getStoreId()));
                    }
                }
            }
            if (candidates.size() > 1) {
                report.recordConflict("purchase_orders", po.getId());
            } else if (candidates.size() == 1) {
                String inferred = candidates.iterator().next();
                if (dryRun) report.recordEligibleOnly("purchase_orders");
                else {
                    po.setStoreId(inferred);
                    purchaseOrderRepository.save(po);
                    report.recordMigrated("purchase_orders");
                }
            } else {
                report.recordUnresolved("purchase_orders", po.getId());
            }
        }

        // 6. Collections without reliable parent relationships: report unresolved without guessing
        for (Customer c : customerRepository.findAll()) {
            report.inspectedCount++;
            if (!isBlank(c.getStoreId())) report.recordSkipped();
            else report.recordUnresolved("customers", c.getId());
        }
        for (Supplier s : supplierRepository.findAll()) {
            report.inspectedCount++;
            if (!isBlank(s.getStoreId())) report.recordSkipped();
            else report.recordUnresolved("suppliers", s.getId());
        }
        for (Expense e : expenseRepository.findAll()) {
            report.inspectedCount++;
            if (!isBlank(e.getStoreId())) report.recordSkipped();
            else report.recordUnresolved("expenses", e.getId());
        }
        for (Notification n : notificationRepository.findAll()) {
            report.inspectedCount++;
            if (!isBlank(n.getStoreId())) report.recordSkipped();
            else report.recordUnresolved("notifications", n.getId());
        }

        return report;
    }
}
