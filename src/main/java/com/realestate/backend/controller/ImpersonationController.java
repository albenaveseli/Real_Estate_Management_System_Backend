package com.realestate.backend.controller;

import com.realestate.backend.dto.auth.MessageResponse;
import com.realestate.backend.dto.impersonation.ImpersonationResponse;
import com.realestate.backend.service.ImpersonationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ImpersonationController — allows ADMIN to act as another user.
 *
 * Generates a new JWT token with the target user's claims plus an
 * "impersonatedBy" field so every action is traceable back to the
 * original admin. The admin's original token is never invalidated —
 * the frontend simply swaps tokens and restores the original on exit.
 *
 * All security rules and business logic are enforced in ImpersonationService.
 */
@RestController
@RequestMapping("/api/admin/impersonate")
@RequiredArgsConstructor
@Tag(name = "Impersonation")
@SecurityRequirement(name = "BearerAuth")
public class ImpersonationController extends BaseController {

    private final ImpersonationService impersonationService;

    @PostMapping("/{userId}")
    @Operation(summary = "Start impersonating a user (ADMIN only)")
    public ResponseEntity<ImpersonationResponse> impersonate(
            @PathVariable Long userId) {
        return ok(impersonationService.impersonate(userId));
    }

    @PostMapping("/exit")
    @Operation(summary = "Exit impersonation — frontend restores original token")
    public ResponseEntity<MessageResponse> exit() {
        return ok(impersonationService.exit());
    }
}