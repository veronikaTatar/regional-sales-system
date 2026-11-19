package by.regsales.security;
import by.regsales.domain.AppUser;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import java.util.*;
public class AppPrincipal implements UserDetails, OidcUser {
    private final Long id;
    private final String login,password;
    private final boolean active;
    private final long sessionVersion;
    private final List<GrantedAuthority> authorities;
    private final OidcUser oidcUser;
    public AppPrincipal(AppUser u) { this(u,null); }
    public AppPrincipal(AppUser u,OidcUser oidcUser) {
        id=u.getId(); login=u.getLogin(); password=u.getPasswordHash()==null?"":u.getPasswordHash(); active=u.isActive();
        sessionVersion=u.getSessionVersion();
        authorities=List.of(new SimpleGrantedAuthority("ROLE_"+u.getRole().getCode().name())); this.oidcUser=oidcUser;
    }
    public Long getId() { return id; }
    public long getSessionVersion() { return sessionVersion; }
    public OidcUser getDelegate() { return oidcUser; }
    @Override public String getName() { return login; }
    @Override public String getUsername() { return login; }
    @Override public String getPassword() { return password; }
    @Override public boolean isEnabled() { return active; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public Map<String,Object> getAttributes() { return getClaims(); }
    @Override public Map<String,Object> getClaims() { return oidcUser==null?Map.of():oidcUser.getClaims(); }
    @Override public OidcUserInfo getUserInfo() { return oidcUser==null?null:oidcUser.getUserInfo(); }
    @Override public OidcIdToken getIdToken() { return oidcUser==null?null:oidcUser.getIdToken(); }
}
