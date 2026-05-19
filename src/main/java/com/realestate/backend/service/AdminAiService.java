package com.realestate.backend.service;

import com.realestate.backend.dto.ai.AiDtos.*;
import com.realestate.backend.entity.enums.LeadStatus;
import com.realestate.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AdminAiService {

    private final AiService               aiService;
    private final UserRepository          userRepo;
    private final LeadRequestRepository   leadRepo;
    private final LeaseContractRepository contractRepo;
    private final SaleContractRepository  saleContractRepo;
    private final PaymentRepository       paymentRepo;

    @Transactional(readOnly = true)
    public AgentPerformanceResponse analyzeAgentPerformance(Long agentId) {

        String agentName = userRepo.findFullNameById(agentId)
                .orElse("Agent #" + agentId);

        long totalLeads =
                leadRepo.countByAssignedAgentIdAndStatus(agentId, LeadStatus.DONE)
                        + leadRepo.countByAssignedAgentIdAndStatus(agentId, LeadStatus.IN_PROGRESS)
                        + leadRepo.countByAssignedAgentIdAndStatus(agentId, LeadStatus.NEW);

        long doneLeads = leadRepo.countByAssignedAgentIdAndStatus(
                agentId, LeadStatus.DONE);

        long activeLeases = contractRepo
                .findByAgentIdOrderByCreatedAtDesc(
                        agentId,
                        PageRequest.of(0, Integer.MAX_VALUE))
                .getTotalElements();

        long totalSales = saleContractRepo
                .findByAgentIdOrderByCreatedAtDesc(
                        agentId,
                        PageRequest.of(0, Integer.MAX_VALUE))
                .getTotalElements();

        BigDecimal revenue = paymentRepo.totalRevenue();

        AgentPerformanceRequest req = new AgentPerformanceRequest(
                agentId,
                agentName,
                (int) totalLeads,
                (int) doneLeads,
                (int) activeLeases,
                (int) totalSales,
                revenue != null ? revenue.toPlainString() : "0"
        );

        return aiService.analyzeAgentPerformance(req);
    }
}