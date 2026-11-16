package by.regsales.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "purchasing_power_calculations")
@Getter
@Setter
@NoArgsConstructor
public class PurchasingPowerCalculation {
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

    @Column(nullable = false, length = 40)
    private String methodVersion;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal indexValue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calculated_by_user_id", nullable = false)
    private AppUser calculatedByUser;

    @Column(nullable = false, updatable = false)
    private Instant calculatedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    void initializeTimestamp() {
        if (calculatedAt == null) calculatedAt = Instant.now();
    }
}
