package com.realestate.backend.repository;

import com.realestate.backend.entity.rental.RentalListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface RentalListingRepository extends JpaRepository<RentalListing, Long> {

    Optional<RentalListing> findByIdAndDeletedAtIsNull(Long id);

    Page<RentalListing> findAllByDeletedAtIsNull(Pageable pageable);

    List<RentalListing> findByProperty_IdAndStatusAndDeletedAtIsNull(
            Long propertyId, String status);

    @Query("""
    SELECT COUNT(rl) > 0 FROM RentalListing rl
    WHERE rl.property.id = :propertyId
      AND rl.status = 'ACTIVE'
      AND rl.deletedAt IS NULL
      AND rl.availableFrom < :availableUntil
      AND rl.availableUntil > :availableFrom
""")
    boolean existsOverlappingListing(
            @Param("propertyId")    Long propertyId,
            @Param("availableFrom") java.time.LocalDate availableFrom,
            @Param("availableUntil") java.time.LocalDate availableUntil
    );
    @Modifying
    @Query("""
        UPDATE RentalListing rl
        SET rl.deletedAt = CURRENT_TIMESTAMP
        WHERE rl.id = :id
    """)
    void softDelete(@Param("id") Long id);

    @Modifying
    @Query("""
        UPDATE RentalListing rl
        SET rl.status = :status
        WHERE rl.id = :id
    """)
    void updateStatus(@Param("id") Long id, @Param("status") String status);


}