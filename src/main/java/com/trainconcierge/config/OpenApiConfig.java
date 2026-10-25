package com.trainconcierge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger metadata. UI is served at /swagger-ui.html and the raw
 * document at /v3/api-docs.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI trainConciergeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TrainConcierge API")
                        .version("0.0.1")
                        .description("TrainConcierge backend. Phase 0 exposes only foundation endpoints "
                                + "(application status and validation demo). Business modules are added in later phases.")
                        .contact(new Contact().name("TrainConcierge Team"))
                        .license(new License().name("Academic project")));
    }
}
