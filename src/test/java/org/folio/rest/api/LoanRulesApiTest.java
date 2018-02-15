package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.LoanRules;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LoanRulesApiTest extends ApiTests {
  private static final int HTTP_VALIDATION_ERROR = 422;
  private String uuid;

  private static URL loanRulesStorageUrl() throws MalformedURLException {
    return loanRulesStorageUrl("");
  }

  private static URL loanRulesStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/loan-rules-storage" + subPath);
  }

  private LoanRules loanRules(String loanRulesAsTextFile) {
    LoanRules loanRules = new LoanRules();
    loanRules.setLoanRulesAsTextFile(loanRulesAsTextFile);
    return loanRules;
  }

  private LoanRules exampleLoanRules() {
    return loanRules("fallback-policy: no-circulation\npriority: t, a, b, c, s, m, g\nm book cd dvd: in-house\n");
  }

  private LoanRules exampleLoanRules2() {
    return loanRules("fallback-policy: no-circulation\npriority: t, a, b, c, s, m, g\nm book cd dvd: general-loan\n");
  }

  private JsonResponse getResponse() throws Exception {
    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    client.get(loanRulesStorageUrl(),
      null, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));
    return createCompleted.get(5, TimeUnit.SECONDS);
  }

  /** @return the JSON of the get response, asserts an HTTP_OK=200 response */
  private JsonObject get() throws Exception {
    JsonResponse response = getResponse();
    assertThat(response.getBody(), response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    return response.getJson();
  }

  private JsonResponse putResponse(LoanRules loanRules) throws Exception {
    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();
    client.put(loanRulesStorageUrl(),
      loanRules, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));
    return createCompleted.get(5, TimeUnit.SECONDS);
  }

  /** @return the message of the put response, asserts an HTTP_OK=200 response */
  private String put204(LoanRules loanRules) throws Exception {
    JsonResponse response = putResponse(loanRules);
    assertThat(response.getBody(), response.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
    return response.getBody();
  }

  @Test
  public void a_getInitialEmptyFile() throws Exception {
    JsonObject json = get();
    assertThat(json.getString("loanRulesAsTextFile"), is(""));
    uuid = json.getString("id");
    assertThat(uuid, is(notNullValue()));
  }

  public void putAndGet(LoanRules loanRules) throws Exception {
    put204(loanRules);
    JsonObject json = get();
    assertThat(json.getString("loanRulesAsTextFile"), is(loanRules.getLoanRulesAsTextFile()));
  }

  @Test
  public void putAndGet() throws Exception {
    putAndGet(exampleLoanRules());
    putAndGet(exampleLoanRules2());
  }

  @Test
  public void putNullFields() throws Exception {
    LoanRules loanRules = new LoanRules();
    assertThat(putResponse(loanRules).getStatusCode(), is(HTTP_VALIDATION_ERROR));
  }
}
