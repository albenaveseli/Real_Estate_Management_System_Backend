package com.realestate.backend.service;

import com.realestate.backend.dto.rental.PaymentDtos.*;
import com.realestate.backend.entity.enums.PaymentStatus;
import com.realestate.backend.entity.enums.PaymentType;
import com.realestate.backend.entity.rental.LeaseContract;
import com.realestate.backend.entity.rental.Payment;
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
@DisplayName("PaymentService — Unit Tests")
class PaymentServiceTest {

    // ── Mocks ────────────────────────────────────────────────────
    @Mock PaymentRepository       paymentRepo;
    @Mock LeaseContractRepository contractRepo;
    @Mock UserRepository          userRepo;
    @Mock DashboardService        dashboardService;

    @InjectMocks
    PaymentService paymentService;

    // ── Fixtures ─────────────────────────────────────────────────
    private LeaseContract contract;
    private Payment       pendingPayment;
    private Payment       paidPayment;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L, 1L, "tenant_test_1", "ADMIN");

        contract = LeaseContract.builder()
                .clientId(20L)
                .agentId(1L)
                .rent(new BigDecimal("500.00"))
                .currency("EUR")
                .status(com.realestate.backend.entity.enums.LeaseStatus.ACTIVE)
                .build();
        setId(contract, 50L);

        pendingPayment = Payment.builder()
                .contract(contract)
                .amount(new BigDecimal("500.00"))
                .currency("EUR")
                .paymentType(PaymentType.RENT)
                .dueDate(LocalDate.now().plusDays(5))
                .status(PaymentStatus.PENDING)
                .build();
        setId(pendingPayment, 200L);

        paidPayment = Payment.builder()
                .contract(contract)
                .amount(new BigDecimal("500.00"))
                .currency("EUR")
                .paymentType(PaymentType.RENT)
                .dueDate(LocalDate.now().minusDays(10))
                .paidDate(LocalDate.now().minusDays(9))
                .status(PaymentStatus.PAID)
                .build();
        setId(paidPayment, 201L);
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
        @DisplayName("Kthen pagesën kur ekziston")
        void returnsPayment_whenExists() {
            when(paymentRepo.findById(200L))
                    .thenReturn(Optional.of(pendingPayment));

            PaymentResponse result = paymentService.getById(200L);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(200L);
            assertThat(result.amount()).isEqualByComparingTo("500.00");
            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("Hedh ResourceNotFoundException kur nuk ekziston")
        void throwsNotFound_whenMissing() {
            when(paymentRepo.findById(999L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ════════════════════════════════════════════════════════════
    // getByContract()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getByContract()")
    class GetByContract {

        @Test
        @DisplayName("Kthen pagesat e kontratës")
        void returnsPayments_forContract() {
            when(paymentRepo.findByContract_IdOrderByDueDateAsc(50L))
                    .thenReturn(List.of(pendingPayment, paidPayment));

            var result = paymentService.getByContract(50L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).status()).isEqualTo(PaymentStatus.PENDING);
            assertThat(result.get(1).status()).isEqualTo(PaymentStatus.PAID);
        }

        @Test
        @DisplayName("Kthen listë bosh kur kontrata nuk ka pagesa")
        void returnsEmpty_whenNoPayments() {
            when(paymentRepo.findByContract_IdOrderByDueDateAsc(50L))
                    .thenReturn(Collections.emptyList());

            var result = paymentService.getByContract(50L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Hedh IllegalArgumentException për contractId null")
        void throwsIllegal_forNullContractId() {
            assertThatThrownBy(() -> paymentService.getByContract(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ════════════════════════════════════════════════════════════
    // create()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("Krijon pagesë me sukses")
        void createsPayment_successfully() {
            PaymentCreateRequest req = new PaymentCreateRequest(
                    50L,
                    new BigDecimal("500.00"),
                    "EUR",
                    PaymentType.RENT,
                    LocalDate.now().plusDays(30),
                    "BANK_TRANSFER",
                    null,
                    "Pagesa e qirasë"
            );

            // validateCreate() kalon → contractRepo thirret
            when(contractRepo.findById(50L))
                    .thenReturn(Optional.of(contract));
            when(paymentRepo.save(any(Payment.class)))
                    .thenAnswer(inv -> {
                        Payment p = inv.getArgument(0);
                        setId(p, 300L);
                        return p;
                    });

            PaymentResponse result = paymentService.create(req);

            assertThat(result).isNotNull();
            assertThat(result.amount()).isEqualByComparingTo("500.00");
            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
            assertThat(result.currency()).isEqualTo("EUR");

            verify(paymentRepo).save(any(Payment.class));
            verify(dashboardService).evict();
        }

        @Test
        @DisplayName("Hedh IllegalArgumentException për amount negativ")
        void throwsIllegal_forNegativeAmount() {
            // amount < 0 → validateCreate hedh para contractRepo
            PaymentCreateRequest req = new PaymentCreateRequest(
                    50L,
                    new BigDecimal("-100.00"),
                    "EUR",
                    PaymentType.RENT,
                    LocalDate.now().plusDays(30),
                    null, null, null
            );

            assertThatThrownBy(() -> paymentService.create(req))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(contractRepo, never()).findById(any());
            verify(paymentRepo, never()).save(any());
        }

        @Test
        @DisplayName("Hedh IllegalArgumentException për currency të pavlefshme")
        void throwsIllegal_forInvalidCurrency() {
            // currency invalide → validateCreate hedh para contractRepo
            PaymentCreateRequest req = new PaymentCreateRequest(
                    50L,
                    new BigDecimal("500.00"),
                    "XYZ",
                    PaymentType.RENT,
                    LocalDate.now().plusDays(30),
                    null, null, null
            );

            // NUK ka when(contractRepo...) — validateCreate hedh para tij
            assertThatThrownBy(() -> paymentService.create(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency");

            verify(contractRepo, never()).findById(any());
            verify(paymentRepo, never()).save(any());
        }

        @Test
        @DisplayName("Hedh ForbiddenException kur roli është CLIENT")
        void throwsForbidden_whenClient() {
            TenantContext.clear();
            TenantContext.set(20L, 1L, "tenant_test_1", "CLIENT");

            // assertIsAdminOrAgent hedh para validateCreate
            PaymentCreateRequest req = new PaymentCreateRequest(
                    50L, new BigDecimal("500.00"), "EUR",
                    PaymentType.RENT,
                    LocalDate.now().plusDays(30),
                    null, null, null
            );

            assertThatThrownBy(() -> paymentService.create(req))
                    .isInstanceOf(ForbiddenException.class);

            verify(contractRepo, never()).findById(any());
        }

        @Test
        @DisplayName("Hedh ResourceNotFoundException kur kontrata nuk ekziston")
        void throwsNotFound_whenContractMissing() {
            // validateCreate kalon → contractRepo thirret → nuk gjen
            PaymentCreateRequest req = new PaymentCreateRequest(
                    999L, new BigDecimal("500.00"), "EUR",
                    PaymentType.RENT,
                    LocalDate.now().plusDays(30),
                    null, null, null
            );

            when(contractRepo.findById(999L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.create(req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ════════════════════════════════════════════════════════════
    // markAsPaid()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("markAsPaid()")
    class MarkAsPaid {

        @Test
        @DisplayName("Shënon pagesën PENDING si PAID me sukses")
        void marksPaid_forPendingPayment() {
            PaymentMarkPaidRequest req = new PaymentMarkPaidRequest(
                    "BANK_TRANSFER", "TXN-001",
                    LocalDate.now()
            );

            when(paymentRepo.findById(200L))
                    .thenReturn(Optional.of(pendingPayment));
            when(paymentRepo.save(any(Payment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse result = paymentService.markAsPaid(200L, req);

            assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
            assertThat(result.paymentMethod()).isEqualTo("BANK_TRANSFER");

            verify(paymentRepo).save(any(Payment.class));
            verify(dashboardService).evict();
        }

        @Test
        @DisplayName("Hedh ConflictException kur pagesa është tashmë PAID")
        void throwsConflict_whenAlreadyPaid() {
            PaymentMarkPaidRequest req = new PaymentMarkPaidRequest(
                    "CASH", null, LocalDate.now()
            );

            when(paymentRepo.findById(201L))
                    .thenReturn(Optional.of(paidPayment));

            assertThatThrownBy(() -> paymentService.markAsPaid(201L, req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("paguar");

            verify(paymentRepo, never()).save(any());
        }

        @Test
        @DisplayName("Hedh IllegalArgumentException kur paidDate është në të ardhmen")
        void throwsIllegal_whenFuturePaidDate() {
            PaymentMarkPaidRequest req = new PaymentMarkPaidRequest(
                    "CASH", null,
                    LocalDate.now().plusDays(5)
            );

            when(paymentRepo.findById(200L))
                    .thenReturn(Optional.of(pendingPayment));

            assertThatThrownBy(() -> paymentService.markAsPaid(200L, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ardhmen");

            verify(paymentRepo, never()).save(any());
        }

        @Test
        @DisplayName("Vendos paidDate = today kur nuk jepet")
        void usesTodayAsPaidDate_whenNull() {
            PaymentMarkPaidRequest req = new PaymentMarkPaidRequest(
                    "CASH", null, null
            );

            when(paymentRepo.findById(200L))
                    .thenReturn(Optional.of(pendingPayment));
            when(paymentRepo.save(any(Payment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse result = paymentService.markAsPaid(200L, req);

            assertThat(result.paidDate()).isEqualTo(LocalDate.now());
        }
    }

    // ════════════════════════════════════════════════════════════
    // updateStatus()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        @Test
        @DisplayName("Ndrysho statusin nga PENDING në OVERDUE")
        void updatesStatus_toOverdue() {
            PaymentStatusRequest req = new PaymentStatusRequest(PaymentStatus.OVERDUE);

            when(paymentRepo.findById(200L))
                    .thenReturn(Optional.of(pendingPayment));
            when(paymentRepo.save(any(Payment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PaymentResponse result = paymentService.updateStatus(200L, req);

            assertThat(result.status()).isEqualTo(PaymentStatus.OVERDUE);
            verify(dashboardService).evict();
        }

        @Test
        @DisplayName("Hedh IllegalArgumentException për status null")
        void throwsIllegal_forNullStatus() {
            // status null → hedh para findPayment
            assertThatThrownBy(() ->
                    paymentService.updateStatus(200L,
                            new PaymentStatusRequest(null)))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(paymentRepo, never()).findById(any());
        }
    }

    // ════════════════════════════════════════════════════════════
    // getOverdue()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getOverdue()")
    class GetOverdue {

        @Test
        @DisplayName("Kthen pagesat e vonuara")
        void returnsOverduePayments() {
            Payment overduePayment = Payment.builder()
                    .contract(contract)
                    .amount(new BigDecimal("500.00"))
                    .currency("EUR")
                    .paymentType(PaymentType.RENT)
                    .dueDate(LocalDate.now().minusDays(5))
                    .status(PaymentStatus.PENDING)
                    .build();
            setId(overduePayment, 202L);

            when(paymentRepo.findOverduePayments(any(LocalDate.class)))
                    .thenReturn(List.of(overduePayment));

            var result = paymentService.getOverdue();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(202L);
        }
    }

    // ════════════════════════════════════════════════════════════
    // getSummaryByContract()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getSummaryByContract()")
    class GetSummary {

        @Test
        @DisplayName("Kthen summary të saktë")
        void returnsSummary_correctly() {
            when(paymentRepo.findByContract_IdOrderByDueDateAsc(50L))
                    .thenReturn(List.of(pendingPayment, paidPayment));
            when(paymentRepo.totalPaidByContract(50L))
                    .thenReturn(new BigDecimal("500.00"));

            PaymentSummaryResponse result =
                    paymentService.getSummaryByContract(50L);

            assertThat(result.totalPayments()).isEqualTo(2);
            assertThat(result.totalPaid()).isEqualByComparingTo("500.00");
            assertThat(result.payments()).hasSize(2);
        }
    }

    // ════════════════════════════════════════════════════════════
    // markOverduePayments() — Scheduler
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("markOverduePayments() — Scheduler")
    class MarkOverdue {

        @Test
        @DisplayName("Shënon pagesat e vonuara si OVERDUE dhe kthen numrin")
        void marksOverdue_andReturnsCount() {
            Payment p1 = Payment.builder()
                    .contract(contract)
                    .amount(new BigDecimal("500.00"))
                    .dueDate(LocalDate.now().minusDays(3))
                    .status(PaymentStatus.PENDING)
                    .build();
            Payment p2 = Payment.builder()
                    .contract(contract)
                    .amount(new BigDecimal("300.00"))
                    .dueDate(LocalDate.now().minusDays(1))
                    .status(PaymentStatus.PENDING)
                    .build();

            when(paymentRepo.findOverduePayments(any()))
                    .thenReturn(List.of(p1, p2));
            when(paymentRepo.saveAll(anyList()))
                    .thenReturn(List.of(p1, p2));

            int count = paymentService.markOverduePayments();

            assertThat(count).isEqualTo(2);
            assertThat(p1.getStatus()).isEqualTo(PaymentStatus.OVERDUE);
            assertThat(p2.getStatus()).isEqualTo(PaymentStatus.OVERDUE);
            verify(paymentRepo).saveAll(anyList());
            verify(dashboardService).evict();
        }

        @Test
        @DisplayName("Kthen 0 kur nuk ka pagesa të vonuara")
        void returnsZero_whenNoOverdue() {
            when(paymentRepo.findOverduePayments(any()))
                    .thenReturn(Collections.emptyList());
            when(paymentRepo.saveAll(anyList()))
                    .thenReturn(Collections.emptyList());

            int count = paymentService.markOverduePayments();

            assertThat(count).isEqualTo(0);
        }
    }

    // ════════════════════════════════════════════════════════════
    // getTotalRevenue()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getTotalRevenue()")
    class GetTotalRevenue {

        @Test
        @DisplayName("Kthen totalin e të ardhurave")
        void returnsTotalRevenue() {
            when(paymentRepo.totalRevenue())
                    .thenReturn(new BigDecimal("15000.00"));

            BigDecimal result = paymentService.getTotalRevenue();

            assertThat(result).isEqualByComparingTo("15000.00");
        }

        @Test
        @DisplayName("Hedh ForbiddenException kur roli është CLIENT")
        void throwsForbidden_whenClient() {
            TenantContext.clear();
            TenantContext.set(20L, 1L, "tenant_test_1", "CLIENT");

            assertThatThrownBy(() -> paymentService.getTotalRevenue())
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    // ════════════════════════════════════════════════════════════
    // Helper
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