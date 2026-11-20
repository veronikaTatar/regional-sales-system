package by.regsales.config;
import by.regsales.auth.GoogleAccountService;
import by.regsales.common.ApiError;
import by.regsales.repository.AppUserRepository;
import by.regsales.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.web.cors.*;
import java.util.*;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean AuthenticationManager authenticationManager(AppUserRepository users,PasswordEncoder encoder) {
        var provider=new DaoAuthenticationProvider(login -> new AppPrincipal(users.findByLogin(login).orElseThrow(()->new UsernameNotFoundException("Пользователь не найден"))));
        provider.setPasswordEncoder(encoder); return new ProviderManager(provider);
    }
    @Bean SecurityContextRepository contextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean HttpSessionCsrfTokenRepository csrfRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SessionAuthenticationStrategy sessionStrategy(HttpSessionCsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),new CsrfAuthenticationStrategy(csrf)));
    }
    @Bean CorsConfigurationSource cors(@Value("${app.frontend-url}") String frontend) {
        var config=new CorsConfiguration(); config.setAllowedOrigins(List.of(frontend)); config.setAllowedMethods(List.of("GET","POST","PATCH","PUT","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN")); config.setAllowCredentials(true); config.setMaxAge(3600L);
        var source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",config); return source;
    }
    @Bean SecurityFilterChain security(HttpSecurity http,AppUserRepository users,ObjectMapper mapper,
        HttpSessionCsrfTokenRepository csrf,SecurityContextRepository context,GoogleAccountService google,
        ObjectProvider<ClientRegistrationRepository> clients,@Value("${app.frontend-url}") String frontend) throws Exception {
        http.cors(Customizer.withDefaults()).csrf(c->c.csrfTokenRepository(csrf))
            .securityContext(c->c.securityContextRepository(context))
            .requestCache(c->c.disable())
            .authorizeHttpRequests(a->a
                .requestMatchers("/api/auth/csrf","/api/auth/providers","/api/auth/login","/api/auth/register","/actuator/health","/error","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html","/oauth2/**","/login/oauth2/**").permitAll()
                .requestMatchers("/api/users", "/api/users/**", "/api/roles").hasRole("ADMIN")
                .requestMatchers("/api/products/manage", "/api/products/import", "/api/products/export",
                        "/api/products/*/status", "/api/products/price-history", "/api/products/price-history/**").hasRole("MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/products").hasRole("MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/products/*").hasRole("MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/shipments", "/api/shipments/import").hasRole("MANAGER")
                .requestMatchers("/api/shipments", "/api/shipments/**").hasAnyRole("MANAGER", "ANALYST", "SALES_HEAD")
                .anyRequest().authenticated())
            .exceptionHandling(e->e
                .authenticationEntryPoint((request,response,error)->{response.setStatus(401);response.setContentType("application/json;charset=UTF-8");mapper.writeValue(response.getWriter(),new ApiError("UNAUTHORIZED","Войдите в систему"));})
                .accessDeniedHandler((request,response,error)->{response.setStatus(403);response.setContentType("application/json;charset=UTF-8");mapper.writeValue(response.getWriter(),error instanceof CsrfException ? new ApiError("CSRF_INVALID","Сессия формы устарела. Повторите запрос с новым CSRF-токеном") : new ApiError("FORBIDDEN","Недостаточно прав для выполнения операции"));}))
            .addFilterBefore(new CurrentAccountFilter(users),AnonymousAuthenticationFilter.class)
            .logout(l->l.logoutUrl("/api/auth/logout").deleteCookies("REGSALES_SESSION").logoutSuccessHandler((request,response,auth)->response.setStatus(204)));
        if (clients.getIfAvailable()!=null) {
            var delegate=new OidcUserService();
            http.oauth2Login(o->o.userInfoEndpoint(u->u.oidcUserService(request->google.resolve(delegate.loadUser(request))))
                .successHandler((request,response,auth)->response.sendRedirect(frontend+"/"))
                .failureHandler((request,response,error)->response.sendRedirect(frontend+"/login?error=google")));
        }
        return http.build();
    }
}
