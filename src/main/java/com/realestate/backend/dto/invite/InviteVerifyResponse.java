package com.realestate.backend.dto.invite;

/**
 * Response returned when verifying an invite token.
 * Contains tenant info and role for the registration form.
 */
public record InviteVerifyResponse(
        String tenantSlug,
        String tenantName,
        String role
) {}