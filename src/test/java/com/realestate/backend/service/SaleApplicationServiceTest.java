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