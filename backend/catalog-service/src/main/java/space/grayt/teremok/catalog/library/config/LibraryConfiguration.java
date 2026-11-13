package space.grayt.teremok.catalog.library.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class LibraryConfiguration {

  @Bean
  OpenAPI libraryOpenApi() {
    return new OpenAPI()
      .info(
        new Info()
          .title("Теремок — библиотека")
          .version("1.0")
          .description(
            "Управление произведениями, коллекциями, заметками, CSV и аналитикой"
          )
      )
      .components(
        new Components().addSecuritySchemes(
          "bearerAuth",
          new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
        )
      )
      .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }
}
