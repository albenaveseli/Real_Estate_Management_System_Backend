package com.realestate.backend.service;

import com.realestate.backend.dto.sale.SaleApplicationDtos.*;
import com.realestate.backend.entity.enums.PropertyStatus;
import com.realestate.backend.entity.enums.SaleStatus;
import com.realestate.backend.entity.property.Property;
import com.realestate.backend.entity.sale.SaleApplication;
import com.realestate.backend.entity.sale.SaleListing;
import com.realestate.backend.exception.*;
import com.realestate.backend.multitenancy.TenantContext;
import com.realestate.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleApplicationServiceTest {

    @Mock
    SaleApplicationRepository applicationRepo;
    @Mock
    SaleListingRepository listingRepo;
    @Mock
    PropertyRepository propertyRepo;
    @Mock
    UserRepository userRepo;

    SaleApplicationService saleApplicationService;

    @BeforeEach
    void setUp() {
        saleApplicationService = new SaleApplicationService(
                applicationRepo, listingRepo, propertyRepo, userRepo
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Property activeProperty() {
        Property p = new Property();
        p.setId(1L);
        p.setStatus(PropertyStatus.AVAILABLE);
        return p;
    }

    private SaleListing activeListing(Property property) {
        SaleListing l = new SaleListing();
        l.setId(100L);
        l.setProperty(property);
        l.setAgentId(10L);
        l.setStatus(SaleStatus.ACTIVE);
        l.setPrice(new BigDecimal("150000"));
        l.setCreatedAt(LocalDateTime.now());
        l.setUpdatedAt(LocalDateTime.now());
        return l;
    }

    private SaleApplication pendingApp(SaleListing listing) {
        SaleApplication a = new SaleApplication();
        a.setId(200L);
        a.setListing(listing);
        a.setProperty(listing.getProperty());
        a.setBuyerId(50L);
        a.setAgentId(10L);
        a.setStatus("PENDING");
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        return a;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // createApplication
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("createApplication — sukses")
    void createApplication_success() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        Property prop = activeProperty();
        SaleListing listing = activeListing(prop);
        when(listingRepo.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(listing));
        when(applicationRepo.findByListing_IdAndBuyerIdAndStatusIn(any(), any(), any()))
                .thenReturn(Optional.empty());

        SaleApplication saved = pendingApp(listing);
        when(applicationRepo.save(any())).thenReturn(saved);

        SaleApplicationCreateRequest req = new SaleApplicationCreateRequest(
                100L, "Message", new BigDecimal("145000"),
                LocalDate.now().plusMonths(1), new BigDecimal("3000")
        );

        SaleApplicationResponse resp = saleApplicationService.createApplication(req);

        assertThat(resp.id()).isEqualTo(200L);
        assertThat(resp.status()).isEqualTo("PENDING");
        verify(applicationRepo).save(any());
    }

    @Test
    @DisplayName("createApplication — listing jo aktiv → InvalidStateException")
    void createApplication_inactiveListing_throwsInvalidState() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        Property prop = activeProperty();
        SaleListing listing = activeListing(prop);
        listing.setStatus(SaleStatus.CANCELLED);
        when(listingRepo.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(listing));

        SaleApplicationCreateRequest req = new SaleApplicationCreateRequest(
                100L, null, null, null, null
        );

        assertThatThrownBy(() -> saleApplicationService.createApplication(req))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("aktiv");
    }

    @Test
    @DisplayName("createApplication — prona e shitur → InvalidStateException")
    void createApplication_soldProperty_throwsInvalidState() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        Property prop = activeProperty();
        prop.setStatus(PropertyStatus.SOLD);
        SaleListing listing = activeListing(prop);
        when(listingRepo.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(listing));

        SaleApplicationCreateRequest req = new SaleApplicationCreateRequest(
                100L, null, null, null, null
        );

        assertThatThrownBy(() -> saleApplicationService.createApplication(req))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("disponueshme");
    }

    @Test
    @DisplayName("createApplication — prona me qira → ConflictException")
    void createApplication_rentedProperty_throwsConflict() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        Property prop = activeProperty();
        prop.setStatus(PropertyStatus.RENTED);
        SaleListing listing = activeListing(prop);
        when(listingRepo.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(listing));

        SaleApplicationCreateRequest req = new SaleApplicationCreateRequest(
                100L, null, null, null, null
        );

        assertThatThrownBy(() -> saleApplicationService.createApplication(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("qira");
    }

    @Test
    @DisplayName("createApplication — duplikat PENDING → ConflictException")
    void createApplication_duplicatePending_throwsConflict() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        Property prop = activeProperty();
        SaleListing listing = activeListing(prop);
        SaleApplication existing = pendingApp(listing);

        when(listingRepo.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(listing));
        when(applicationRepo.findByListing_IdAndBuyerIdAndStatusIn(any(), any(), any()))
                .thenReturn(Optional.of(existing));

        SaleApplicationCreateRequest req = new SaleApplicationCreateRequest(
                100L, null, null, null, null
        );

        assertThatThrownBy(() -> saleApplicationService.createApplication(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("aplikim");
    }

    @Test
    @DisplayName("createApplication — listing nuk ekziston → ResourceNotFoundException")
    void createApplication_listingNotFound_throws() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        when(listingRepo.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

        SaleApplicationCreateRequest req = new SaleApplicationCreateRequest(
                999L, null, null, null, null
        );

        assertThatThrownBy(() -> saleApplicationService.createApplication(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getMyApplications
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getMyApplications — kthehet faqja me aplikimet e buyer-it")
    void getMyApplications_returnsPage() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        Property prop = activeProperty();
        SaleListing listing = activeListing(prop);
        SaleApplication app = pendingApp(listing);
        Page<SaleApplication> page = new PageImpl<>(List.of(app));
        Pageable pageable = PageRequest.of(0, 10);

        when(applicationRepo.findByBuyerIdOrderByCreatedAtDesc(50L, pageable)).thenReturn(page);

        Page<SaleApplicationResponse> result = saleApplicationService.getMyApplications(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).buyerId()).isEqualTo(50L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // cancelMyApplication
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("cancelMyApplication — PENDING → anulohet")
    void cancelMyApplication_pending_success() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        Property prop = activeProperty();
        SaleListing listing = activeListing(prop);
        SaleApplication app = pendingApp(listing);
        SaleApplication cancelled = pendingApp(listing);
        cancelled.setStatus("CANCELLED");

        when(applicationRepo.findById(200L))
                .thenReturn(Optional.of(app))
                .thenReturn(Optional.of(cancelled));

        SaleApplicationResponse resp = saleApplicationService.cancelMyApplication(200L);

        assertThat(resp.status()).isEqualTo("CANCELLED");
        verify(applicationRepo).updateStatus(200L, "CANCELLED");
    }

    @Test
    @DisplayName("cancelMyApplication — aplikim i një tjetri → ForbiddenException")
    void cancelMyApplication_notOwner_throwsForbidden() {
        TenantContext.set(99L, 1L, "tenant_1", "CLIENT");

        Property prop = activeProperty();
        SaleListing listing = activeListing(prop);
        SaleApplication app = pendingApp(listing); // buyerId = 50L

        when(applicationRepo.findById(200L)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> saleApplicationService.cancelMyApplication(200L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("përket");
    }

    @Test
    @DisplayName("cancelMyApplication — jo PENDING → ConflictException")
    void cancelMyApplication_notPending_throwsConflict() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        Property prop = activeProperty();
        SaleListing listing = activeListing(prop);
        SaleApplication app = pendingApp(listing);
        app.setStatus("APPROVED");

        when(applicationRepo.findById(200L)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> saleApplicationService.cancelMyApplication(200L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("PENDING");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // updateStatus (admin/agent)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("updateStatus — PENDING → APPROVED sukses")
    void updateStatus_pendingToApproved_success() {
        TenantContext.set(10L, 1L, "tenant_1", "AGENT");

        Property prop = activeProperty();
        SaleListing listing = activeListing(prop);
        SaleApplication app = pendingApp(listing);
        SaleApplication approved = pendingApp(listing);
        approved.setStatus("APPROVED");

        when(applicationRepo.findById(200L))
                .thenReturn(Optional.of(app))
                .thenReturn(Optional.of(approved));
        when(userRepo.findFullNameById(any())).thenReturn(Optional.of("Test User"));

        SaleApplicationStatusRequest req = new SaleApplicationStatusRequest("APPROVED", null);
        SaleApplicationAdminResponse resp = saleApplicationService.updateStatus(200L, req);

        assertThat(resp.status()).isEqualTo("APPROVED");
        verify(applicationRepo).updateStatus(200L, "APPROVED");
    }

    @Test
    @DisplayName("updateStatus — PENDING → REJECTED me arsye")
    void updateStatus_pendingToRejected_withReason() {
        TenantContext.set(10L, 1L, "tenant_1", "AGENT");

        Property prop = activeProperty();
        SaleListing listing = activeListing(prop);
        SaleApplication app = pendingApp(listing);
        SaleApplication rejected = pendingApp(listing);
        rejected.setStatus("REJECTED");
        rejected.setRejectionReason("Dokumentet jo të plota");

        when(applicationRepo.findById(200L))
                .thenReturn(Optional.of(app))
                .thenReturn(Optional.of(rejected));
        when(userRepo.findFullNameById(any())).thenReturn(Optional.of("Test User"));

        SaleApplicationStatusRequest req = new SaleApplicationStatusRequest(
                "REJECTED", "Dokumentet jo të plota"
        );
        SaleApplicationAdminResponse resp = saleApplicationService.updateStatus(200L, req);

        assertThat(resp.status()).isEqualTo("REJECTED");
        verify(applicationRepo).updateStatusWithReason(200L, "REJECTED", "Dokumentet jo të plota");
    }

    @Test
    @DisplayName("updateStatus — aplikim i REJECTED → ConflictException")
    void updateStatus_alreadyRejected_throwsConflict() {
        TenantContext.set(10L, 1L, "tenant_1", "AGENT");

        Property prop = activeProperty();
        SaleListing listing = activeListing(prop);
        SaleApplication app = pendingApp(listing);
        app.setStatus("REJECTED");

        when(applicationRepo.findById(200L)).thenReturn(Optional.of(app));

        SaleApplicationStatusRequest req = new SaleApplicationStatusRequest("APPROVED", null);

        assertThatThrownBy(() -> saleApplicationService.updateStatus(200L, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("nuk mund të ndryshohet");
    }

    @Test
    @DisplayName("updateStatus — përpjekje për PENDING → BadRequestException")
    void updateStatus_backToPending_throwsBadRequest() {
        TenantContext.set(10L, 1L, "tenant_1", "AGENT");

        Property prop = activeProperty();
        SaleListing listing = activeListing(prop);
        SaleApplication app = pendingApp(listing);
        app.setStatus("APPROVED");

        when(applicationRepo.findById(200L)).thenReturn(Optional.of(app));

        SaleApplicationStatusRequest req = new SaleApplicationStatusRequest("PENDING", null);

        assertThatThrownBy(() -> saleApplicationService.updateStatus(200L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("updateStatus — status i pavlefshëm → BadRequestException")
    void updateStatus_invalidStatus_throwsBadRequest() {
        TenantContext.set(10L, 1L, "tenant_1", "AGENT");

        Property prop = activeProperty();
        SaleListing listing = activeListing(prop);
        SaleApplication app = pendingApp(listing);

        when(applicationRepo.findById(200L)).thenReturn(Optional.of(app));

        SaleApplicationStatusRequest req = new SaleApplicationStatusRequest("NONEXISTENT", null);

        assertThatThrownBy(() -> saleApplicationService.updateStatus(200L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Status i pavlefshëm");
    }

    @Test
    @DisplayName("updateStatus — CLIENT → ForbiddenException")
    void updateStatus_client_throwsForbidden() {
        TenantContext.set(50L, 1L, "tenant_1", "CLIENT");

        SaleApplicationStatusRequest req = new SaleApplicationStatusRequest("APPROVED", null);

        assertThatThrownBy(() -> saleApplicationService.updateStatus(200L, req))
                .isInstanceOf(ForbiddenException.class);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getByListing, getByStatus, getById
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getByListing — AGENT → kthehet faqja")
    void getByListing_agent_returnsPage() {
        TenantContext.set(10L, 1L, "tenant_1", "AGENT");

        Property prop = activeProperty();
        SaleListing listing = activeListing(prop);
        SaleApplication app = pendingApp(listing);
        Page<SaleApplication> page = new PageImpl<>(List.of(app));
        Pageable pageable = PageRequest.of(0, 10);

        when(applicationRepo.findByListing_IdOrderByCreatedAtDesc(100L, pageable)).thenReturn(page);
        when(userRepo.findFullNameById(any())).thenReturn(Optional.of("Test User"));

        Page<SaleApplicationAdminResponse> result = saleApplicationService.getByListing(100L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

}