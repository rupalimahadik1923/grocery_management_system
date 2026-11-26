package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.Supplier;
import com.grocery.repository.SupplierRepository;
import com.grocery.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/suppliers")
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
public class SupplierController {

    private final SupplierRepository supplierRepository;
    private final TenantContext tenantContext;

    public SupplierController(SupplierRepository supplierRepository, TenantContext tenantContext) {
        this.supplierRepository = supplierRepository;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    public ResponseEntity<List<Supplier>> getSuppliers(@AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return ResponseEntity.ok(supplierRepository.findByStoreId(storeId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Supplier>> createSupplier(
            @RequestBody Supplier supplier,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        if (supplier.getCode() == null || supplier.getCode().isEmpty()) {
            supplier.setCode("SUP-" + (supplierRepository.countByStoreId(storeId) + 1));
        } else if (supplierRepository.existsByStoreIdAndCode(storeId, supplier.getCode())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Supplier code already exists in your store"));
        }
        supplier.setStoreId(storeId);
        Supplier saved = supplierRepository.save(supplier);
        return ResponseEntity.ok(ApiResponse.success("Supplier added successfully", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Supplier>> updateSupplier(
            @PathVariable String id,
            @RequestBody Supplier update,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return supplierRepository.findByStoreIdAndId(storeId, id).map(existing -> {
            existing.setName(update.getName());
            existing.setContactPerson(update.getContactPerson());
            existing.setPhone(update.getPhone());
            existing.setEmail(update.getEmail());
            existing.setGstin(update.getGstin());
            existing.setAddress(update.getAddress());
            existing.setPaymentTerms(update.getPaymentTerms());
            existing.setStatus(update.getStatus());
            Supplier saved = supplierRepository.save(existing);
            return ResponseEntity.ok(ApiResponse.success("Supplier updated successfully", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSupplier(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return supplierRepository.findByStoreIdAndId(storeId, id).map(existing -> {
            supplierRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.<Void>success("Supplier deleted successfully", null));
        }).orElse(ResponseEntity.notFound().build());
    }
}
