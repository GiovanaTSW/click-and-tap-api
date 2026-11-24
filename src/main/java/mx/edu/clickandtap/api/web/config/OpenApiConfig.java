package mx.edu.clickandtap.api.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the API in Swagger UI and adds the "Authorize" button for JWT tokens.
 * The UI itself is switched on or off with the springdoc.* properties (off in production).
 */
@Configuration
public class OpenApiConfig {
    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI clickAndTapOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Click and Tap API")
                        .version("0.0.1")
                        .description("Backend of Click and Tap: catalog, orders and digital experiences "
                                + "linked to NFC/QR products. Log in with POST /api/auth/login, copy the token "
                                + "and paste it in the Authorize button."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
