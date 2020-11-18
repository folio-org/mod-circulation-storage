package org.folio.rest.support.spring;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;

import java.util.Base64;
import java.util.UUID;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.support.clients.OkapiHeaders;
import org.folio.rest.support.clients.ResourceClient;
import org.folio.rest.support.clients.RestAssuredClient;
import org.folio.rest.support.dto.RequestDto;
import org.folio.rest.support.dto.ScheduledNoticeDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.vertx.core.json.JsonObject;

@Configuration
@SuppressWarnings("unused")
public class TestContextConfiguration {
  private static final String USER_ID = UUID.randomUUID().toString();

  @Bean
  public OkapiHeaders okapiHeaders() {
    return OkapiHeaders.builder()
      .tenantId(TENANT_ID)
      .url("http://localhost:" + StorageTestSuite.PROXY_PORT)
      .token(generateToken())
      .userId(USER_ID)
      .build();
  }

  @Bean
  public RestAssuredClient restAssuredClient(OkapiHeaders okapiHeaders) {
    return new RestAssuredClient(okapiHeaders, restAssuredConfig());
  }

  @Bean
  public RestAssuredConfig restAssuredConfig() {
    final ObjectMapperConfig objectMapperConfig = new ObjectMapperConfig()
      .jackson2ObjectMapperFactory((type, s) -> objectMapper());

    return new RestAssuredConfig().objectMapperConfig(objectMapperConfig);
  }

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules()
      .disable(WRITE_DATES_AS_TIMESTAMPS);
  }

  @Bean
  public ResourceClient<RequestDto> requestClient(RestAssuredClient restAssuredClient) {
    return new ResourceClient<>("/request-storage/requests", "requests",
      restAssuredClient, RequestDto.class);
  }

  @Bean
  public ResourceClient<ScheduledNoticeDto> scheduledNoticeClient(RestAssuredClient restAssuredClient) {
    return new ResourceClient<>("/scheduled-notice-storage/scheduled-notices",
      "scheduledNotices", restAssuredClient, ScheduledNoticeDto.class);
  }

  private String generateToken() {
    final String payload = new JsonObject()
      .put("user_id", USER_ID)
      .put("tenant", TENANT_ID)
      .put("sub", "admin")
      .toString();
    return String.format("1.%s.3", Base64.getEncoder()
      .encodeToString(payload.getBytes()));
  }
}
