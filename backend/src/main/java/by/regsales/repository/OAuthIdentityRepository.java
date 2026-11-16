package by.regsales.repository;
import by.regsales.domain.OAuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, Long> {
    Optional<OAuthIdentity> findByProviderAndSubject(String provider, String subject);
}
