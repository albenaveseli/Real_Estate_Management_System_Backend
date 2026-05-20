package com.realestate.backend.service;

import com.realestate.backend.entity.enums.LeadStatus;
import com.realestate.backend.entity.enums.NotificationType;
import com.realestate.backend.entity.tenant.TenantSchemaRegistry;
import com.realestate.backend.repository.LeadRequestRepository;
import com.realestate.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.realestate.backend.multitenancy.TenantContext;
import com.realestate.backend.repository.SchemaRegistryRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final PaymentService           paymentService;
    private final LeaseContractService     leaseContractService;
    private final SchemaRegistryRepository schemaRegistryRepo;
    private final LeadRequestRepository    leadRequestRepository;
    private final NotificationService      notificationService;
    private final UserRepository           userRepository;
    private final DashboardService         dashboardService;

    @Scheduled(cron = "0 0 0 * * *")
    public void markOverduePayments() {
        for (var schema : activeSchemas()) {
            try {
                TenantContext.set(null, null, schema.getSchemaName(), "SYSTEM");
                int count = paymentService.markOverduePayments();

                if (count > 0) {
                    notifyTenantAdmins(
                            schema.getTenant().getId(),
                            schema.getSchemaName(),
                            "⏰ Overdue Payments Detected",
                            count + " payment(s) marked as overdue.",
                            NotificationType.WARNING,
                            "payment", null,
                            "/admin/payments"
                    );
                    dashboardService.evictAll();
                }

                log.info("[Scheduler] Schema={} — {} payments marked OVERDUE",
                        schema.getSchemaName(), count);

            } catch (Exception e) {
                log.error("[Scheduler] Error for schema={}: {}",
                        schema.getSchemaName(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void checkExpiringContracts() {
        for (var schema : activeSchemas()) {
            try {
                TenantContext.set(null, null, schema.getSchemaName(), "SYSTEM");
                var expiring = leaseContractService.getExpiringSoon();

                if (!expiring.isEmpty()) {
                    notifyTenantAdmins(
                            schema.getTenant().getId(),
                            schema.getSchemaName(),
                            "📋 Contracts Expiring Soon",
                            expiring.size() + " lease contract(s) expire within 30 days.",
                            NotificationType.REMINDER,
                            "lease_contract", null,
                            "/admin/contracts"
                    );
                    log.warn("[Scheduler] Schema={} — {} contracts expiring",
                            schema.getSchemaName(), expiring.size());
                }

            } catch (Exception e) {
                log.error("[Scheduler] Error for schema={}: {}",
                        schema.getSchemaName(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Scheduled(fixedDelay = 21600000)
    public void logSystemStats() {
        for (var schema : activeSchemas()) {
            try {
                TenantContext.set(null, null, schema.getSchemaName(), "SYSTEM");
                long active = leaseContractService.countActive();
                log.info("[Scheduler] Schema={} — Active leases: {}",
                        schema.getSchemaName(), active);
            } catch (Exception e) {
                log.error("[Scheduler] Error for schema={}: {}",
                        schema.getSchemaName(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void healthCheck() {
        log.debug("[Scheduler] Running — schemas active: {}",
                activeSchemas().size());
    }

    @Scheduled(cron = "0 0 9 * * MON")
    public void weeklyAdminReport() {
        for (var schema : activeSchemas()) {
            try {
                TenantContext.set(null, null, schema.getSchemaName(), "SYSTEM");

                int  overdueCount    = paymentService.markOverduePayments();
                var  expiring        = leaseContractService.getExpiringSoon();
                long unassignedLeads = leadRequestRepository.countByStatus(LeadStatus.NEW);

                String msg = String.format(
                        "Weekly Report: %d overdue payments · %d expiring contracts · %d unassigned leads",
                        overdueCount, expiring.size(), unassignedLeads
                );

                notifyTenantAdmins(
                        schema.getTenant().getId(),
                        schema.getSchemaName(),
                        "📊 Weekly System Report",
                        msg,
                        NotificationType.INFO,
                        null, null,
                        "/admin/background-jobs"
                );

                if (overdueCount > 0) dashboardService.evictAll(); // ← SHTO

                log.info("[WeeklyReport] Schema={} — {}",
                        schema.getSchemaName(), msg);

            } catch (Exception e) {
                log.error("[WeeklyReport] Error for schema={}: {}",
                        schema.getSchemaName(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }


    private void notifyTenantAdmins(Long tenantId, String schemaName,
                                    String title, String message,
                                    NotificationType type,
                                    String entityType, Long entityId,
                                    String actionUrl) {
        try {
            userRepository.findAllByTenantId(tenantId).stream()
                    .filter(u -> u.getRole().name().equals("ADMIN"))
                    .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                    .filter(u -> u.getDeletedAt() == null)
                    .forEach(admin ->
                            notificationService.sendNotification(
                                    admin.getId(), title, message,
                                    type, entityType, entityId, actionUrl
                            )
                    );
        } catch (Exception e) {
            log.error("[Scheduler] Failed to notify admins for schema={}: {}",
                    schemaName, e.getMessage());
        }
    }

    private List<TenantSchemaRegistry> activeSchemas() {
        return schemaRegistryRepo.findAll()
                .stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsProvisioned()))
                .toList();
    }
}