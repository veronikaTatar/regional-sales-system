package by.regsales.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "regional_statistics", uniqueConstraints = @UniqueConstraint(name = "uk_statistic_region_indicator_period", columnNames = {"region_id", "statistical_indicator_id", "period_start", "period_end"}))
@Getter
@Setter
@NoArgsConstructor
public class RegionalStatistic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statistical_indicator_id", nullable = false)
    private StatisticalIndicator indicator;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(name = "indicator_value", nullable = false, precision = 20, scale = 4)
    private BigDecimal value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statistics_import_id")
    private StatisticsImport statisticsImport;

    @Column(columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
