package com.realestate.backend.controller;

import com.realestate.backend.dto.invite.InviteRequest;
import com.realestate.backend.dto.invite.InviteVerifyResponse;
import com.realestate.backend.service.InviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * InviteController — manages invitation tokens for new user registration.
 *
 * POST   /api/invites          → create invite (ADMIN only, secured via permissions)
 * GET    /api/invites/{token}  → verify invite (public — called before registration)
 * PATCH  /api/invites/{token}/use → mark used (public — called after registration)
 *
 * All business logic lives in InviteService.
 */
@RestController
@RequestMapping("/api/invites")
@RequiredArgsConstructor
@Tag(name = "Invitations")
public class InviteController extends BaseController {

    private final InviteService inviteService;

    @PostMapping
    @Operation(summary = "Krijo invite token te ri (ADMIN only)")
    public ResponseEntity<Map<String, String>> createInvite(
            @Valid @RequestBody InviteRequest request) {

        String token = inviteService.createInvite(request);
        return ok(Map.of("token", token));
    }

    @GetMapping("/{token}")
    @Operation(summary = "Verifiko invite token para regjistrimit")
    public ResponseEntity<InviteVerifyResponse> verifyInvite(
            @PathVariable String token) {

        return ok(inviteService.verifyInvite(token));
    }

    @PatchMapping("/{token}/use")
    @Operation(summary = "Sheno invite si te perdorur pas regjistrimit")
    public ResponseEntity<Void> markUsed(
            @PathVariable String token) {

        inviteService.markUsed(token);
        return noContent();
    }
}