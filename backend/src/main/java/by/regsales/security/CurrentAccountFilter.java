package by.regsales.security;
import by.regsales.repository.AppUserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
/** Re-read account status and authorities on each request and reject revoked sessions. */
public class CurrentAccountFilter extends OncePerRequestFilter {
    private final AppUserRepository users;
    public CurrentAccountFilter(AppUserRepository users) { this.users=users; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth!=null && auth.getPrincipal() instanceof AppPrincipal p) {
            var user=users.findById(p.getId()).orElse(null);
            if (user==null || !user.isActive() || user.getSessionVersion()!=p.getSessionVersion()) {
                var session=request.getSession(false);
                if (session != null) {
                    try {
                        session.invalidate();
                    } catch (IllegalStateException ignored) {
                        // Another request may have invalidated the same session.
                    }
                }
                SecurityContextHolder.clearContext();
            } else {
                var fresh=new AppPrincipal(user,p.getDelegate());
                var context=SecurityContextHolder.createEmptyContext();
                if (auth instanceof OAuth2AuthenticationToken oauth) context.setAuthentication(new OAuth2AuthenticationToken(fresh,fresh.getAuthorities(),oauth.getAuthorizedClientRegistrationId()));
                else context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(fresh,null,fresh.getAuthorities()));
                SecurityContextHolder.setContext(context);
            }
        }
        chain.doFilter(request,response);
    }
}
