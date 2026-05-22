package com.realestate.backend.service;

import com.realestate.backend.dto.user.UserProfileDtos.*;
import com.realestate.backend.entity.User;
import com.realestate.backend.exception.*;
import com.realestate.backend.multitenancy.TenantContext;
import com.realestate.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Set<String> VALID_ROLES = Set.of("ADMIN", "AGENT", "CLIENT");

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsersInTenant() {
        assertIsAdmin();
        return userRepository.findAllByTenantId(TenantContext.getTenantId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = findUser(id);
        assertSameUserOrAdmin(user);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getMyProfile() {
        return toResponse(findUser(TenantContext.getUserId()));
    }

    @Transactional
    public UserResponse updateMyProfile(UserUpdateRequest req) {
        User user = findUser(TenantContext.getUserId());

        if (req.firstName() != null && req.firstName().isBlank()) {
            throw new BadRequestException("Emri nuk mund të jetë bosh");
        }
        if (req.lastName() != null && req.lastName().isBlank()) {
            throw new BadRequestException("Mbiemri nuk mund të jetë bosh");
        }
        if (req.firstName() != null && req.firstName().length() > 50) {
            throw new BadRequestException("Emri nuk mund të jetë më i gjatë se 50 karaktere");
        }
        if (req.lastName() != null && req.lastName().length() > 50) {
            throw new BadRequestException("Mbiemri nuk mund të jetë më i gjatë se 50 karaktere");
        }
        if (req.email() != null) {
            if (!req.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw new BadRequestException("Formati i email-it është i pavlefshëm");
            }
            if (req.email().length() > 150) {
                throw new BadRequestException("Email nuk mund të jetë më i gjatë se 150 karaktere");
            }
            if (!req.email().equals(user.getEmail()) && userRepository.existsByEmail(req.email())) {
                throw new ConflictException("Email ekziston tashmë: " + req.email());
            }
        }

        if (req.email()     != null) user.setEmail(req.email().trim().toLowerCase());
        if (req.firstName() != null) user.setFirstName(req.firstName().trim());
        if (req.lastName()  != null) user.setLastName(req.lastName().trim());

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        User user = findUser(TenantContext.getUserId());

        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw new UnauthorizedException("Fjalëkalimi aktual është i gabuar");
        }

        if (req.newPassword().length() < 8) {
            throw new BadRequestException("Fjalëkalimi i ri duhet të ketë minimum 8 karaktere");
        }
        if (!req.newPassword().matches("^(?=.*[A-Za-z])(?=.*\\d).+$")) {
            throw new BadRequestException(
                    "Fjalëkalimi i ri duhet të përmbajë të paktën një shkronjë dhe një numër");
        }
        if (passwordEncoder.matches(req.newPassword(), user.getPassword())) {
            throw new BadRequestException(
                    "Fjalëkalimi i ri nuk mund të jetë i njëjtë me fjalëkalimin aktual");
        }

        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        log.info("Fjalëkalimi u ndryshua për user id={}", user.getId());
    }

    @Transactional
    public UserResponse setUserActive(Long id, UserStatusRequest req) {
        assertIsAdmin();

        User user = findUser(id);

        if (user.getId().equals(TenantContext.getUserId()) && Boolean.FALSE.equals(req.isActive())) {
            throw new ConflictException("Nuk mund të çaktivizoni llogarinë tuaj");
        }

        user.setIsActive(req.isActive());
        log.info("User id={} u {}aktivizua nga admin id={}",
                id, req.isActive() ? "" : "ç", TenantContext.getUserId());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse changeRole(Long id, UserRoleRequest req) {
        assertIsAdmin();

        User user = findUser(id);
        if (user.getId().equals(TenantContext.getUserId())) {
            throw new ConflictException("Nuk mund të ndryshoni rolin tuaj");
        }

        if (req.role() == null) {
            throw new BadRequestException("Roli është i detyrueshëm. Vlerat: " + VALID_ROLES);
        }

        user.setRole(req.role());
        log.info("Roli i user id={} u ndryshua në {} nga admin id={}",
                id, req.role(), TenantContext.getUserId());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        assertIsAdmin();

        User user = findUser(id);

        if (user.getId().equals(TenantContext.getUserId())) {
            throw new ConflictException("Nuk mund të fshini llogarinë tuaj");
        }

        user.setDeletedAt(LocalDateTime.now());
        user.setIsActive(false);
        userRepository.save(user);
        log.info("User id={} u fshi (soft delete) nga admin id={}", id, TenantContext.getUserId());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAgentsInTenant() {
        return userRepository.findAllByTenantId(TenantContext.getTenantId())
                .stream()
                .filter(u -> "AGENT".equalsIgnoreCase(u.getRole().name()))
                .map(this::toResponse)
                .toList();
    }


    private User findUser(Long id) {
        return userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("User nuk u gjet: " + id));
    }

    private void assertIsAdmin() {
        if (!TenantContext.hasRole("ADMIN")) {
            throw new ForbiddenException("Vetëm ADMIN mund të kryejë këtë veprim");
        }
    }

    private void assertSameUserOrAdmin(User user) {
        if (TenantContext.hasRole("ADMIN")) return;
        if (!user.getId().equals(TenantContext.getUserId())) {
            throw new ForbiddenException("Nuk keni leje të shikoni këtë profil");
        }
    }



    private UserResponse toResponse(User u) {
        return new UserResponse(
                u.getId(), u.getEmail(),
                u.getFirstName(), u.getLastName(),
                u.getRole().name(),
                u.getTenant() != null ? u.getTenant().getId() : null,
                u.getIsActive(), u.getCreatedAt()
        );
    }
}