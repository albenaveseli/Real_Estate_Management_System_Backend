package com.realestate.backend.service;

import com.realestate.backend.dto.maintenance.MaintenanceDtos.*;
import com.realestate.backend.entity.enums.MaintenanceCategory;
import com.realestate.backend.entity.enums.MaintenancePriority;
import com.realestate.backend.entity.enums.MaintenanceStatus;
import com.realestate.backend.entity.enums.NotificationType;
import com.realestate.backend.entity.maintenance.MaintenanceRequest;
import com.realestate.backend.entity.property.Property;
import com.realestate.backend.entity.rental.LeaseContract;
import com.realestate.backend.exception.ForbiddenException;
import com.realestate.backend.exception.ResourceNotFoundException;
import com.realestate.backend.multitenancy.TenantContext;
import com.realestate.backend.repository.LeaseContractRepository;
import com.realestate.backend.repository.MaintenanceRequestRepository;
import com.realestate.backend.repository.PropertyRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock MaintenanceRequestRepository maintenanceRepo;
    @Mock PropertyRepository           propertyRepo;
    @Mock LeaseContractRepository      leaseRepo;
    @Mock NotificationService          notificationService;

    @InjectMocks MaintenanceService maintenanceService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Property property() {
        Property p = new Property();
        p.setId(1L);
        p.setAgentId(10L);
        p.setTitle("Test Property");
        return p;
    }

    private MaintenanceRequest openRequest(Property prop) {
        MaintenanceRequest mr = new MaintenanceRequest();
        mr.setId(300L);
        mr.setProperty(prop);
        mr.setRequestedBy(50L);
        mr.setTitle("Çatia rrjedhë");
        mr.setDescription("Ka lagështi nga çatia");
        mr.setCategory(MaintenanceCategory.PLUMBING);
        mr.setPriority(MaintenancePriority.HIGH);
        mr.setStatus(MaintenanceStatus.OPEN);
        mr.setCreatedAt(LocalDateTime.now());
        mr.setUpdatedAt(LocalDateTime.now());
        return mr;
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getAll
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getAll — ADMIN → kthehet faqja me kërkesa OPEN")
    void getAll_admin_returnsPage() {
        TenantContext.set(1L, 1L, "tenant_1", "ADMIN");

        Property prop = property();
        MaintenanceRequest mr = openRequest(prop);
        Page<MaintenanceRequest> page = new PageImpl<>(List.of(mr));
        Pageable pageable = PageRequest.of(0, 10);

        when(maintenanceRepo.findByStatusOrderByCreatedAtDesc(MaintenanceStatus.OPEN, pageable))
                .thenReturn(page);

        Page<MaintenanceResponse> result =
                maintenanceService.getAll(MaintenanceStatus.OPEN, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(300L);
    }

    @Test
    @DisplayName("getAll — CLIENT → ForbiddenException")
    void getAll_client_throwsForbidden() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        assertThatThrownBy(() ->
                maintenanceService.getAll(MaintenanceStatus.OPEN, PageRequest.of(0, 10))
        ).isInstanceOf(ForbiddenException.class);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getById
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getById — ekziston → kthehet response")
    void getById_found() {
        Property prop = property();
        MaintenanceRequest mr = openRequest(prop);
        when(maintenanceRepo.findById(300L)).thenReturn(Optional.of(mr));

        MaintenanceResponse resp = maintenanceService.getById(300L);

        assertThat(resp.id()).isEqualTo(300L);
        assertThat(resp.status()).isEqualTo(MaintenanceStatus.OPEN);
    }

    @Test
    @DisplayName("getById — nuk ekziston → ResourceNotFoundException")
    void getById_notFound_throws() {
        when(maintenanceRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> maintenanceService.getById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getByProperty
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getByProperty — AGENT → kthehet lista")
    void getByProperty_agent_returnsList() {
        TenantContext.set(10L, 1L, "tenant_1", "AGENT");

        Property prop = property();
        MaintenanceRequest mr = openRequest(prop);
        when(maintenanceRepo.findByProperty_IdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(mr));

        List<MaintenanceResponse> result = maintenanceService.getByProperty(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).propertyId()).isEqualTo(1L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getMyRequests / getAssignedToMe
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getMyRequests — client → kthehet faqja me kërkesat e veta")
    void getMyRequests_client_returnsPage() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        Property prop = property();
        MaintenanceRequest mr = openRequest(prop);
        Page<MaintenanceRequest> page = new PageImpl<>(List.of(mr));
        Pageable pageable = PageRequest.of(0, 10);

        when(maintenanceRepo.findByRequestedByOrderByCreatedAtDesc(50L, pageable))
                .thenReturn(page);

        Page<MaintenanceResponse> result = maintenanceService.getMyRequests(pageable);

        assertThat(result.getContent().get(0).requestedBy()).isEqualTo(50L);
    }
