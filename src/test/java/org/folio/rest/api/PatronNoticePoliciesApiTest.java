package org.folio.rest.api;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.UpdateResult;
import org.folio.rest.jaxrs.model.PatronNoticePolicy;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.hamcrest.CoreMatchers;
import org.hamcrest.junit.MatcherAssert;
import org.junit.After;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.rest.impl.PatronNoticePoliciesAPI.PATRON_NOTICE_POLICY_TABLE;
import static org.folio.rest.impl.PatronNoticePoliciesAPI.STATUS_CODE_DUPLICATE_NAME;
import static org.hamcrest.collection.IsArrayContainingInAnyOrder.arrayContainingInAnyOrder;

public class PatronNoticePoliciesApiTest extends ApiTests {

  private PatronNoticePolicy firstPolicy = new PatronNoticePolicy()
    .withName("firstPolicy");

  private PatronNoticePolicy secondPolicy = new PatronNoticePolicy()
    .withName("secondPolicy");

  private PatronNoticePolicy nonexistentPolicy = new PatronNoticePolicy()
    .withId("0f612a84-dc0f-4670-9017-62981ba63644")
    .withName("nonexistentPolicy");

  @After
  public void cleanUp() {
    CompletableFuture<UpdateResult> future = new CompletableFuture<>();
    PostgresClient
      .getInstance(StorageTestSuite.getVertx(), StorageTestSuite.TENANT_ID)
      .delete(PATRON_NOTICE_POLICY_TABLE, new Criterion(), del -> future.complete(del.result()));
    future.join();
  }

  @Test
  public void canCreatePatronNoticePolicy() throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    JsonResponse response = createPatronNoticePolicy(firstPolicy);

    MatcherAssert.assertThat("Failed to create patron notice policy", response.getStatusCode(), CoreMatchers.is(201));
  }

  @Test
  public void cannotCreatePatronNoticePolicyWithNotUniqueName() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    createPatronNoticePolicy(firstPolicy);
    JsonResponse response = createPatronNoticePolicy(firstPolicy);
    String code = response.getJson().getJsonArray("errors").getJsonObject(0).getString("code");

    MatcherAssert.assertThat(response.getStatusCode(), CoreMatchers.is(422));
    MatcherAssert.assertThat(code, CoreMatchers.is(STATUS_CODE_DUPLICATE_NAME));
  }

  @Test
  public void canUpdatePatronNoticePolicy() throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    String id = createPatronNoticePolicy(firstPolicy).getJson().getString("id");

    firstPolicy.setId(id);
    firstPolicy.setDescription("description");

    Response response = updatePatronNoticePolicy(firstPolicy);

    MatcherAssert.assertThat("Failed to update patron notice policy", response.getStatusCode(), CoreMatchers.is(204));
  }

  @Test
  public void cannotUpdatePatronNoticePolicyWithNotUniqueName() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    createPatronNoticePolicy(firstPolicy);

    String id = createPatronNoticePolicy(secondPolicy).getJson().getString("id");

    JsonResponse response = updatePatronNoticePolicy(secondPolicy
      .withId(id)
      .withName(firstPolicy.getName()));

    String code = response.getJson().getJsonArray("errors").getJsonObject(0).getString("code");

    MatcherAssert.assertThat(response.getStatusCode(), CoreMatchers.is(422));
    MatcherAssert.assertThat(code, CoreMatchers.is(STATUS_CODE_DUPLICATE_NAME));
  }

  @Test
  public void cannotUpdateNonexistentPatronNoticePolicy() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonResponse response = updatePatronNoticePolicy(nonexistentPolicy);

    MatcherAssert.assertThat(response.getStatusCode(), CoreMatchers.is(404));
  }

  @Test
  public void canGetAllPatronNoticePolicies() throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    createPatronNoticePolicy(firstPolicy);
    createPatronNoticePolicy(secondPolicy);

    CompletableFuture<JsonResponse> getAllCompleted = new CompletableFuture<>();

    client.get(patronNoticePoliciesStorageUrl(), StorageTestSuite.TENANT_ID, ResponseHandler.json(getAllCompleted));

    JsonResponse response = getAllCompleted.get(5, TimeUnit.SECONDS);

    MatcherAssert.assertThat("Failed to get all patron notice policies", response.getStatusCode(), CoreMatchers.is(200));
    JsonArray policies = response.getJson().getJsonArray("patronNoticePolicies");

    MatcherAssert.assertThat(policies.size(), CoreMatchers.is(2));
    MatcherAssert.assertThat(response.getJson().getInteger("totalRecords"), CoreMatchers.is(2));

    String[] names = policies.stream()
      .map(o -> (JsonObject) o)
      .map(o -> o.mapTo(PatronNoticePolicy.class))
      .map(PatronNoticePolicy::getName)
      .toArray(String[]::new);

    MatcherAssert.assertThat(names, arrayContainingInAnyOrder(firstPolicy.getName(), secondPolicy.getName()));
  }

  private JsonResponse createPatronNoticePolicy(PatronNoticePolicy entity) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(patronNoticePoliciesStorageUrl(), entity, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    return createCompleted.get(5, TimeUnit.SECONDS);
  }

  private JsonResponse updatePatronNoticePolicy(PatronNoticePolicy entity) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> updateCompleted = new CompletableFuture<>();

    client.put(patronNoticePoliciesStorageUrl("/" + entity.getId()), entity, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(updateCompleted));

    return updateCompleted.get(5, TimeUnit.SECONDS);
  }

  private static URL patronNoticePoliciesStorageUrl() throws MalformedURLException {
    return patronNoticePoliciesStorageUrl("");
  }

  private static URL patronNoticePoliciesStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/patron-notice-policy-storage/patron-notice-policies" + subPath);
  }
}
