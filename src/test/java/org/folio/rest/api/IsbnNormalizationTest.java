package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.api.StorageTestSuite.storageUrl;
import static org.folio.util.StringUtil.urlEncode;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.support.ApiTests;
import org.folio.rest.support.Response;
import org.folio.rest.support.builders.RequestItemSummary;
import org.folio.rest.support.builders.RequestRequestBuilder;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import lombok.SneakyThrows;

public class IsbnNormalizationTest extends ApiTests {
  private static final String REQUEST_STORAGE_URL = "/request-storage/requests";

  @SneakyThrows
  @Before
  public void beforeEach() {
    StorageTestSuite.deleteAll(storageUrl(REQUEST_STORAGE_URL));
  }

  @SneakyThrows
  @Test
  public void searchForNormalizedIsbns() {
    createRequests();

    //canSearchForFirstIsbnWithAdditionalHyphens()
    find("isbn = 0-552-16754-1",      "Interesting Times");

    //canSearchForFirstIsbnWithAdditionalHyphenAndTruncation()
    find("isbn = 05-5*",              "Interesting Times");

    //canSearchForSecondIsbnWithMissingHyphens()
    find("isbn = 9780552167543",      "Interesting Times");

    //canSearchForSecondIsbnWithMissingHyphensAndTrunation()
    find("isbn = 9780*", "Interesting Times", "Temeraire");

    //canSearchForSecondIsbnWithAlteredHyphens()
    find("isbn = 9780*", "Interesting Times", "Temeraire");
    find("isbn = 9-7-8-055-2167-543", "Interesting Times");

    //cannotFindIsbnWithTailString()
    find("isbn = 552-16754-3");

    //cannotFindIsbnWithInnerStringAndTruncation()
    find("isbn = 552*");
  }

  private void find(String cql, String ... expectedTitles) {
    JsonObject searchBody = searchForRequests(cql);
    assertThat(searchBody.toString(), not(""));
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
    JsonObject item = request.getJsonObject("item");
    String itemTitle = item.getString("title");
    if (itemTitle.equals(titleToMatch)) {
      return true;
    } else {
      return false;
    }
  }

  private JsonObject searchForRequests(String cql) {
    CompletableFuture<Response> searchCompleted = new CompletableFuture<>();
    try {
      String url = requestStorageUrl("").toString() + "?query=" + urlEncode(cql);

      client.get(url, TENANT_ID, responseHandler(searchCompleted));
      Response searchResponse = searchCompleted.get(5, SECONDS);

      assertThat(searchResponse.getStatusCode(), is(200));
      return searchResponse.getJson();
    } catch (Exception e) {
      return new JsonObject("");
    }
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

  private ArrayList<String> createRequests()
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
    final UUID isbnIdentifierId = UUID.fromString("8261054f-be78-422d-bd51-4ed9f33c3422");
    ArrayList<RequestItemSummary> items = new ArrayList<RequestItemSummary>();
    ArrayList<String> requestIds = new ArrayList<String>();

    items.add(new RequestItemSummary("Nod", "000000001")
        .addIdentifier(isbnIdentifierId, "0562167542"));
    items.add(new RequestItemSummary("Temeraire", "000000002")
        .addIdentifier(isbnIdentifierId, "0007258712")
        .addIdentifier(isbnIdentifierId, "9780007258710"));
    items.add(new RequestItemSummary("Small Angry Planet", "000000003")
        .addIdentifier(isbnIdentifierId, "9781473619777"));
    items.add(new RequestItemSummary("Uprooted", "565575337892")
        .addIdentifier(isbnIdentifierId, "9781447294146")
        .addIdentifier(isbnIdentifierId, "1447294149"));
    // Interesting Times has two ISBNs: 0552167541, 978-0-552-16754-3
    items.add(new RequestItemSummary("Interesting Times", "000000004")
        .addIdentifier(isbnIdentifierId, "978-0-552-16754-3")
        .addIdentifier(isbnIdentifierId, "0552167541"));

    for (RequestItemSummary item : items) {
      JsonObject representation = createEntity(
            new RequestRequestBuilder()
                .recall()
                .toHoldShelf()
                .withItem(item)
                .create(),
            requestStorageUrl()
        ).getJson();
      requestIds.add(representation.getString("id"));
    }

    return requestIds;
  }
}
