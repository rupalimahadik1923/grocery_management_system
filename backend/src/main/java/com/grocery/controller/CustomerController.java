package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.Customer;
import com.grocery.repository.CustomerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerRepository customerRepository;

    public CustomerController(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @GetMapping
    public ResponseEntity<List<Customer>> getCustomers() {
        return ResponseEntity.ok(customerRepository.findAll());
    }

    @GetMapping({"/by-phone/{phone}", "/phone/{phone}"})
    public ResponseEntity<Customer> getCustomerByPhone(@PathVariable String phone) {
        return customerRepository.findByPhone(phone)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Customer>> createCustomer(@RequestBody Customer customer) {
        if (customer.getPhone() != null && customerRepository.findByPhone(customer.getPhone()).isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Customer with this phone already exists"));
        }
        Customer saved = customerRepository.save(customer);
        return ResponseEntity.ok(ApiResponse.success("Customer registered", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Customer>> updateCustomer(@PathVariable String id, @RequestBody Customer update) {
        return customerRepository.findById(id).map(existing -> {
            existing.setName(update.getName());
            existing.setPhone(update.getPhone());
            existing.setEmail(update.getEmail());
            existing.setAddress(update.getAddress());
            Customer saved = customerRepository.save(existing);
            return ResponseEntity.ok(ApiResponse.success("Customer updated", saved));
        }).orElse(ResponseEntity.notFound().build());
    }
}
