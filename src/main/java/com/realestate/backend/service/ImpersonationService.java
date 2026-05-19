package com.realestate.backend.service;

import com.realestate.backend.dto.impersonation.ImpersonationResponse;
import com.realestate.backend.dto.auth.MessageResponse;
import com.realestate.backend.entity.User;
import com.realestate.backend.entity.tenant.TenantSchemaRegistry;
import com.realestate.backend.multitenancy.TenantContext;
import com.realestate.backend.repository.SchemaRegistryRepository;
import com.realestate.backend.repository.UserRepository;
import com.realestate.backend.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * ImpersonationService — business logic for admin impersonation.
 *
 * Enforces all security rules before generating an impersonation token:
 *   1. Caller must be ADMIN
 *   2. Target cannot be another ADMIN
 *   3. Target must belong to the same tenant as the caller
 *
 * Token generation and audit logging live here — not in the controller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImpersonationService {

    private final UserRepository           userRepository;
    private final SchemaRegistryRepository schemaRegistryRepository;
    private final JwtUtil                  jwtUtil;

    /**
     * Validates all security rules, generates an impersonation JWT,
     * and returns it alongside basic info about the impersonated user.
     */
    public ImpersonationResponse impersonate(Long targetUserId) {

        // Rule 1 — only ADMIN can impersonate
        if (!"ADMIN".equals(TenantContext.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only ADMIN can impersonate users");
        }

        // Find target user
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found: " + targetUserId));

        // Rule 2 — cannot impersonate another ADMIN
        if ("ADMIN".equals(target.getRole().name())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Cannot impersonate another ADMIN");
        }

        // Rule 3 — cannot impersonate across tenants
        if (!target.getTenant().getId().equals(TenantContext.getTenantId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Cannot impersonate user from a different tenant");
        }

        // Resolve schema for the target tenant
        String schemaName = schemaRegistryRepository
                .findByTenant_Id(target.getTenant().getId())
                .map(TenantSchemaRegistry::getSchemaName)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Schema not found for tenant"));

        // Generate impersonation JWT with audit claim
        String token = jwtUtil.generateImpersonationToken(
                target.getId(),
                target.getEmail(),
                target.getTenant().getId(),
                schemaName,
                target.getRole().name(),
                TenantContext.getUserId()   // impersonatedBy — audit trail
        );

        log.warn("IMPERSONATION STARTED — admin={} impersonating userId={}",
                TenantContext.getUserId(), targetUserId);

        String fullName = (target.getFirstName() + " " + target.getLastName()).trim();

        return new ImpersonationResponse(
                token,
                target.getEmail(),
                target.getRole().name(),
                fullName,
                "Now acting as " + target.getEmail()
        );
    }

    /**
     * Logs the impersonation exit.
     * The actual token restoration is handled entirely by the frontend.
     */
    public MessageResponse exit() {
        log.info("IMPERSONATION EXIT — userId={}", TenantContext.getUserId());
        return new MessageResponse("Impersonation exited — restore your original token");
    }
}