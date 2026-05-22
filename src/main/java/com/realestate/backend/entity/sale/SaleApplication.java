package com.realestate.backend.entity.sale;

import com.realestate.backend.entity.property.Property;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "sale_applications",
        indexes = {
                @Index(name = "idx_sale_app_listing",  columnList = "listing_id"),
                @Index(name = "idx_sale_app_buyer",    columnList = "buyer_id"),
                @Index(name = "idx_sale_app_status",   columnList = "status"),
                @Index(name = "idx_sale_app_property", columnList = "property_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id")
    private SaleListing listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id")
    private Property property;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "offer_price", precision = 12, scale = 2)
    private BigDecimal offerPrice;

    @Column(name = "desired_purchase_date")
    private LocalDate desiredPurchaseDate;

    @Column(name = "monthly_income", precision = 12, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}