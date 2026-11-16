package by.regsales.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "analytical_recommendations")
@Getter
@Setter
@NoArgsConstructor
public class AnalyticalRecommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchasing_power_calculation_id")
    private PurchasingPowerCalculation purchasingPowerCalculation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_user_id", nullable = false)
    private AppUser authorUser;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String recommendationText;

    @PrePersist
    void initializeTimestamp() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
