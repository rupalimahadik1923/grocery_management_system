package com.grocery.controller;

import com.grocery.dto.ApiResponse;
import com.grocery.model.StoreSettings;
import com.grocery.repository.StoreSettingsRepository;
import com.grocery.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final StoreSettingsRepository storeSettingsRepository;

    public SettingsController(StoreSettingsRepository storeSettingsRepository) {
        this.storeSettingsRepository = storeSettingsRepository;
    }

    private String resolveSettingsId(UserPrincipal user) {
        return (user != null && user.getStoreId() != null)
                ? "STORE_SETTINGS_" + user.getStoreId()
                : "STORE_SETTINGS_PRIMARY";
    }

    @GetMapping
    public ResponseEntity<StoreSettings> getSettings(@AuthenticationPrincipal UserPrincipal user) {
        String settingsId = resolveSettingsId(user);
        StoreSettings settings = storeSettingsRepository.findById(settingsId)
                .orElseGet(() -> {
                    StoreSettings s = new StoreSettings();
                    s.setId(settingsId);
                    if (user != null) s.setStoreId(user.getStoreId());
                    return storeSettingsRepository.save(s);
                });
        return ResponseEntity.ok(settings);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StoreSettings>> updateSettings(
            @RequestBody StoreSettings update,
            @AuthenticationPrincipal UserPrincipal user) {
        String settingsId = resolveSettingsId(user);
        StoreSettings existing = storeSettingsRepository.findById(settingsId)
                .orElseGet(() -> {
                    StoreSettings s = new StoreSettings();
                    s.setId(settingsId);
                    if (user != null) s.setStoreId(user.getStoreId());
                    return s;
                });

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
