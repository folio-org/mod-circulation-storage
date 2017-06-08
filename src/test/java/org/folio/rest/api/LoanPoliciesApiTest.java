package org.folio.rest.api;


import io.vertx.core.json.JsonObject;
import org.folio.rest.support.HttpClient;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.builders.LoanPolicyRequestBuilder;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class LoanPolicyStorageTest {

  private static HttpClient client = new HttpClient(StorageTestSuite.getVertx());

  @Test
  public void canCreateALoanPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    JsonObject loanPolicyRequest = new LoanPolicyRequestBuilder()
      .loanable()
      .create();

    client.post(loanPolicyStorageUrl(),
      loanPolicyRequest,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create loan policy: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = response.getJson();

    assertThat(representation.getBoolean("loanable"), is(true));
  }

  private static URL loanPolicyStorageUrl() throws MalformedURLException {
    return loanPolicyStorageUrl("");
  }

  private static URL loanPolicyStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/loan-policy-storage/loans-policies" + subPath);
  }

}
