package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.Supplier;
import com.grocery.repository.SupplierRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierRepository supplierRepository;

    public SupplierController(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @GetMapping
    public ResponseEntity<List<Supplier>> getSuppliers() {
        return ResponseEntity.ok(supplierRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Supplier>> createSupplier(@RequestBody Supplier supplier) {
        if (supplier.getCode() == null || supplier.getCode().isEmpty()) {
            supplier.setCode("SUP-" + (supplierRepository.count() + 1));
        }
        Supplier saved = supplierRepository.save(supplier);
        return ResponseEntity.ok(ApiResponse.success("Supplier added successfully", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Supplier>> updateSupplier(@PathVariable String id, @RequestBody Supplier update) {
        return supplierRepository.findById(id).map(existing -> {
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
    public ResponseEntity<ApiResponse<Void>> deleteSupplier(@PathVariable String id) {
        supplierRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Supplier deleted successfully", null));
    }
}
