package com.realestate.backend.repository;

import com.realestate.backend.entity.sale.SalePayment;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SalePaymentRepository extends JpaRepository<SalePayment, Long> {

    List<SalePayment> findByContract_IdOrderByCreatedAtAsc(Long contractId);

    @Query("""
        SELECT COALESCE(SUM(sp.amount), 0)
        FROM SalePayment sp
        WHERE sp.contract.id = :contractId
          AND sp.status = 'PAID'
    """)
    BigDecimal totalPaidByContract(@Param("contractId") Long contractId);

    @Modifying
    @Query("""
        UPDATE SalePayment sp
        SET sp.status = :status
        WHERE sp.id = :id
    """)
    void updateStatus(@Param("id") Long id, @Param("status") String status);
}
