package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.StoreSettings;
import com.grocery.repository.StoreSettingsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final StoreSettingsRepository storeSettingsRepository;

    public SettingsController(StoreSettingsRepository storeSettingsRepository) {
        this.storeSettingsRepository = storeSettingsRepository;
    }

    @GetMapping
    public ResponseEntity<StoreSettings> getSettings() {
        StoreSettings settings = storeSettingsRepository.findById("STORE_SETTINGS_PRIMARY")
                .orElseGet(() -> {
                    StoreSettings s = new StoreSettings();
                    return storeSettingsRepository.save(s);
                });
        return ResponseEntity.ok(settings);
    }

    @PutMapping
    public ResponseEntity<ApiResponse<StoreSettings>> updateSettings(@RequestBody StoreSettings update) {
        StoreSettings existing = storeSettingsRepository.findById("STORE_SETTINGS_PRIMARY")
                .orElse(new StoreSettings());

        existing.setStoreName(update.getStoreName());
        existing.setLegalName(update.getLegalName());
        existing.setPhone(update.getPhone());
        existing.setEmail(update.getEmail());
        existing.setAddress(update.getAddress());
        existing.setCity(update.getCity());
        existing.setState(update.getState());
        existing.setPinCode(update.getPinCode());
        existing.setGstin(update.getGstin());
        existing.setCurrencySymbol(update.getCurrencySymbol());
        existing.setReceiptHeader(update.getReceiptHeader());
        existing.setReceiptFooter(update.getReceiptFooter());
        existing.setPrintBarcodeOnReceipt(update.isPrintBarcodeOnReceipt());
        existing.setPrinterPaperWidth(update.getPrinterPaperWidth());
        existing.setDefaultTaxRate(update.getDefaultTaxRate());
        existing.setDefaultMinStockThreshold(update.getDefaultMinStockThreshold());
        existing.setNearExpiryDaysWindow(update.getNearExpiryDaysWindow());
        existing.setAllowNegativeStock(update.isAllowNegativeStock());
        existing.setLoyaltyEnabled(update.isLoyaltyEnabled());
        existing.setLoyaltyPointsPerHundred(update.getLoyaltyPointsPerHundred());
        existing.setUpdatedAt(LocalDateTime.now());

        StoreSettings saved = storeSettingsRepository.save(existing);
        return ResponseEntity.ok(ApiResponse.success("Store settings updated successfully", saved));
    }
}
