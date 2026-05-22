package com.realestate.backend.service;

import com.realestate.backend.dto.sale.SaleDtos.*;
import com.realestate.backend.entity.enums.*;
import com.realestate.backend.entity.property.Property;
import com.realestate.backend.entity.sale.SaleContract;
import com.realestate.backend.entity.sale.SaleListing;
import com.realestate.backend.entity.sale.SalePayment;
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
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SaleService — Unit Tests")
class SaleServiceTest {

    // ── Mocks ────────────────────────────────────────────────────
    @Mock SaleListingRepository  listingRepo;
    @Mock SaleContractRepository contractRepo;
    @Mock SalePaymentRepository  paymentRepo;
    @Mock PropertyRepository     propertyRepo;
    @Mock UserRepository         userRepo;
    @Mock LeadRequestRepository  leadRequestRepo;
    @Mock NotificationService    notificationService;

    @InjectMocks
    SaleService saleService;

    // ── Fixtures ─────────────────────────────────────────────────
    private Property     property;
    private SaleListing  activeListing;
    private SaleContract pendingContract;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L, 1L, "tenant_test_1", "ADMIN");

        property = Property.builder()
                .status(PropertyStatus.AVAILABLE)
                .listingType(ListingType.SALE)
                .build();
        setId(property, 10L);

        activeListing = SaleListing.builder()
                .property(property)
                .agentId(1L)
                .price(new BigDecimal("150000"))
                .currency("EUR")
                .negotiable(true)
                .status(SaleStatus.ACTIVE)
                .build();
        setId(activeListing, 100L);
        activeListing.setCreatedAt(LocalDateTime.now());
        activeListing.setUpdatedAt(LocalDateTime.now());

        pendingContract = SaleContract.builder()
                .property(property)
                .listing(activeListing)
                .buyerId(20L)
                .agentId(1L)
                .salePrice(new BigDecimal("150000"))
                .currency("EUR")
                .contractDate(LocalDate.now())
                .handoverDate(LocalDate.now().plusMonths(1))
                .status("PENDING")
                .build();
        setId(pendingContract, 200L);
        pendingContract.setCreatedAt(LocalDateTime.now());
        pendingContract.setUpdatedAt(LocalDateTime.now());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ════════════════════════════════════════════════════════════
    // getListingById()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getListingById()")
    class GetListingById {

        @Test
        @DisplayName("Kthen listing kur ekziston")
        void returnsListing_whenExists() {
            when(listingRepo.findByIdAndDeletedAtIsNull(100L))
                    .thenReturn(Optional.of(activeListing));

            SaleListingResponse result = saleService.getListingById(100L);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(100L);
            assertThat(result.price()).isEqualByComparingTo("150000");
            assertThat(result.status()).isEqualTo(SaleStatus.ACTIVE);
        }

        @Test
        @DisplayName("Hedh ResourceNotFoundException kur nuk ekziston")
        void throwsNotFound_whenMissing() {
            when(listingRepo.findByIdAndDeletedAtIsNull(999L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> saleService.getListingById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ════════════════════════════════════════════════════════════
    // createListing()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createListing()")
    class CreateListing {

        @Test
        @DisplayName("Krijon listing me sukses")
        void createsListing_successfully() {
            SaleListingCreateRequest req = new SaleListingCreateRequest(
                    10L, new BigDecimal("150000"), "EUR",
                    true, "Apartament i ri", "Pamje deti"
            );

            when(propertyRepo.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(property));
            when(listingRepo.save(any(SaleListing.class)))
                    .thenAnswer(inv -> {
                        SaleListing l = inv.getArgument(0);
                        setId(l, 101L);
                        l.setCreatedAt(LocalDateTime.now());
                        l.setUpdatedAt(LocalDateTime.now());
                        return l;
                    });

            SaleListingResponse result = saleService.createListing(req);

            assertThat(result).isNotNull();
            assertThat(result.price()).isEqualByComparingTo("150000");
            assertThat(result.status()).isEqualTo(SaleStatus.ACTIVE);
            verify(listingRepo).save(any(SaleListing.class));
        }

        @Test
        @DisplayName("Hedh BadRequestException për çmim negativ")
        void throwsBadRequest_forNegativePrice() {
            SaleListingCreateRequest req = new SaleListingCreateRequest(
                    10L, new BigDecimal("-100"), "EUR",
                    null, null, null
            );

            assertThatThrownBy(() -> saleService.createListing(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Çmimi");

            verify(propertyRepo, never()).findByIdAndDeletedAtIsNull(any());
        }

        @Test
        @DisplayName("Hedh BadRequestException për currency të pavlefshme")
        void throwsBadRequest_forInvalidCurrency() {
            SaleListingCreateRequest req = new SaleListingCreateRequest(
                    10L, new BigDecimal("150000"), "XYZ",
                    null, null, null
            );

            assertThatThrownBy(() -> saleService.createListing(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Monedha");

            verify(propertyRepo, never()).findByIdAndDeletedAtIsNull(any());
        }

        @Test
        @DisplayName("Hedh ConflictException kur prona është RENT only")
        void throwsConflict_forRentOnlyProperty() {
            property.setListingType(ListingType.RENT);

            SaleListingCreateRequest req = new SaleListingCreateRequest(
                    10L, new BigDecimal("150000"), "EUR",
                    null, null, null
            );

            when(propertyRepo.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(property));

            assertThatThrownBy(() -> saleService.createListing(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("qira");
        }

        @Test
        @DisplayName("Hedh ConflictException kur prona është SOLD")
        void throwsConflict_forSoldProperty() {
            property.setStatus(PropertyStatus.SOLD);

            SaleListingCreateRequest req = new SaleListingCreateRequest(
                    10L, new BigDecimal("150000"), "EUR",
                    null, null, null
            );

            when(propertyRepo.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(property));

            assertThatThrownBy(() -> saleService.createListing(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("shitur");
        }

        @Test
        @DisplayName("Hedh ForbiddenException kur roli është CLIENT")
        void throwsForbidden_whenClient() {
            TenantContext.clear();
            TenantContext.set(20L, 1L, "tenant_test_1", "CLIENT");

            SaleListingCreateRequest req = new SaleListingCreateRequest(
                    10L, new BigDecimal("150000"), "EUR",
                    null, null, null
            );

            assertThatThrownBy(() -> saleService.createListing(req))
                    .isInstanceOf(ForbiddenException.class);

            verify(propertyRepo, never()).findByIdAndDeletedAtIsNull(any());
        }

        @Test
        @DisplayName("Hedh ResourceNotFoundException kur prona nuk ekziston")
        void throwsNotFound_whenPropertyMissing() {
            SaleListingCreateRequest req = new SaleListingCreateRequest(
                    999L, new BigDecimal("150000"), "EUR",
                    null, null, null
            );

            when(propertyRepo.findByIdAndDeletedAtIsNull(999L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> saleService.createListing(req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ════════════════════════════════════════════════════════════
    // updateListing()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateListing()")
    class UpdateListing {

        @Test
        @DisplayName("Update listing me sukses — ADMIN")
        void updatesListing_successfully() {
            SaleListingUpdateRequest req = new SaleListingUpdateRequest(
                    new BigDecimal("160000"), "EUR", true,
                    "Përshkrim i ri", null, SaleStatus.ACTIVE
            );

            when(listingRepo.findByIdAndDeletedAtIsNull(100L))
                    .thenReturn(Optional.of(activeListing));
            when(listingRepo.save(any(SaleListing.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SaleListingResponse result = saleService.updateListing(100L, req);

            assertThat(result.price()).isEqualByComparingTo("160000");
            verify(listingRepo).save(any(SaleListing.class));
        }

        @Test
        @DisplayName("Hedh ForbiddenException kur AGENT mundohet të modifikojë listing të tjetrit")
        void throwsForbidden_whenAgentModifiesOthersListing() {
            TenantContext.clear();
            TenantContext.set(99L, 1L, "tenant_test_1", "AGENT"); // agentId=99, listing.agentId=1

            SaleListingUpdateRequest req = new SaleListingUpdateRequest(
                    new BigDecimal("160000"), null, null, null, null, null
            );

            when(listingRepo.findByIdAndDeletedAtIsNull(100L))
                    .thenReturn(Optional.of(activeListing));

            assertThatThrownBy(() -> saleService.updateListing(100L, req))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("Hedh BadRequestException për çmim negativ")
        void throwsBadRequest_forNegativePrice() {
            SaleListingUpdateRequest req = new SaleListingUpdateRequest(
                    new BigDecimal("-500"), null, null, null, null, null
            );

            when(listingRepo.findByIdAndDeletedAtIsNull(100L))
                    .thenReturn(Optional.of(activeListing));

            assertThatThrownBy(() -> saleService.updateListing(100L, req))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ════════════════════════════════════════════════════════════
    // deleteListing()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteListing()")
    class DeleteListing {

        @Test
        @DisplayName("Fshin listing me sukses — soft delete")
        void deletesListing_successfully() {
            when(listingRepo.findByIdAndDeletedAtIsNull(100L))
                    .thenReturn(Optional.of(activeListing));

            saleService.deleteListing(100L);

            verify(listingRepo).softDelete(100L);
        }

        @Test
        @DisplayName("Hedh ResourceNotFoundException kur listing nuk ekziston")
        void throwsNotFound_whenMissing() {
            when(listingRepo.findByIdAndDeletedAtIsNull(999L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> saleService.deleteListing(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ════════════════════════════════════════════════════════════
    // createContract()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createContract()")
    class CreateContract {

        @Test
        @DisplayName("Krijon kontratë me sukses")
        void createsContract_successfully() {
            SaleContractCreateRequest req = new SaleContractCreateRequest(
                    10L, 100L, 20L,
                    new BigDecimal("150000"), "EUR",
                    LocalDate.now(), LocalDate.now().plusMonths(1),
                    null
            );

            when(contractRepo.findByProperty_IdAndStatus(10L, "PENDING"))
                    .thenReturn(Optional.empty());
            when(propertyRepo.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(property));
            when(contractRepo.findByProperty_IdAndStatus(10L, "COMPLETED"))
                    .thenReturn(Optional.empty());
            when(listingRepo.findByIdAndDeletedAtIsNull(100L))
                    .thenReturn(Optional.of(activeListing));
            when(contractRepo.save(any(SaleContract.class)))
                    .thenAnswer(inv -> {
                        SaleContract c = inv.getArgument(0);
                        setId(c, 201L);
                        c.setCreatedAt(LocalDateTime.now());
                        c.setUpdatedAt(LocalDateTime.now());
                        return c;
                    });

            SaleContractResponse result = saleService.createContract(req);

            assertThat(result).isNotNull();
            assertThat(result.buyerId()).isEqualTo(20L);
            assertThat(result.salePrice()).isEqualByComparingTo("150000");
            assertThat(result.status()).isEqualTo("PENDING");

            verify(contractRepo).save(any(SaleContract.class));
            verify(propertyRepo).updateStatus(10L, PropertyStatus.PENDING);
            verify(notificationService).sendNotification(
                    eq(20L), anyString(), anyString(),
                    any(), anyString(), any(), anyString()
            );
        }

        @Test
        @DisplayName("Hedh ConflictException kur ka kontratë PENDING")
        void throwsConflict_whenPendingExists() {
            SaleContractCreateRequest req = new SaleContractCreateRequest(
                    10L, null, 20L,
                    new BigDecimal("150000"), "EUR",
                    LocalDate.now(), LocalDate.now().plusMonths(1),
                    null
            );

            when(contractRepo.findByProperty_IdAndStatus(10L, "PENDING"))
                    .thenReturn(Optional.of(pendingContract));

            assertThatThrownBy(() -> saleService.createContract(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("PENDING");

            verify(contractRepo, never()).save(any());
        }

        @Test
        @DisplayName("Hedh ConflictException kur prona është SOLD")
        void throwsConflict_whenPropertySold() {
            property.setStatus(PropertyStatus.SOLD);

            SaleContractCreateRequest req = new SaleContractCreateRequest(
                    10L, null, 20L,
                    new BigDecimal("150000"), "EUR",
                    LocalDate.now(), LocalDate.now().plusMonths(1),
                    null
            );

            when(contractRepo.findByProperty_IdAndStatus(10L, "PENDING"))
                    .thenReturn(Optional.empty());
            when(propertyRepo.findByIdAndDeletedAtIsNull(10L))
                    .thenReturn(Optional.of(property));

            assertThatThrownBy(() -> saleService.createContract(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("shitur");
        }

        @Test
        @DisplayName("Hedh BadRequestException kur handoverDate para contractDate")
        void throwsBadRequest_whenHandoverBeforeContract() {
            SaleContractCreateRequest req = new SaleContractCreateRequest(
                    10L, null, 20L,
                    new BigDecimal("150000"), "EUR",
                    LocalDate.now().plusMonths(2),  // contractDate
                    LocalDate.now().plusMonths(1),  // handoverDate < contractDate ❌
                    null
            );

            assertThatThrownBy(() -> saleService.createContract(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("dorëzimit");

            verify(contractRepo, never()).save(any());
        }

        @Test
        @DisplayName("Hedh ForbiddenException kur roli është CLIENT")
        void throwsForbidden_whenClient() {
            TenantContext.clear();
            TenantContext.set(20L, 1L, "tenant_test_1", "CLIENT");

            SaleContractCreateRequest req = new SaleContractCreateRequest(
                    10L, null, 20L,
                    new BigDecimal("150000"), "EUR",
                    LocalDate.now(), LocalDate.now().plusMonths(1),
                    null
            );

            assertThatThrownBy(() -> saleService.createContract(req))
                    .isInstanceOf(ForbiddenException.class);

            verify(contractRepo, never()).save(any());
        }
    }

    // ════════════════════════════════════════════════════════════
    // updateContractStatus()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateContractStatus()")
    class UpdateContractStatus {

        @Test
        @DisplayName("PENDING → COMPLETED — krijon komisione + njofton")
        void completesContract_andCreatesPayments() {
            when(contractRepo.findById(200L))
                    .thenReturn(Optional.of(pendingContract))
                    .thenReturn(Optional.of(pendingContract));
            when(leadRequestRepo.findByPropertyIdOrdered(10L))
                    .thenReturn(Collections.emptyList());
            when(paymentRepo.findByContract_IdOrderByCreatedAtAsc(200L))
                    .thenReturn(Collections.emptyList());
            when(listingRepo.findByProperty_IdAndDeletedAtIsNull(10L))
                    .thenReturn(List.of(activeListing));
            when(paymentRepo.save(any(SalePayment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SaleContractStatusRequest req = new SaleContractStatusRequest("COMPLETED");
            SaleContractResponse result = saleService.updateContractStatus(200L, req);

            assertThat(result).isNotNull();
            verify(contractRepo).updateStatus(200L, "COMPLETED");
            verify(propertyRepo).updateStatus(10L, PropertyStatus.SOLD);
            verify(paymentRepo, atLeast(2)).save(any(SalePayment.class));
            verify(notificationService, times(2))
                    .sendNotification(anyLong(), anyString(), anyString(),
                            any(), anyString(), any(), anyString());
        }

        @Test
        @DisplayName("PENDING → CANCELLED — rikthen property AVAILABLE")
        void cancelsContract_andRevertsProperty() {
            when(contractRepo.findById(200L))
                    .thenReturn(Optional.of(pendingContract))
                    .thenReturn(Optional.of(pendingContract));
            when(contractRepo.findByProperty_IdOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of(pendingContract));

            SaleContractStatusRequest req = new SaleContractStatusRequest("CANCELLED");
            saleService.updateContractStatus(200L, req);

            verify(contractRepo).updateStatus(200L, "CANCELLED");
            verify(propertyRepo).updateStatus(10L, PropertyStatus.AVAILABLE);
        }

        @Test
        @DisplayName("Hedh ConflictException kur kontrata është COMPLETED")
        void throwsConflict_whenAlreadyCompleted() {
            pendingContract.setStatus("COMPLETED");

            when(contractRepo.findById(200L))
                    .thenReturn(Optional.of(pendingContract));

            assertThatThrownBy(() ->
                    saleService.updateContractStatus(200L,
                            new SaleContractStatusRequest("PENDING")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("nuk mund të ndryshohet");
        }

        @Test
        @DisplayName("Hedh BadRequestException kur status është PENDING")
        void throwsBadRequest_whenBackToPending() {
            when(contractRepo.findById(200L))
                    .thenReturn(Optional.of(pendingContract));

            assertThatThrownBy(() ->
                    saleService.updateContractStatus(200L,
                            new SaleContractStatusRequest("PENDING")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("PENDING");
        }

        @Test
        @DisplayName("Hedh BadRequestException për status i pavlefshëm")
        void throwsBadRequest_forInvalidStatus() {
            when(contractRepo.findById(200L))
                    .thenReturn(Optional.of(pendingContract));

            assertThatThrownBy(() ->
                    saleService.updateContractStatus(200L,
                            new SaleContractStatusRequest("INVALID")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("pavlefshëm");
        }
    }

    // ════════════════════════════════════════════════════════════
    // createPayment()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createPayment()")
    class CreatePayment {

        @Test
        @DisplayName("Krijon pagesë DEPOSIT me sukses")
        void createsDepositPayment_successfully() {
            SalePaymentCreateRequest req = new SalePaymentCreateRequest(
                    200L, new BigDecimal("5000"), "EUR",
                    "DEPOSIT", "BANK_TRANSFER", null
            );

            when(contractRepo.findById(200L))
                    .thenReturn(Optional.of(pendingContract));
            when(paymentRepo.save(any(SalePayment.class)))
                    .thenAnswer(inv -> {
                        SalePayment p = inv.getArgument(0);
                        setId(p, 300L);
                        return p;
                    });

            SalePaymentResponse result = saleService.createPayment(req);

            assertThat(result).isNotNull();
            assertThat(result.amount()).isEqualByComparingTo("5000");
            assertThat(result.status()).isEqualTo("PENDING");
            verify(paymentRepo).save(any(SalePayment.class));
        }

        @Test
        @DisplayName("Hedh BadRequestException për shuma negative")
        void throwsBadRequest_forNegativeAmount() {
            SalePaymentCreateRequest req = new SalePaymentCreateRequest(
                    200L, new BigDecimal("-100"), "EUR",
                    "DEPOSIT", null, null
            );

            assertThatThrownBy(() -> saleService.createPayment(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Shuma");

            verify(contractRepo, never()).findById(any());
        }

        @Test
        @DisplayName("Hedh BadRequestException për tip COMMISSION (auto-only)")
        void throwsBadRequest_forAutoOnlyType() {
            SalePaymentCreateRequest req = new SalePaymentCreateRequest(
                    200L, new BigDecimal("5000"), "EUR",
                    "COMMISSION", null, null
            );

            when(contractRepo.findById(200L))
                    .thenReturn(Optional.of(pendingContract));

            assertThatThrownBy(() -> saleService.createPayment(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("automatikisht");
        }

        @Test
        @DisplayName("Hedh InvalidStateException kur kontrata është COMPLETED")
        void throwsInvalidState_whenContractCompleted() {
            pendingContract.setStatus("COMPLETED");

            SalePaymentCreateRequest req = new SalePaymentCreateRequest(
                    200L, new BigDecimal("5000"), "EUR",
                    "DEPOSIT", null, null
            );

            when(contractRepo.findById(200L))
                    .thenReturn(Optional.of(pendingContract));

            assertThatThrownBy(() -> saleService.createPayment(req))
                    .isInstanceOf(InvalidStateException.class)
                    .hasMessageContaining("COMPLETED");
        }

        @Test
        @DisplayName("Hedh ForbiddenException kur roli është CLIENT")
        void throwsForbidden_whenClient() {
            TenantContext.clear();
            TenantContext.set(20L, 1L, "tenant_test_1", "CLIENT");

            SalePaymentCreateRequest req = new SalePaymentCreateRequest(
                    200L, new BigDecimal("5000"), "EUR",
                    "DEPOSIT", null, null
            );

            assertThatThrownBy(() -> saleService.createPayment(req))
                    .isInstanceOf(ForbiddenException.class);

            verify(contractRepo, never()).findById(any());
        }
    }

    // ════════════════════════════════════════════════════════════
    // markPaymentAsPaid()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("markPaymentAsPaid()")
    class MarkPaymentAsPaid {

        @Test
        @DisplayName("Shënon pagesën si PAID me sukses")
        void marksPaymentAsPaid_successfully() {
            SalePayment pending = SalePayment.builder()
                    .contract(pendingContract)
                    .amount(new BigDecimal("5000"))
                    .currency("EUR")
                    .paymentType("DEPOSIT")
                    .status("PENDING")
                    .build();
            setId(pending, 300L);

            SalePaymentMarkPaidRequest req = new SalePaymentMarkPaidRequest(
                    "BANK_TRANSFER", "TXN-001", LocalDate.now()
            );

            when(paymentRepo.findById(300L)).thenReturn(Optional.of(pending));
            when(paymentRepo.save(any(SalePayment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            SalePaymentResponse result = saleService.markPaymentAsPaid(300L, req);

            assertThat(result.status()).isEqualTo("PAID");
            assertThat(result.paymentMethod()).isEqualTo("BANK_TRANSFER");
            verify(paymentRepo).save(any(SalePayment.class));
        }

        @Test
        @DisplayName("Hedh ConflictException kur pagesa është tashmë PAID")
        void throwsConflict_whenAlreadyPaid() {
            SalePayment paid = SalePayment.builder()
                    .contract(pendingContract)
                    .amount(new BigDecimal("5000"))
                    .status("PAID")
                    .build();
            setId(paid, 301L);

            when(paymentRepo.findById(301L)).thenReturn(Optional.of(paid));

            assertThatThrownBy(() ->
                    saleService.markPaymentAsPaid(301L,
                            new SalePaymentMarkPaidRequest(null, null, LocalDate.now())))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("paguar");

            verify(paymentRepo, never()).save(any());
        }

        @Test
        @DisplayName("Hedh BadRequestException kur paidDate është në të ardhmen")
        void throwsBadRequest_whenFuturePaidDate() {
            SalePayment pending = SalePayment.builder()
                    .contract(pendingContract)
                    .amount(new BigDecimal("5000"))
                    .status("PENDING")
                    .build();
            setId(pending, 300L);

            when(paymentRepo.findById(300L)).thenReturn(Optional.of(pending));

            assertThatThrownBy(() ->
                    saleService.markPaymentAsPaid(300L,
                            new SalePaymentMarkPaidRequest(null, null,
                                    LocalDate.now().plusDays(5))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("ardhmen");

            verify(paymentRepo, never()).save(any());
        }
    }

    // ════════════════════════════════════════════════════════════
    // getPaymentsByContract()
    // ════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getPaymentsByContract()")
    class GetPaymentsByContract {

        @Test
        @DisplayName("ADMIN sheh pagesat e kontratës")
        void adminSeesPayments() {
            SalePayment p = SalePayment.builder()
                    .contract(pendingContract)
                    .amount(new BigDecimal("5000"))
                    .currency("EUR")
                    .status("PENDING")
                    .build();
            setId(p, 300L);

            when(contractRepo.findById(200L)).thenReturn(Optional.of(pendingContract));
            when(paymentRepo.findByContract_IdOrderByCreatedAtAsc(200L))
                    .thenReturn(List.of(p));

            var result = saleService.getPaymentsByContract(200L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).amount()).isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("CLIENT sheh vetëm pagesat e kontratave të veta")
        void clientSeesOwnPayments() {
            TenantContext.clear();
            TenantContext.set(20L, 1L, "tenant_test_1", "CLIENT"); // buyerId=20

            when(contractRepo.findById(200L)).thenReturn(Optional.of(pendingContract));
            when(paymentRepo.findByContract_IdOrderByCreatedAtAsc(200L))
                    .thenReturn(Collections.emptyList());

            var result = saleService.getPaymentsByContract(200L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("CLIENT hedh ForbiddenException për kontratë tjetri")
        void clientThrowsForbidden_forOtherContract() {
            TenantContext.clear();
            TenantContext.set(99L, 1L, "tenant_test_1", "CLIENT"); // buyerId=99 ≠ 20

            when(contractRepo.findById(200L)).thenReturn(Optional.of(pendingContract));

            assertThatThrownBy(() -> saleService.getPaymentsByContract(200L))
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
