package com.grocery.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {

    public String requireStoreId(UserDetails userDetails) {
        UserPrincipal principal = resolvePrincipal(userDetails);
        if (principal == null || principal.getStoreId() == null || principal.getStoreId().trim().isEmpty()) {
            throw new AccessDeniedException("Authenticated store context is required");
        }
        return principal.getStoreId();
    }

    public String requireUsername(UserDetails userDetails) {
        UserPrincipal principal = resolvePrincipal(userDetails);
        if (principal != null && principal.getUsername() != null) {
            return principal.getUsername();
        }
        if (userDetails != null && userDetails.getUsername() != null) {
            return userDetails.getUsername();
        }
        throw new AccessDeniedException("Authenticated user context is required");
    }

    private UserPrincipal resolvePrincipal(UserDetails userDetails) {
        if (userDetails instanceof UserPrincipal p) {
            return p;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        return null;
    }
}
