package by.regsales.config;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
/** Read property presence without interpolating credential values into an expression. */
public class GoogleCredentialsCondition implements Condition {
    @Override public boolean matches(ConditionContext context,AnnotatedTypeMetadata metadata) {
        return StringUtils.hasText(context.getEnvironment().getProperty("app.google.client-id"))
            && StringUtils.hasText(context.getEnvironment().getProperty("app.google.client-secret"));
    }
}
