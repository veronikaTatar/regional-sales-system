package by.regsales.auth;
import by.regsales.domain.*;
import by.regsales.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class GoogleAccountServiceTest {
    @Mock OAuthIdentityRepository identities;
    @Mock AppUserRepository users;
    @Mock RoleRepository roles;
    @Mock OidcUser google;
    @InjectMocks GoogleAccountService service;
    void claims() { when(google.getSubject()).thenReturn("subject-123");when(google.getEmail()).thenReturn("person@example.com");when(google.getEmailVerified()).thenReturn(true); }
    @Test void rejectsUnverifiedEmail() {when(google.getSubject()).thenReturn("subject-123");when(google.getEmail()).thenReturn("person@example.com");when(google.getEmailVerified()).thenReturn(false);assertThatThrownBy(()->service.resolve(google)).isInstanceOf(OAuth2AuthenticationException.class);verifyNoInteractions(identities,users);}
    @Test void refusesAutomaticLinkToLocalAccount() { claims();when(users.existsByEmail("person@example.com")).thenReturn(true);assertThatThrownBy(()->service.resolve(google)).isInstanceOf(OAuth2AuthenticationException.class);verify(users,never()).saveAndFlush(any()); }
    @Test void createsManagerAndSavesImmutableSubject() {
        claims();var role=new Role();role.setCode(RoleCode.MANAGER);when(roles.findByCode(RoleCode.MANAGER)).thenReturn(Optional.of(role));when(users.saveAndFlush(any())).thenAnswer(i->{AppUser user=i.getArgument(0);user.setId(10L);return user;});
        var principal=service.resolve(google);assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_MANAGER");
        var captor=ArgumentCaptor.forClass(OAuthIdentity.class);verify(identities).saveAndFlush(captor.capture());assertThat(captor.getValue().getSubject()).isEqualTo("subject-123");assertThat(captor.getValue().getUser().getPasswordHash()).isNull();
    }
    @Test void returnsSameAccountForKnownSubjectAndRejectsDisabledAccount() {
        claims();var role=new Role();role.setCode(RoleCode.ANALYST);var user=new AppUser();user.setId(4L);user.setLogin("analyst");user.setRole(role);var identity=new OAuthIdentity();identity.setUser(user);when(identities.findByProviderAndSubject("google","subject-123")).thenReturn(Optional.of(identity));
        assertThat(service.resolve(google).getId()).isEqualTo(4L);verifyNoInteractions(users);user.setActive(false);assertThatThrownBy(()->service.resolve(google)).isInstanceOf(OAuth2AuthenticationException.class);
    }
}
