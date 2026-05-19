package com.realestate.backend.service;

import com.realestate.backend.dto.auth.MessageResponse;
import com.realestate.backend.dto.invite.InviteRequest;
import com.realestate.backend.dto.invite.InviteVerifyResponse;
import com.realestate.backend.entity.InviteToken;
import com.realestate.backend.entity.tenant.TenantCompany;
import com.realestate.backend.exception.ResourceNotFoundException;
import com.realestate.backend.exception.UnauthorizedException;
import com.realestate.backend.repository.InviteRepository;
import com.realestate.backend.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * InviteService — business logic for invitation token management.
 *
 * Handles:
 *   - Generating a unique invite token tied to a tenant and role
 *   - Verifying a token is valid (not used, not expired)
 *   - Marking a token as used after registration completes
 */
@Service
@RequiredArgsConstructor
public class InviteService {

    private final InviteRepository inviteRepository;
    private final TenantRepository tenantRepository;

    /**
     * Creates a new invite token for the given tenant and role.
     * Token is valid for 7 days and can only be used once.
     */
    @Transactional
    public String createInvite(InviteRequest request) {

        TenantCompany tenant = tenantRepository.findById(request.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant nuk ekziston: " + request.tenantId()));

        // 32-character unique token — no hyphens
        String token = UUID.randomUUID().toString().replace("-", "");

        InviteToken invite = InviteToken.builder()
                .token(token)
                .tenant(tenant)
                .role(request.role().toUpperCase())
                .used(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        inviteRepository.save(invite);

        return token;
    }

    /**
     * Verifies the invite token exists and has not been used or expired.
     * Returns tenant info and role for pre-filling the registration form.
     */
    @Transactional(readOnly = true)
    public InviteVerifyResponse verifyInvite(String token) {

        InviteToken invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invite i pavlefshëm"));

        if (!invite.isValid()) {
            throw new UnauthorizedException(
                    "Invite ka skaduar ose është përdorur");
        }

        return new InviteVerifyResponse(
                invite.getTenant().getSlug(),
                invite.getTenant().getName(),
                invite.getRole()
        );
    }

    /**
     * Marks the invite token as used after a successful registration.
     * Called by the frontend after register completes.
     */
    @Transactional
    public void markUsed(String token) {

        InviteToken invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invite nuk ekziston"));

        invite.setUsed(true);
        inviteRepository.save(invite);
    }
}