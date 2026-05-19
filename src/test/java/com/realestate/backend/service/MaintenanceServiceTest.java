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

    @Test
    @DisplayName("getAssignedToMe — agent → kthehet faqja me kërkesat e asinjuara")
    void getAssignedToMe_agent_returnsPage() {
        TenantContext.set(10L, 1L, "tenant_1", "AGENT");

        Property prop = property();
        MaintenanceRequest mr = openRequest(prop);
        mr.setAssignedTo(10L);
        Page<MaintenanceRequest> page = new PageImpl<>(List.of(mr));
        Pageable pageable = PageRequest.of(0, 10);

        when(maintenanceRepo.findByAssignedToOrderByCreatedAtDesc(10L, pageable))
                .thenReturn(page);

        Page<MaintenanceResponse> result = maintenanceService.getAssignedToMe(pageable);

        assertThat(result.getContent().get(0).assignedTo()).isEqualTo(10L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // create
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("create — pa lease → krijohet kërkesa dhe njoftim dërguar agjentit")
    void create_withoutLease_success() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        Property prop = property();
        when(propertyRepo.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(prop));

        MaintenanceRequest saved = openRequest(prop);
        when(maintenanceRepo.save(any())).thenReturn(saved);

        MaintenanceCreateRequest req = new MaintenanceCreateRequest(
                1L, null, "Çatia rrjedhë", "Ka lagështi",
                MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH, new BigDecimal("200")
        );

        MaintenanceResponse resp = maintenanceService.create(req);

        assertThat(resp.id()).isEqualTo(300L);
        assertThat(resp.status()).isEqualTo(MaintenanceStatus.OPEN);
        verify(maintenanceRepo).save(any());
        verify(notificationService).sendNotification(
                eq(10L), anyString(), anyString(),
                eq(NotificationType.WARNING), anyString(), eq(300L), anyString()
        );
    }

    @Test
    @DisplayName("create — me lease → krijohet kërkesa me kontratën e lidhur")
    void create_withLease_success() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        Property prop = property();
        LeaseContract lease = new LeaseContract();
        lease.setId(5L);

        when(propertyRepo.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(prop));
        when(leaseRepo.findById(5L)).thenReturn(Optional.of(lease));

        MaintenanceRequest saved = openRequest(prop);
        saved.setLease(lease);
        when(maintenanceRepo.save(any())).thenReturn(saved);

        MaintenanceCreateRequest req = new MaintenanceCreateRequest(
                1L, 5L, "Ngrohja nuk funksionon", "Defekt",
                MaintenanceCategory.HVAC, MaintenancePriority.MEDIUM, null
        );

        MaintenanceResponse resp = maintenanceService.create(req);

        assertThat(resp.leaseId()).isEqualTo(5L);
        verify(leaseRepo).findById(5L);
    }

    @Test
    @DisplayName("create — prona nuk ekziston → ResourceNotFoundException")
    void create_propertyNotFound_throws() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        when(propertyRepo.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

        MaintenanceCreateRequest req = new MaintenanceCreateRequest(
                999L, null, "Title", "Desc", MaintenanceCategory.PLUMBING, null, null
        );

        assertThatThrownBy(() -> maintenanceService.create(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("create — lease nuk ekziston → ResourceNotFoundException")
    void create_leaseNotFound_throws() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        Property prop = property();
        when(propertyRepo.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(prop));
        when(leaseRepo.findById(999L)).thenReturn(Optional.empty());

        MaintenanceCreateRequest req = new MaintenanceCreateRequest(
                1L, 999L, "Title", "Desc", MaintenanceCategory.PLUMBING, null, null
        );

        assertThatThrownBy(() -> maintenanceService.create(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("create — priority null → vendoset MEDIUM si default")
    void create_nullPriority_defaultsMedium() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        Property prop = property();
        when(propertyRepo.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(prop));

        // Kap argumentin e save() për të verifikuar priority-n
        ArgumentCaptor<MaintenanceRequest> captor = ArgumentCaptor.forClass(MaintenanceRequest.class);
        MaintenanceRequest saved = openRequest(prop);
        saved.setPriority(MaintenancePriority.MEDIUM);
        when(maintenanceRepo.save(captor.capture())).thenReturn(saved);

        MaintenanceCreateRequest req = new MaintenanceCreateRequest(
                1L, null, "Title", "Desc", MaintenanceCategory.ELECTRICAL, null, null
        );

        maintenanceService.create(req);

        assertThat(captor.getValue().getPriority()).isEqualTo(MaintenancePriority.MEDIUM);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // update
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("update — ADMIN → ndryshon fushat e dhëna")
    void update_admin_updatesFields() {
        TenantContext.set(1L, 1L, "tenant_1", "ADMIN");

        Property prop = property();
        MaintenanceRequest mr = openRequest(prop);
        when(maintenanceRepo.findById(300L)).thenReturn(Optional.of(mr));
        when(maintenanceRepo.save(any())).thenReturn(mr);

        MaintenanceUpdateRequest req = new MaintenanceUpdateRequest(
                "Titulli i ri", "Përshkrim i ri", MaintenanceCategory.ELECTRICAL,
                MaintenancePriority.LOW, new BigDecimal("150"), new BigDecimal("180")
        );

        MaintenanceResponse resp = maintenanceService.update(300L, req);

        assertThat(resp).isNotNull();
        verify(maintenanceRepo).save(any());
    }

    @Test
    @DisplayName("update — CLIENT → ForbiddenException")
    void update_client_throwsForbidden() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        MaintenanceUpdateRequest req = new MaintenanceUpdateRequest(
                null, null, null, null, null, null
        );

        assertThatThrownBy(() -> maintenanceService.update(300L, req))
                .isInstanceOf(ForbiddenException.class);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // updateStatus
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateStatus — COMPLETED → vendoset completedAt dhe dërgon notifikacion")
    void updateStatus_completed_setsCompletedAtAndNotifies() {
        TenantContext.set(10L, 1L, "tenant_1", "AGENT");

        Property prop = property();
        MaintenanceRequest mr = openRequest(prop);
        mr.setStatus(MaintenanceStatus.IN_PROGRESS);

        when(maintenanceRepo.findById(300L)).thenReturn(Optional.of(mr));
        when(maintenanceRepo.save(any())).thenReturn(mr);

        MaintenanceStatusRequest req = new MaintenanceStatusRequest(
                MaintenanceStatus.COMPLETED, null
        );

        maintenanceService.updateStatus(300L, req);

        assertThat(mr.getCompletedAt()).isNotNull();
        verify(notificationService).sendNotification(
                eq(50L), anyString(), anyString(),
                eq(NotificationType.SUCCESS), anyString(), eq(300L), anyString()
        );
    }

    @Test
    @DisplayName("updateStatus — IN_PROGRESS → nuk dërgon notifikacion")
    void updateStatus_inProgress_noNotification() {
        TenantContext.set(10L, 1L, "tenant_1", "AGENT");

        Property prop = property();
        MaintenanceRequest mr = openRequest(prop);
        when(maintenanceRepo.findById(300L)).thenReturn(Optional.of(mr));
        when(maintenanceRepo.save(any())).thenReturn(mr);

        MaintenanceStatusRequest req = new MaintenanceStatusRequest(
                MaintenanceStatus.IN_PROGRESS, null
        );

        maintenanceService.updateStatus(300L, req);

        assertThat(mr.getCompletedAt()).isNull();
        verify(notificationService, never()).sendNotification(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateStatus — me actualCost → vendoset actualCost")
    void updateStatus_withActualCost_setsActualCost() {
        TenantContext.set(10L, 1L, "tenant_1", "AGENT");

        Property prop = property();
        MaintenanceRequest mr = openRequest(prop);
        when(maintenanceRepo.findById(300L)).thenReturn(Optional.of(mr));
        when(maintenanceRepo.save(any())).thenReturn(mr);

        MaintenanceStatusRequest req = new MaintenanceStatusRequest(
                MaintenanceStatus.IN_PROGRESS, new BigDecimal("350")
        );

        maintenanceService.updateStatus(300L, req);

        assertThat(mr.getActualCost()).isEqualByComparingTo("350");
    }

    @Test
    @DisplayName("updateStatus — CLIENT → ForbiddenException")
    void updateStatus_client_throwsForbidden() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        MaintenanceStatusRequest req = new MaintenanceStatusRequest(
                MaintenanceStatus.COMPLETED, null
        );

        assertThatThrownBy(() -> maintenanceService.updateStatus(300L, req))
                .isInstanceOf(ForbiddenException.class);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // assign
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("assign — ADMIN → asinjoi dhe vendosi IN_PROGRESS, notifikacion dërguar")
    void assign_admin_success() {
        TenantContext.set(1L, 1L, "tenant_1", "ADMIN");

        Property prop = property();
        MaintenanceRequest mr = openRequest(prop);
        when(maintenanceRepo.findById(300L)).thenReturn(Optional.of(mr));
        when(maintenanceRepo.save(any())).thenReturn(mr);

        MaintenanceAssignRequest req = new MaintenanceAssignRequest(20L);

        MaintenanceResponse resp = maintenanceService.assign(300L, req);

        assertThat(mr.getAssignedTo()).isEqualTo(20L);
        assertThat(mr.getStatus()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
        verify(notificationService).sendNotification(
                eq(20L), anyString(), anyString(),
                eq(NotificationType.REMINDER), anyString(), eq(300L), anyString()
        );
    }

    @Test
    @DisplayName("assign — CLIENT → ForbiddenException")
    void assign_client_throwsForbidden() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        MaintenanceAssignRequest req = new MaintenanceAssignRequest(20L);

        assertThatThrownBy(() -> maintenanceService.assign(300L, req))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("assign — kërkesa nuk ekziston → ResourceNotFoundException")
    void assign_notFound_throws() {
        TenantContext.set(1L, 1L, "tenant_1", "ADMIN");

        when(maintenanceRepo.findById(999L)).thenReturn(Optional.empty());

        MaintenanceAssignRequest req = new MaintenanceAssignRequest(20L);

        assertThatThrownBy(() -> maintenanceService.assign(999L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getUrgentOpen
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getUrgentOpen — AGENT → kthehet lista urgjente")
    void getUrgentOpen_agent_returnsList() {
        TenantContext.set(10L, 1L, "tenant_1", "AGENT");

        Property prop = property();
        MaintenanceRequest mr = openRequest(prop);
        mr.setPriority(MaintenancePriority.URGENT);
        when(maintenanceRepo.findUrgentOpen()).thenReturn(List.of(mr));

        List<MaintenanceResponse> result = maintenanceService.getUrgentOpen();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).priority()).isEqualTo(MaintenancePriority.URGENT);
    }

    @Test
    @DisplayName("getUrgentOpen — CLIENT → ForbiddenException")
    void getUrgentOpen_client_throwsForbidden() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        assertThatThrownBy(() -> maintenanceService.getUrgentOpen())
                .isInstanceOf(ForbiddenException.class);
    }
}
