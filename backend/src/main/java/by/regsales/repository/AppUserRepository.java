package by.regsales.repository;
import by.regsales.domain.AppUser;
import by.regsales.domain.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByLogin(String login);
    Optional<AppUser> findByEmail(String email);
    boolean existsByLogin(String login);
    boolean existsByEmail(String email);
    long countByRoleCodeAndActiveTrue(RoleCode code);
}
