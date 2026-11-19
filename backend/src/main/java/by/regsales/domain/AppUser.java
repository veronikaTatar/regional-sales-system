package by.regsales.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "app_users") @Getter @Setter @NoArgsConstructor
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "role_id", nullable = false) private Role role;
    @Column(nullable = false, unique = true, length = 60) private String login;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(length = 255) private String passwordHash;
    @Column(nullable = false, length = 150) private String fullName;
    @Column(nullable = false) private boolean active = true;
    @Column(nullable = false) private long sessionVersion;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
}
