package by.regsales.auth;
import by.regsales.security.AppPrincipal;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static by.regsales.auth.AuthDtos.*;
@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService service;
    private final AuthenticationManager manager;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessionStrategy;
    @Value("${app.google.client-id:}") private String googleId;
    @Value("${app.google.client-secret:}") private String googleSecret;
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @GetMapping("/providers") public Map<String,Boolean> providers() { return Map.of("google",!googleId.isBlank()&&!googleSecret.isBlank()); }
    @PostMapping("/register") public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest body,HttpServletRequest request,HttpServletResponse response) {
        var user=service.register(body); var principal=new AppPrincipal(user);
        authenticate(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()),request,response);
        return ResponseEntity.status(201).body(UserView.from(user));
    }
    @PostMapping("/login") public UserView login(@Valid @RequestBody LoginRequest body,HttpServletRequest request,HttpServletResponse response) {
        try {
            var auth=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(body.login().toLowerCase(Locale.ROOT),body.password()));
            authenticate(auth,request,response); return UserView.from(service.current(auth.getName()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            throw new by.regsales.common.ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Неверный логин или пароль либо учётная запись отключена");
        }
    }
    @GetMapping("/me") public UserView me(Authentication authentication) { return UserView.from(service.current(authentication.getName())); }
    private void authenticate(Authentication authentication,HttpServletRequest request,HttpServletResponse response) {
        sessionStrategy.onAuthentication(authentication,request,response);
        var context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication); SecurityContextHolder.setContext(context);
        contexts.saveContext(context,request,response);
    }
}
