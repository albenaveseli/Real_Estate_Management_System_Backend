package com.realestate.backend.service;

import com.realestate.backend.dto.rental.LeaseContractDtos.*;
import com.realestate.backend.entity.enums.LeaseStatus;
import com.realestate.backend.entity.enums.PaymentStatus;
import com.realestate.backend.entity.enums.PaymentType;
import com.realestate.backend.entity.property.Property;
import com.realestate.backend.entity.rental.LeaseContract;
import com.realestate.backend.entity.rental.Payment;
import com.realestate.backend.entity.rental.RentalListing;
import com.realestate.backend.exception.ConflictException;
import com.realestate.backend.exception.ForbiddenException;
import com.realestate.backend.exception.ResourceNotFoundException;
import com.realestate.backend.multitenancy.TenantContext;
import com.realestate.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaseContractService — Unit Tests")
class LeaseContractServiceTest {

    // ── Mocks ────────────────────────────────────────────────────
    @Mock LeaseContractRepository contractRepo;
    @Mock PropertyRepository      propertyRepo;
    @Mock RentalListingRepository  listingRepo;
    @Mock PaymentRepository        paymentRepo;
    @Mock UserRepository           userRepo;
    @Mock LeadRequestRepository    leadRequestRepo;
    @Mock NotificationService      notificationService;
    @Mock DashboardService         dashboardService; // ← SHTUAR

    @InjectMocks
    LeaseContractService contractService;

    // ── Fixtures ─────────────────────────────────────────────────
    private Property      property;
    private LeaseContract activeContract;
    private LeaseContract pendingContract;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L, 1L, "tenant_test_1", "ADMIN");

        property = Property.builder().build();
        setId(property, 10L);

        activeContract = LeaseContract.builder()
                .property(property)
                .clientId(20L)
                .agentId(1L)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(11))
                .rent(new BigDecimal("500.00"))
                .deposit(new BigDecimal("1000.00"))
                .currency("EUR")
                .status(LeaseStatus.ACTIVE)
                .build();
        setId(activeContract, 100L);

        pendingContract = LeaseContract.builder()
                .property(property)
                .clientId(20L)
                .agentId(1L)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusMonths(12))
                .rent(new BigDecimal("600.00"))
                .deposit(new BigDecimal("1200.00"))
                .currency("EUR")
                .status(LeaseStatus.PENDING_SIGNATURE)
                .build();
        setId(pendingContract, 101L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ════════════════════════════════════════════════════════════
    // getById()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("Kthen kontratën kur ekziston")
        void returnsContract_whenExists() {
            when(contractRepo.findById(100L))
                    .thenReturn(Optional.of(activeContract));

            LeaseContractResponse result = contractService.getById(100L);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(100L);
            assertThat(result.clientId()).isEqualTo(20L);
            assertThat(result.rent()).isEqualByComparingTo("500.00");
            assertThat(result.status()).isEqualTo(LeaseStatus.ACTIVE);
        }

        @Test
        @DisplayName("Hedh ResourceNotFoundException kur nuk ekziston")
        void throwsNotFound_whenMissing() {
            when(contractRepo.findById(999L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> contractService.getById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ════════════════════════════════════════════════════════════
    // create()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("Krijon kontratë me sukses")
        void createsContract_successfully() {
            LeaseContractCreateRequest req = new LeaseContractCreateRequest(
                    10L, null, 20L,
                    LocalDate.now().plusDays(1),
                    LocalDate.now().plusMonths(12),
                    new BigDecimal("500.00"),
                    new BigDecimal("1000.00"),
                    "EUR", null
            );

            when(contractRepo.existsOverlappingContract(
                    eq(10L), any(), any())).thenReturn(false);
            when(propertyRepo.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(property));
            when(contractRepo.save(any(LeaseContract.class)))
                    .thenAnswer(inv -> {
                        LeaseContract c = inv.getArgument(0);
                        setId(c, 200L);
                        return c;
                    });

            LeaseContractResponse result = contractService.create(req);

            assertThat(result).isNotNull();
            assertThat(result.clientId()).isEqualTo(20L);
            assertThat(result.rent()).isEqualByComparingTo("500.00");
            assertThat(result.status()).isEqualTo(LeaseStatus.PENDING_SIGNATURE);

            verify(contractRepo).save(any(LeaseContract.class));
            verify(notificationService).sendNotification(
                    eq(20L), anyString(), anyString(),
                    any(), anyString(), any(), anyString()
            );
            verify(dashboardService).evict(); // ← verifiko evict
        }

        @Test
        @DisplayName("Hedh ConflictException kur ka overlap kontrate")
        void throwsConflict_whenOverlappingContract() {
            LeaseContractCreateRequest req = new LeaseContractCreateRequest(
                    10L, null, 20L,
                    LocalDate.now().plusDays(1),
                    LocalDate.now().plusMonths(12),
                    new BigDecimal("500.00"),
                    new BigDecimal("1000.00"),
                    "EUR", null
            );

            when(contractRepo.existsOverlappingContract(
                    eq(10L), any(), any())).thenReturn(true);

            assertThatThrownBy(() -> contractService.create(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("kontratë aktive");

            verify(contractRepo, never()).save(any());
        }

        @Test
        @DisplayName("Hedh ResourceNotFoundException kur prona nuk ekziston")
        void throwsNotFound_whenPropertyMissing() {
            LeaseContractCreateRequest req = new LeaseContractCreateRequest(
                    999L, null, 20L,
                    LocalDate.now().plusDays(1),
                    LocalDate.now().plusMonths(12),
                    new BigDecimal("500.00"),
                    new BigDecimal("1000.00"),
                    "EUR", null
            );

            when(contractRepo.existsOverlappingContract(
                    eq(999L), any(), any())).thenReturn(false);
            when(propertyRepo.findByIdAndDeletedAtIsNull(999L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> contractService.create(req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Hedh ForbiddenException kur roli është CLIENT")
        void throwsForbidden_whenClient() {
            TenantContext.clear();
            TenantContext.set(20L, 1L, "tenant_test_1", "CLIENT");

            LeaseContractCreateRequest req = new LeaseContractCreateRequest(
                    10L, null, 20L,
                    LocalDate.now().plusDays(1),
                    LocalDate.now().plusMonths(12),
                    new BigDecimal("500.00"),
                    new BigDecimal("1000.00"),
                    "EUR", null
            );

            assertThatThrownBy(() -> contractService.create(req))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("Hedh IllegalArgumentException kur endDate para startDate")
        void throwsIllegal_whenEndDateBeforeStart() {
            LeaseContractCreateRequest req = new LeaseContractCreateRequest(
                    10L, null, 20L,
                    LocalDate.now().plusMonths(6),
                    LocalDate.now().plusMonths(1),
                    new BigDecimal("500.00"),
                    new BigDecimal("1000.00"),
                    "EUR", null
            );

            assertThatThrownBy(() -> contractService.create(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("endDate");
        }
    }

    // ════════════════════════════════════════════════════════════
    // updateStatus()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        @Test
        @DisplayName("PENDING_SIGNATURE → ACTIVE krijon pagesat e komisionit")
        void activatesContract_andCreatesPayments() {
            when(contractRepo.findById(101L))
                    .thenReturn(Optional.of(pendingContract))
                    .thenReturn(Optional.of(pendingContract));
            when(leadRequestRepo.findByPropertyIdOrdered(anyLong()))
                    .thenReturn(Collections.emptyList());
            when(paymentRepo.findByContract_IdOrderByDueDateAsc(anyLong()))
                    .thenReturn(Collections.emptyList());
            when(paymentRepo.save(any(Payment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            LeaseStatusRequest req = new LeaseStatusRequest(LeaseStatus.ACTIVE);
            contractService.updateStatus(101L, req);

            verify(contractRepo).updateStatus(101L, LeaseStatus.ACTIVE);
            verify(paymentRepo, atLeast(2)).save(any(Payment.class));
            verify(notificationService, atLeast(2))
                    .sendNotification(anyLong(), anyString(), anyString(),
                            any(), anyString(), any(), anyString());
            verify(dashboardService).evict(); // ← verifiko evict
        }

        @Test
        @DisplayName("ACTIVE → ENDED funksionon")
        void endsActiveContract() {
            when(contractRepo.findById(100L))
                    .thenReturn(Optional.of(activeContract))
                    .thenReturn(Optional.of(activeContract));

            LeaseStatusRequest req = new LeaseStatusRequest(LeaseStatus.ENDED);
            contractService.updateStatus(100L, req);

            verify(contractRepo).updateStatus(100L, LeaseStatus.ENDED);
            verify(dashboardService).evict(); // ← verifiko evict
        }

        @Test
        @DisplayName("Hedh ResourceNotFoundException për kontratë që nuk ekziston")
        void throwsNotFound_forMissingContract() {
            when(contractRepo.findById(999L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    contractService.updateStatus(999L,
                            new LeaseStatusRequest(LeaseStatus.ACTIVE)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ════════════════════════════════════════════════════════════
    // getExpiringSoon()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getExpiringSoon()")
    class GetExpiringSoon {

        @Test
        @DisplayName("Kthen kontratat që skadojnë brenda 30 ditëve")
        void returnsExpiring_withinThirtyDays() {
            LeaseContract expiring = LeaseContract.builder()
                    .property(property)
                    .clientId(20L)
                    .agentId(1L)
                    .startDate(LocalDate.now().minusMonths(11))
                    .endDate(LocalDate.now().plusDays(15))
                    .rent(new BigDecimal("500.00"))
                    .currency("EUR")
                    .status(LeaseStatus.ACTIVE)
                    .build();
            setId(expiring, 102L);

            when(contractRepo.findExpiringContracts(any(), any()))
                    .thenReturn(List.of(expiring));

            var result = contractService.getExpiringSoon();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(102L);
        }

        @Test
        @DisplayName("Kthen listë bosh kur nuk ka kontrata që skadojnë")
        void returnsEmpty_whenNoneExpiring() {
            when(contractRepo.findExpiringContracts(any(), any()))
                    .thenReturn(Collections.emptyList());

            var result = contractService.getExpiringSoon();

            assertThat(result).isEmpty();
        }
    }

    // ════════════════════════════════════════════════════════════
    // getByClient()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getByClient()")
    class GetByClient {

        @Test
        @DisplayName("CLIENT sheh vetëm kontratat e veta")
        void clientSeesOwnContracts() {
            TenantContext.clear();
            TenantContext.set(20L, 1L, "tenant_test_1", "CLIENT");

            Page<LeaseContract> page = new PageImpl<>(List.of(activeContract));
            when(contractRepo.findByClientIdOrderByCreatedAtDesc(eq(20L), any()))
                    .thenReturn(page);

            var result = contractService.getByClient(99L, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            verify(contractRepo)
                    .findByClientIdOrderByCreatedAtDesc(eq(20L), any());
        }
    }

    // ════════════════════════════════════════════════════════════
    // Helper — reflection për të vendosur ID
    // ════════════════════════════════════════════════════════════

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Cannot set id on " + entity.getClass(), e);
        }
    }
}
