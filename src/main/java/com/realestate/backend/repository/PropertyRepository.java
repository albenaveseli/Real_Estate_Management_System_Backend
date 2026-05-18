package com.realestate.backend.repository;


import com.realestate.backend.entity.enums.ListingType;
import com.realestate.backend.entity.enums.PropertyStatus;
import com.realestate.backend.entity.enums.PropertyType;
import com.realestate.backend.entity.property.Property;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;


@Repository
public interface PropertyRepository
        extends JpaRepository<Property, Long>,
        JpaSpecificationExecutor<Property> {


    Optional<Property> findByIdAndDeletedAtIsNull(Long id);

    Page<Property> findAllByDeletedAtIsNull(Pageable pageable);


    List<Property> findByIsFeaturedTrueAndDeletedAtIsNull();


    Page<Property> findByAgentIdAndDeletedAtIsNull(
            Long agentId, Pageable pageable);


    @Query(
            value = """
        SELECT * FROM properties
        WHERE search_vector @@ plainto_tsquery('simple', :keyword)
          AND deleted_at IS NULL
        ORDER BY ts_rank(search_vector, plainto_tsquery('simple', :keyword)) DESC
        """,
            countQuery = """
        SELECT COUNT(*) FROM properties
        WHERE search_vector @@ plainto_tsquery('simple', :keyword)
          AND deleted_at IS NULL
        """,
            nativeQuery = true
    )
    Page<Property> fullTextSearch(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
        SELECT COUNT(p) FROM Property p
        WHERE p.status = :status
          AND p.deletedAt IS NULL
    """)
    Long countByStatus(@Param("status") PropertyStatus status);


    @Modifying
    @Query("""
        UPDATE Property p
        SET p.status = :status, p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.id = :id
    """)
    void updateStatus(@Param("id") Long id,
                      @Param("status") PropertyStatus status);


    @Modifying
    @Query("""
        UPDATE Property p
        SET p.viewCount = p.viewCount + 1
        WHERE p.id = :id
    """)
    void incrementViewCount(@Param("id") Long id);


    @Modifying
    @Query("""
        UPDATE Property p
        SET p.deletedAt = CURRENT_TIMESTAMP
        WHERE p.id = :id
    """)
    void softDelete(@Param("id") Long id);
}
