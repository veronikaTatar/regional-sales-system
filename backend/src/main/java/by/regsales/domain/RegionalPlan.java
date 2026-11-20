package by.regsales.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GeneratedColumn;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "regional_plans", uniqueConstraints = @UniqueConstraint(name = "uk_active_plan_region_period",
        columnNames = {"region_id", "period_start", "period_end", "active_slot"}))
@Getter
@Setter
@NoArgsConstructor
public class RegionalPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status = PlanStatus.DRAFT;

    @GeneratedColumn("case when status = 'CANCELLED' then null else 1 end")
    @Column(columnDefinition = "TINYINT", insertable = false, updatable = false)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Byte activeSlot;

    @Column(precision = 18, scale = 2)
    private BigDecimal budgetLimit;

    @Column(precision = 18, scale = 2)
    private BigDecimal targetRevenue;

    @Column(precision = 5, scale = 2)
    private BigDecimal maxDiscountPercent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_manager_id")
    private AppUser assignedManager;

    private Instant sentToManagerAt;

    @Column(columnDefinition = "TEXT")
    private String planningComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchasing_power_calculation_id")
    private PurchasingPowerCalculation purchasingPowerCalculation;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analytical_recommendation_id", unique = true)
    private AnalyticalRecommendation analyticalRecommendation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private AppUser createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private AppUser approvedByUser;

    private Instant approvedAt;

    @Column(columnDefinition = "TEXT")
    private String reviewComment;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String validationNote;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    void initializeTimestamp() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
