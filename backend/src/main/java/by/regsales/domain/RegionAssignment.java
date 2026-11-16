package by.regsales.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "region_assignments", uniqueConstraints = @UniqueConstraint(name = "uk_assignment_start", columnNames = {"region_id", "manager_id", "start_date"}))
@Getter
@Setter
@NoArgsConstructor
public class RegionAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_id", nullable = false)
    private AppUser manager;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;
}
