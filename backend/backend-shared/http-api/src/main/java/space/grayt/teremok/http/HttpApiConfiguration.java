package space.grayt.teremok.http;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration(
  before = org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration.class
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class HttpApiConfiguration {

  @Bean
  ApiExceptionHandler apiExceptionHandler() {
    return new ApiExceptionHandler();
  }

  @Bean
  FilterRegistrationBean<CorrelationFilter> apiCorrelationFilter() {
    var filter = new FilterRegistrationBean<>(new CorrelationFilter());
    filter.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return filter;
  }

  @Bean
  ApiErrorController apiErrorController() {
    return new ApiErrorController();
  }
}
