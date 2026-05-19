package com.realestate.backend.controller;

import com.realestate.backend.dto.ai.AiDtos.*;
import com.realestate.backend.service.AdminAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
@Tag(name = "Admin AI Analytics")
@SecurityRequirement(name = "BearerAuth")
public class AdminAiController extends BaseController {

    private final AdminAiService adminAiService;

    @GetMapping("/agent/{agentId}/performance")
    @Operation(summary = "Analizë AI e performancës së agjentit (vetëm ADMIN)")
    public ResponseEntity<AgentPerformanceResponse> analyzeAgent(
            @PathVariable Long agentId) {
        return ok(adminAiService.analyzeAgentPerformance(agentId));
    }
}