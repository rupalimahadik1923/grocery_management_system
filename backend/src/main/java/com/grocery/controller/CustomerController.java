package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.Customer;
import com.grocery.repository.CustomerRepository;
import com.grocery.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER','CASHIER')")
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final TenantContext tenantContext;

    public CustomerController(CustomerRepository customerRepository, TenantContext tenantContext) {
        this.customerRepository = customerRepository;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    public ResponseEntity<List<Customer>> getCustomers(@AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return ResponseEntity.ok(customerRepository.findByStoreId(storeId));
    }

    @GetMapping({"/by-phone/{phone}", "/phone/{phone}"})
    public ResponseEntity<Customer> getCustomerByPhone(
            @PathVariable String phone,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return customerRepository.findByStoreIdAndPhone(storeId, phone)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Customer>> createCustomer(
            @RequestBody Customer customer,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        if (customer.getPhone() != null && customerRepository.findByStoreIdAndPhone(storeId, customer.getPhone()).isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Customer with this phone already exists in your store"));
        }
        customer.setStoreId(storeId);
        Customer saved = customerRepository.save(customer);
        return ResponseEntity.ok(ApiResponse.success("Customer registered", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Customer>> updateCustomer(
            @PathVariable String id,
            @RequestBody Customer update,
            @AuthenticationPrincipal UserDetails userDetails) {
        String storeId = tenantContext.requireStoreId(userDetails);
        return customerRepository.findByStoreIdAndId(storeId, id).map(existing -> {
            existing.setName(update.getName());
            existing.setPhone(update.getPhone());
            existing.setEmail(update.getEmail());
            existing.setAddress(update.getAddress());
            Customer saved = customerRepository.save(existing);
            return ResponseEntity.ok(ApiResponse.success("Customer updated", saved));
        }).orElse(ResponseEntity.notFound().build());
    }
}
