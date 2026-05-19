package com.realestate.backend.dto.invite;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for creating a new invite token.
 */
public record InviteRequest(
        @NotNull  Long   tenantId,
        @NotBlank String role
) {}