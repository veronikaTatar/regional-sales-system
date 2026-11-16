package by.regsales.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.*;
@Configuration
public class GoogleClientConfig {
    @Bean @Conditional(GoogleCredentialsCondition.class)
    ClientRegistrationRepository googleClients(@Value("${app.google.client-id}") String id,@Value("${app.google.client-secret}") String secret,@Value("${app.google.redirect-uri:http://localhost:8080/login/oauth2/code/google}") String redirect) {
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId(id).clientSecret(secret).scope("openid","profile","email").redirectUri(redirect).build());
    }
}
