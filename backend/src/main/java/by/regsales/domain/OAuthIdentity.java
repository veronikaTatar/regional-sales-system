package by.regsales.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "oauth_identities", uniqueConstraints = @UniqueConstraint(name="uk_oauth_provider_subject", columnNames={"provider", "subject"})) @Getter @Setter @NoArgsConstructor
public class OAuthIdentity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 30) private String provider;
    @Column(nullable = false, length = 255) private String subject;
    @ManyToOne(optional = false) @JoinColumn(name = "user_id", nullable = false) private AppUser user;
}
