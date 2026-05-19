package com.realestate.backend.service;

import com.realestate.backend.dto.rental.RentalDtos.*;
import com.realestate.backend.entity.enums.ListingType;
import com.realestate.backend.entity.enums.NotificationType;
import com.realestate.backend.entity.enums.PropertyStatus;
import com.realestate.backend.entity.enums.RentalApplicationStatus;
import com.realestate.backend.entity.property.Property;
import com.realestate.backend.entity.rental.RentalApplication;
import com.realestate.backend.entity.rental.RentalListing;
import com.realestate.backend.exception.BadRequestException;
import com.realestate.backend.exception.ConflictException;
import com.realestate.backend.exception.ForbiddenException;
import com.realestate.backend.exception.ResourceNotFoundException;
import com.realestate.backend.multitenancy.TenantContext;
import com.realestate.backend.repository.PropertyRepository;
import com.realestate.backend.repository.RentalApplicationRepository;
import com.realestate.backend.repository.RentalListingRepository;
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
class RentalServiceTest {

    @Mock
    RentalListingRepository listingRepo;
    @Mock
    RentalApplicationRepository applicationRepo;
    @Mock
    PropertyRepository propertyRepo;
    @Mock
    NotificationService notificationService;

    RentalService rentalService;

    @BeforeEach
    void setUp() {
        rentalService = new RentalService(listingRepo, applicationRepo, propertyRepo, notificationService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Property activeProperty() {
        Property p = new Property();
        p.setId(1L);
        p.setListingType(ListingType.RENT);
        p.setStatus(PropertyStatus.AVAILABLE);
        p.setAgentId(10L);
        p.setTitle("Test Property");
        return p;
    }

    private RentalListing activeListing(Property property) {
        RentalListing l = new RentalListing();
        l.setId(100L);
        l.setProperty(property);
        l.setAgentId(10L);
        l.setTitle("Test Listing");
        l.setPrice(new BigDecimal("500.00"));
        l.setCurrency("EUR");
        l.setPricePeriod("MONTHLY");
        l.setMinLeaseMonths(12);
        l.setStatus("ACTIVE");
        l.setAvailableFrom(LocalDate.now().minusDays(10));
        l.setAvailableUntil(LocalDate.now().plusMonths(6));
        l.setCreatedAt(LocalDateTime.now());
        l.setUpdatedAt(LocalDateTime.now());
        return l;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getAllListings
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getAllListings — kthehet faqja me listings")
    void getAllListings_returnsPage() {
        Property prop = activeProperty();
        RentalListing listing = activeListing(prop);
        Page<RentalListing> page = new PageImpl<>(List.of(listing));
        Pageable pageable = PageRequest.of(0, 10);

        when(listingRepo.findAllByDeletedAtIsNull(pageable)).thenReturn(page);

        Page<RentalListingResponse> result = rentalService.getAllListings(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(100L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // getListingById
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getListingById — listing ekziston → kthehet response")
    void getListingById_found() {
        Property prop = activeProperty();
        RentalListing listing = activeListing(prop);
        when(listingRepo.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(listing));

        RentalListingResponse resp = rentalService.getListingById(100L);

        assertThat(resp.id()).isEqualTo(100L);
        assertThat(resp.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("getListingById — listing nuk ekziston → ResourceNotFoundException")
    void getListingById_notFound() {
        when(listingRepo.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rentalService.getListingById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}