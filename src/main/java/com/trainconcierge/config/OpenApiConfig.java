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
    public OpenAPI trainConciergeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TrainConcierge API")
                        .version("0.0.1-SNAPSHOT")
                        .description("TrainConcierge backend. Phase 2 adds passenger management and atomic journey planning with multi-leg itinerary validation."));
    }
}
