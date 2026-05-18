package com.realestate.backend.repository;

import com.realestate.backend.entity.tenant.TenantCompany;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<TenantCompany, Long> {

    Optional<TenantCompany> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByName(String name);

}