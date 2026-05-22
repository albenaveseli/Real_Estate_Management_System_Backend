package com.realestate.backend.repository;

import com.realestate.backend.entity.enums.PaymentStatus;
import com.realestate.backend.entity.rental.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("""
        SELECT p FROM Payment p
        LEFT JOIN FETCH p.recipient
        WHERE p.contract.id = :contractId
        ORDER BY p.dueDate ASC
    """)
    List<Payment> findByContract_IdOrderByDueDateAsc(@Param("contractId") Long contractId);

    @Query(
            value = """
            SELECT p FROM Payment p
            LEFT JOIN FETCH p.recipient
            WHERE p.status = :status
            ORDER BY p.dueDate ASC
        """,
            countQuery = """
            SELECT COUNT(p) FROM Payment p
            WHERE p.status = :status
        """
    )
    Page<Payment> findByStatusOrderByDueDateAsc(
            @Param("status") PaymentStatus status, Pageable pageable);

    @Query("""
        SELECT p FROM Payment p
        LEFT JOIN FETCH p.recipient
        WHERE p.status = com.realestate.backend.entity.enums.PaymentStatus.PENDING
          AND p.dueDate < :today
        ORDER BY p.dueDate ASC
    """)
    List<Payment> findOverduePayments(@Param("today") LocalDate today);


    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM Payment p
        WHERE p.status = com.realestate.backend.entity.enums.PaymentStatus.PAID
    """)
    BigDecimal totalRevenue();

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM Payment p
        WHERE p.contract.id = :contractId
          AND p.status = com.realestate.backend.entity.enums.PaymentStatus.PAID
    """)
    BigDecimal totalPaidByContract(@Param("contractId") Long contractId);


    long countByStatus(PaymentStatus status);
}