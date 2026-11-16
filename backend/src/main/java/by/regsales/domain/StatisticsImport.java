package by.regsales.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "statistics_imports")
@Getter
@Setter
@NoArgsConstructor
public class StatisticsImport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 150)
    private String sourceName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "imported_by_user_id", nullable = false)
    private AppUser importedByUser;

    @Column(nullable = false, updatable = false)
    private Instant importedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ImportStatus status = ImportStatus.PROCESSING;

    @Column(nullable = false)
    private int rowsTotal;

    @Column(nullable = false)
    private int rowsAccepted;

    @Column(nullable = false)
    private int rowsRejected;

    @Column(nullable = false)
    private int rowsSkipped;

    @PrePersist
    void initializeTimestamp() {
        if (importedAt == null) importedAt = Instant.now();
    }
}
