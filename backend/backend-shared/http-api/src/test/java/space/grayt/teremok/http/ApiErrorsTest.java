package space.grayt.teremok.http;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.RequestDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class ApiErrorsTest {

  MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.standaloneSetup(
      new Failures(),
      new ApiErrorController()
    )
      .setControllerAdvice(new ApiExceptionHandler())
      .addFilters(new CorrelationFilter())
      .build();
  }

  @Test
  void mvcAndDomainFailuresHaveOneContractAndDoNotExposeInternals()
    throws Exception {
    for (int status : new int[] { 400, 401, 403, 404, 409, 429, 503 }) {
      expect(
        mvc.perform(
          get("/status/" + status).header(ApiErrors.HEADER, "contract-test")
        ),
        status
      );
    }
    expect(mvc.perform(get("/conflict")), 409);
    expect(mvc.perform(get("/too-large")), 413);
    var internal = expect(mvc.perform(get("/internal")), 500);
    assertThat(internal.getResponse().getContentAsString()).doesNotContain(
      "secret",
      "SQLException",
      "stackTrace"
    );
  }

  @Test
  void frameworkValidationAndRoutingUseJsonEvenWhenHtmlIsRequested()
    throws Exception {
    expect(
      mvc.perform(
        post("/body").contentType(MediaType.APPLICATION_JSON).content("{}")
      ),
      400
    );
    expect(
      mvc.perform(
        post("/body").contentType(MediaType.APPLICATION_JSON).content("{")
      ),
      400
    );
    expect(
      mvc.perform(post("/body").contentType(MediaType.APPLICATION_JSON)),
      400
    );
    expect(mvc.perform(get("/uuid/not-a-uuid")), 400);
    expect(mvc.perform(get("/body")), 405);
    expect(
      mvc.perform(
        post("/body").contentType(MediaType.TEXT_PLAIN).content("bad")
      ),
      415
    );
    expect(mvc.perform(post("/file")), 400);
    expect(mvc.perform(multipart("/file")), 400);
    expect(
      mvc.perform(get("/does-not-exist").accept(MediaType.TEXT_HTML)),
      404
    );
  }

  @Test
  void servletAndSecurityFailuresUseSameContractAndRejectInvalidCorrelationIds()
    throws Exception {
    expect(
      mvc.perform(
        get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
      ),
      403
    );
    var request = new MockHttpServletRequest();
    request.addHeader(ApiErrors.HEADER, "bad id\r\nsecret");
    var response = new MockHttpServletResponse();
    new CorrelationFilter().doFilter(request, response, (r, s) ->
      ApiErrors.write(request, response, 401, ApiErrors.message(401))
    );
    var error = JsonMapper.builder()
      .build()
      .readValue(response.getContentAsString(), ApiError.class);
    assertThat(error.status()).isEqualTo(401);
    assertThat(error.code()).isEqualTo("unauthorized");
    assertThatCode(() ->
      UUID.fromString(error.correlationId())
    ).doesNotThrowAnyException();
    assertThat(response.getHeader(ApiErrors.HEADER)).isEqualTo(
      error.correlationId()
    );
  }

  @Test
  void unexpectedFilterFailureIsSanitized() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    new CorrelationFilter().doFilter(request, response, (r, s) -> {
      throw new IllegalStateException("secret");
    });
    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.getContentAsString())
      .contains("internal_error")
      .doesNotContain("secret");
  }

  private MvcResult expect(ResultActions action, int status) throws Exception {
    var result = action
      .andExpect(status().is(status))
      .andExpect(
        content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
      )
      .andExpect(jsonPath("$.status").value(status))
      .andExpect(jsonPath("$.code").isNotEmpty())
      .andExpect(jsonPath("$.message").isNotEmpty())
      .andExpect(jsonPath("$.correlationId").isNotEmpty())
      .andReturn();
    var json = JsonMapper.builder()
      .build()
      .readTree(result.getResponse().getContentAsString());
    assertThat(json.size()).isEqualTo(4);
    assertThat(result.getResponse().getHeader(ApiErrors.HEADER)).isEqualTo(
      json.path("correlationId").asString()
    );
    return result;
  }

  @RestController
  static class Failures {

    @GetMapping("/status/{status}")
    void status(@PathVariable int status) {
      throw new ResponseStatusException(
        HttpStatus.valueOf(status),
        status >= 500 ? "secret" : "Понятное описание"
      );
    }

    @GetMapping("/conflict")
    void conflict() {
      throw new DataIntegrityViolationException("secret SQL");
    }

    @GetMapping("/internal")
    void internal() {
      throw new IllegalStateException("secret SQLException");
    }

    @GetMapping("/too-large")
    void tooLarge() {
      throw new org.springframework.web.multipart.MaxUploadSizeExceededException(
        100
      );
    }

    @GetMapping("/uuid/{id}")
    void uuid(@PathVariable UUID id) {}

    @PostMapping("/body")
    void body(@Valid @RequestBody Input body) {}

    @PostMapping("/file")
    void file(
      @RequestPart("file") org.springframework.web.multipart.MultipartFile file
    ) {}

    record Input(@NotBlank String name) {}
  }
}
