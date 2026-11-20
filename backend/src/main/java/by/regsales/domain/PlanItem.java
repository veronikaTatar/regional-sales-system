package by.regsales.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "plan_items", uniqueConstraints = @UniqueConstraint(name = "uk_plan_product", columnNames = {"regional_plan_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PlanItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "regional_plan_id", nullable = false)
    private RegionalPlan regionalPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal plannedQuantity;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal plannedUnitPrice;

    @Column(precision = 18, scale = 2, insertable = false, updatable = false)
    @org.hibernate.annotations.GeneratedColumn("round(planned_quantity * planned_unit_price, 2)")
    private BigDecimal plannedAmount;
}
