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