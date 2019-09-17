package org.folio.rest.api;

import static org.folio.rest.api.CirculationRulesApiTest.rulesStorageUrl;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.impl.PatronNoticePoliciesAPI.NOT_FOUND;
import static org.folio.rest.impl.PatronNoticePoliciesAPI.PATRON_NOTICE_POLICY_TABLE;
import static org.folio.rest.impl.PatronNoticePoliciesAPI.STATUS_CODE_DUPLICATE_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsArrayContainingInAnyOrder.arrayContainingInAnyOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.TextResponse;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.UpdateResult;

public class PatronNoticePoliciesApiTest extends ApiTests {

  @Before
  public void cleanUp() {
    CompletableFuture<UpdateResult> future = new CompletableFuture<>();
    PostgresClient
      .getInstance(StorageTestSuite.getVertx(), TENANT_ID)
      .delete(PATRON_NOTICE_POLICY_TABLE, new Criterion(), del -> future.complete(del.result()));
    future.join();
  }

  @Test
  public void canCreatePatronNoticePolicy() throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    JsonObject sendOptions = new JsonObject()
      .put("sendWhen", "Request expiration");

    JsonObject requestNotice = new JsonObject()
      .put("templateId", UUID.randomUUID().toString())
      .put("format", "Email")
      .put("realTime", "true")
      .put("sendOptions", sendOptions);

    JsonObject manualDueDateChangeNotice = new JsonObject()
      .put("templateId", UUID.randomUUID().toString())
      .put("format", "Email")
      .put("realTime", "true")
      .put("sendOptions", new JsonObject()
        .put("sendWhen", "Manual due date change"));

    JsonObject noticePolicy = new JsonObject()
      .put("name", "sample policy")
      .put("requestNotices", new JsonArray().add(requestNotice))
      .put("loanNotices", new JsonArray().add(manualDueDateChangeNotice));

    JsonResponse response = postPatronNoticePolicy(noticePolicy);

    assertThat("Failed to create patron notice policy", response.getStatusCode(), is(201));
  }

  @Test
  public void cannotCreatePatronNoticePolicyWithNotUniqueName() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    JsonObject noticePolicy = new JsonObject()
      .put("name", "sample policy");
    postPatronNoticePolicy(noticePolicy);

    JsonResponse response = postPatronNoticePolicy(noticePolicy);
    String code = response.getJson().getJsonArray("errors").getJsonObject(0).getString("code");

    assertThat(response.getStatusCode(), is(422));
    assertThat(code, is(STATUS_CODE_DUPLICATE_NAME));
  }

  @Test
  public void canUpdatePatronNoticePolicy() throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    JsonObject noticePolicy = new JsonObject()
      .put("name", "sample policy");

    String id = postPatronNoticePolicy(noticePolicy).getJson().getString("id");

    JsonObject sendOptions = new JsonObject()
      .put("sendWhen", "Request expiration");

    String templateId = UUID.randomUUID().toString();
    JsonObject requestNotice = new JsonObject()
      .put("templateId", templateId)
      .put("format", "Email")
      .put("realTime", "true")
      .put("sendOptions", sendOptions);

    noticePolicy
      .put("id", id)
      .put("description", "new description")
      .put("requestNotices", new JsonArray().add(requestNotice));

    JsonResponse response = putPatronNoticePolicy(noticePolicy);

    assertThat("Failed to update patron notice policy", response.getStatusCode(), is(204));

    JsonObject updatedPolicy = getPatronNoticePolicyById(id).getJson();
    JsonObject updatedRequestNotice = updatedPolicy.getJsonArray("requestNotices").getJsonObject(0);
    JsonObject updatedSendOptions = updatedRequestNotice.getJsonObject("sendOptions");

    assertThat(updatedPolicy.getString("description"), is("new description"));
    assertThat(updatedRequestNotice.getString("templateId"), is(templateId));
    assertThat(updatedRequestNotice.getString("format"), is("Email"));
    assertThat(updatedRequestNotice.getBoolean("realTime"), is(true));
    assertThat(updatedSendOptions.getString("sendWhen"), is("Request expiration"));
  }

  @Test
  public void cannotUpdatePatronNoticePolicyWithNotUniqueName() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    JsonObject firstPolicy = new JsonObject()
      .put("name", "first policy");
    JsonObject secondPolicy = new JsonObject()
      .put("name", "second policy");

    postPatronNoticePolicy(firstPolicy);
    String id = postPatronNoticePolicy(secondPolicy).getJson().getString("id");

    JsonResponse response = putPatronNoticePolicy(secondPolicy
      .put("id", id)
      .put("name", firstPolicy.getString("name")));

    String code = response.getJson().getJsonArray("errors").getJsonObject(0).getString("code");

    assertThat(response.getStatusCode(), is(422));
    assertThat(code, is(STATUS_CODE_DUPLICATE_NAME));
  }

  @Test
  public void cannotUpdateNonexistentPatronNoticePolicy() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonObject nonexistentPolicy = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", "nonexistentPolicy");
    JsonResponse response = putPatronNoticePolicy(nonexistentPolicy);

    assertThat(response.getStatusCode(), is(404));
  }

  @Test
  public void canGetAllPatronNoticePolicies() throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    JsonObject firstPolicy = new JsonObject()
      .put("name", "first policy");
    JsonObject secondPolicy = new JsonObject()
      .put("name", "second policy");

    postPatronNoticePolicy(firstPolicy);
    postPatronNoticePolicy(secondPolicy);

    CompletableFuture<JsonResponse> getAllCompleted = new CompletableFuture<>();

    client.get(patronNoticePoliciesStorageUrl(), TENANT_ID, ResponseHandler.json(getAllCompleted));

    JsonResponse response = getAllCompleted.get(5, TimeUnit.SECONDS);

    assertThat("Failed to get all patron notice policies", response.getStatusCode(), is(200));
    JsonArray policies = response.getJson().getJsonArray("patronNoticePolicies");

    assertThat(policies.size(), is(2));
    assertThat(response.getJson().getInteger("totalRecords"), is(2));

    String[] names = policies.stream()
      .map(JsonObject.class::cast)
      .map(json -> json.getString("name"))
      .toArray(String[]::new);

    assertThat(names,
      arrayContainingInAnyOrder(firstPolicy.getString("name"), secondPolicy.getString("name")));
  }

  @Test
  public void canGetPatronNoticePolicy() throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    JsonObject noticePolicy = new JsonObject()
      .put("name", "sample policy");
    String id = postPatronNoticePolicy(noticePolicy).getJson().getString("id");

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(patronNoticePoliciesStorageUrl("/" + id), TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getJson().getString("id"), is(id));
    assertThat(response.getJson().getString("name"), is(noticePolicy.getString("name")));
  }

  @Test
  public void cannotGetNonexistentPatronNoticePolicy() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    JsonObject nonexistentPolicy = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("name", "nonexistentPolicy");

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(patronNoticePoliciesStorageUrl("/" + nonexistentPolicy.getString("id")),
      TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(404));
    assertThat(response.getBody(), is(NOT_FOUND));
  }

  @Test
  public void canDeletePatronNoticePolicy() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    JsonObject noticePolicy = new JsonObject()
      .put("name", "sample policy");
    String id = postPatronNoticePolicy(noticePolicy).getJson().getString("id");
    JsonResponse response = deletePatronNoticePolicy(id);

    assertThat(response.getStatusCode(), is(204));
  }

  @Test
  public void cannotDeleteNonexistentPatronNoticePolicyId() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    String id = UUID.randomUUID().toString();
    JsonResponse response = deletePatronNoticePolicy(id);

    assertThat(response.getStatusCode(), is(404));
    assertThat(response.getBody(), is(NOT_FOUND));
  }

  @Test
  public void cannotDeleteInUsePatronNoticePolicy() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    String inUsePolicyId = "16b88363-0d93-464a-967a-ad5ad0f9187c";

    String rulesAsText = "priority: t, s, c, b, a, m, g" +
      "fallback-policy: l 43198de5-f56a-4a53-a0bd-5a324418967a r 4c6e1fb0-2ef1-4666-bd15-f9190ff89060 " +
      "n 122b3d2b-4788-4f1e-9117-56daa91cb75c m 1a54b431-2e4f-452d-9cae-9cee66c9a892: " +
      "l d9cd0bed-1b49-4b5e-a7bd-064b8d177231 r 334e5a9e-94f9-4673-8d1d-ab552863886b n " + inUsePolicyId;

    JsonObject circulationRules = new JsonObject()
      .put("rulesAsText", rulesAsText);

    CompletableFuture<TextResponse> putCompleted = new CompletableFuture<>();
    client.put(rulesStorageUrl(), circulationRules, TENANT_ID, ResponseHandler.text(putCompleted));
    putCompleted.get(5, TimeUnit.SECONDS);

    JsonResponse response = deletePatronNoticePolicy(inUsePolicyId);
    String message = response.getBody();

    assertThat(response.getStatusCode(), is(400));
    assertThat(message, is("Cannot delete in use notice policy"));
  }

  private JsonResponse getPatronNoticePolicyById(String id)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(patronNoticePoliciesStorageUrl("/" + id), TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getCompleted.get(5, TimeUnit.SECONDS);
  }

  private JsonResponse postPatronNoticePolicy(JsonObject entity) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(patronNoticePoliciesStorageUrl(), entity, TENANT_ID,
      ResponseHandler.json(createCompleted));

    return createCompleted.get(5, TimeUnit.SECONDS);
  }

  private JsonResponse putPatronNoticePolicy(JsonObject entity) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> updateCompleted = new CompletableFuture<>();

    client.put(patronNoticePoliciesStorageUrl("/" + entity.getString("id")), entity, TENANT_ID,
      ResponseHandler.json(updateCompleted));

    return updateCompleted.get(5, TimeUnit.SECONDS);
  }

  private JsonResponse deletePatronNoticePolicy(String patronNoticePolicyId) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> deleteCompleted = new CompletableFuture<>();

    client.delete(patronNoticePoliciesStorageUrl("/" + patronNoticePolicyId), TENANT_ID,
      ResponseHandler.json(deleteCompleted));

    return deleteCompleted.get(5, TimeUnit.SECONDS);
  }

  private static URL patronNoticePoliciesStorageUrl() throws MalformedURLException {
    return patronNoticePoliciesStorageUrl("");
  }

  private static URL patronNoticePoliciesStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/patron-notice-policy-storage/patron-notice-policies" + subPath);
  }
}
