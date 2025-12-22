package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.storageUrl;
import static org.folio.util.StringUtil.urlEncode;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.Response;
import org.folio.rest.support.builders.RequestItemSummary;
import org.folio.rest.support.builders.RequestRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import lombok.SneakyThrows;

class IsbnNormalizationTest extends ApiTests {
  private static final String REQUEST_STORAGE_URL = "/request-storage/requests";
  private final UUID isbnIdentifierId = UUID.fromString("8261054f-be78-422d-bd51-4ed9f33c3422");

  @SneakyThrows
  @BeforeEach
  void beforeEach() {
    StorageTestSuite.deleteAll(storageUrl(REQUEST_STORAGE_URL));
  }

  @SneakyThrows
  @Test
  void canSearchForFirstIsbnWithAdditionalHyphens() {
    createRequests("Interesting Times", "Uprooted");

    find("itemIsbn = 0-552-16754-1", "Interesting Times");
  }

  @SneakyThrows
  @Test
  void canSearchForFirstIsbnWithAdditionalHyphenAndTruncation() {
    createRequests("Interesting Times", "Uprooted");

    find("itemIsbn = 05-5*", "Interesting Times");
  }

  @SneakyThrows
  @Test
  void canSearchForSecondIsbnWithMissingHyphens() {
    createRequests("Interesting Times", "Uprooted");

    find("itemIsbn = 9780552167543", "Interesting Times");
  }

  @SneakyThrows
  @Test
  void canSearchForSecondIsbnWithMissingHyphensAndTrunation() {
    createRequests("Interesting Times", "Temeraire", "Uprooted");

    find("itemIsbn = 9780*", "Interesting Times", "Temeraire");
  }

  @SneakyThrows
  @Test
  void canSearchForSecondIsbnWithAlteredHyphens() {
    createRequests("Interesting Times", "Temeraire");

    find("itemIsbn = 9-7-8-055-2167-543", "Interesting Times");
  }

  @SneakyThrows
  @Test
  void cannotFindIsbnWithTailString() {
    createRequests("Interesting Times");

    find("itemIsbn = 552-16754-3");
  }

  @SneakyThrows
  @Test
  void cannotFindIsbnWithInnerStringAndTruncation() {
    createRequests("Interesting Times");

    find("itemIsbn = 552*");
  }

  private void find(String cql, String ... expectedTitles)
   throws MalformedURLException, InterruptedException, TimeoutException, ExecutionException {

    JsonObject searchBody = searchForRequests(cql);
    matchItemTitles(searchBody, expectedTitles);
  }

  private void matchItemTitles(JsonObject jsonObject, String[] expectedTitles) {
    if (expectedTitles.length == 0) {
      assertThat(jsonObject.getInteger("totalRecords"), is(0));
      return;
    }
    assertThat(jsonObject.getInteger("totalRecords"), is(expectedTitles.length));
    JsonArray requests = jsonObject.getJsonArray("requests");
    for (String title : expectedTitles) {
      Boolean found = requests.stream().anyMatch(request -> isTitlePresent((JsonObject) request, title));
      assertThat(found, is(true));
    }
  }

  private Boolean isTitlePresent(JsonObject request, String titleToMatch) {
    return request.getJsonObject("instance")
    .getString("title")
    .equals(titleToMatch);
  }

  private JsonObject searchForRequests(String cql)
    throws MalformedURLException, InterruptedException, TimeoutException, ExecutionException {

    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
      String url = requestStorageUrl("").toString() + "?query=" + urlEncode(cql);

      client.get(url, TENANT_ID, responseHandler(searchCompleted));
      Response searchResponse = searchCompleted.get(5, SECONDS);

      assertThat(searchResponse.getStatusCode(), is(200));
      return searchResponse.getJson();
  }

  private static Handler<AsyncResult<HttpResponse<Buffer>>> responseHandler(CompletableFuture<Response> completed) {
    return vertxResponse -> {
      try{
        if (vertxResponse.failed()) {
          completed.completeExceptionally(vertxResponse.cause());
        }
        Response response = Response.from(vertxResponse.result());
        completed.complete(response);
      } catch (Throwable e) {
        completed.completeExceptionally(e);
      }
    };
  }

  static URL requestStorageUrl() throws MalformedURLException {
    return requestStorageUrl("");
  }

  static URL requestStorageUrl(String subPath)
    throws MalformedURLException {

    return storageUrl(REQUEST_STORAGE_URL + subPath);
  }

  private void createRequests(String ... titlesToCreate)
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
    Map<String, RequestItemSummary> requests
            = new HashMap<String, RequestItemSummary>();

    // Interesting Times has two ISBNs: 0552167541, 978-0-552-16754-3
    requests.put("Interesting Times", new RequestItemSummary("Interesting Times", "000000004")
      .addIdentifier(isbnIdentifierId, "978-0-552-16754-3")
      .addIdentifier(isbnIdentifierId, "0552167541"));
    requests.put("Temeraire", new RequestItemSummary("Temeraire", "000000002")
        .addIdentifier(isbnIdentifierId, "9780007258710"));
    requests.put("Uprooted", new RequestItemSummary("Uprooted", "565575337892")
        .addIdentifier(isbnIdentifierId, "9781447294146"));

    for (String title : titlesToCreate) {
      JsonObject representation = createEntity(
            new RequestRequestBuilder()
                .recall()
                .toHoldShelf()
                .withItem(requests.get(title))
                .create(),
            requestStorageUrl()
        ).getJson();
    }
  }
}
