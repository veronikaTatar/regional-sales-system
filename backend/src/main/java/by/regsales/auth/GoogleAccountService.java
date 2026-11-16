package by.regsales.auth;
import by.regsales.domain.*;
import by.regsales.repository.*;
import by.regsales.security.AppPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class GoogleAccountService {
    private final OAuthIdentityRepository identities;
    private final AppUserRepository users;
    private final RoleRepository roles;
    @Transactional public AppPrincipal resolve(OidcUser google) {
        var subject=google.getSubject(); var email=google.getEmail();
        if (subject==null || subject.isBlank() || subject.length()>255 || !Boolean.TRUE.equals(google.getEmailVerified()) || email==null || email.isBlank() || email.length()>254) {
            throw failure("Google не подтвердил email пользователя");
        }
        var identity=identities.findByProviderAndSubject("google",subject).orElse(null);
        if (identity!=null) {
            if (!identity.getUser().isActive()) throw failure("Учётная запись отключена");
            return new AppPrincipal(identity.getUser(),google);
        }
        email=email.toLowerCase(Locale.ROOT);
        // Email equality is not proof that an existing local account consents to linking.
        if (users.existsByEmail(email)) throw failure("Email уже связан с локальной учётной записью. Войдите по логину и паролю");
        var user=new AppUser(); user.setLogin("google_"+UUID.randomUUID().toString().replace("-",""));
        user.setEmail(email); var name=google.getFullName();
        user.setFullName(name==null||name.isBlank()?"Пользователь Google":name.substring(0,Math.min(name.length(),150)));
        user.setRole(roles.findByCode(RoleCode.MANAGER).orElseThrow()); users.saveAndFlush(user);
        identity=new OAuthIdentity(); identity.setProvider("google"); identity.setSubject(subject); identity.setUser(user); identities.saveAndFlush(identity);
        return new AppPrincipal(user,google);
    }
    private OAuth2AuthenticationException failure(String message) { return new OAuth2AuthenticationException(new OAuth2Error("google_account_rejected"),message); }
}
