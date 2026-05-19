package com.realestate.backend.dto.impersonation;

/**
 * Response returned when impersonation starts successfully.
 * Contains the new JWT token and basic info about the impersonated user.
 */
public record ImpersonationResponse(
        String token,
        String email,
        String role,
        String fullName,
        String message
) {}