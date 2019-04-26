package org.folio.rest.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.collection.IsArrayContainingInAnyOrder.arrayContainingInAnyOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import static org.folio.rest.api.CirculationRulesApiTest.rulesStorageUrl;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.impl.PatronNoticePoliciesAPI.NOT_FOUND;
import static org.folio.rest.impl.PatronNoticePoliciesAPI.PATRON_NOTICE_POLICY_TABLE;
import static org.folio.rest.impl.PatronNoticePoliciesAPI.STATUS_CODE_DUPLICATE_NAME;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.UpdateResult;

import org.junit.After;
import org.junit.Test;

import org.folio.rest.jaxrs.model.CirculationRules;
import org.folio.rest.jaxrs.model.LoanNotice;
import org.folio.rest.jaxrs.model.PatronNoticePolicy;
import org.folio.rest.jaxrs.model.RequestNotice;
import org.folio.rest.jaxrs.model.SendOptions;
import org.folio.rest.jaxrs.model.SendOptions__;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;

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
      .getInstance(StorageTestSuite.getVertx(), TENANT_ID)
      .delete(PATRON_NOTICE_POLICY_TABLE, new Criterion(), del -> future.complete(del.result()));
    future.join();
  }

  @Test
  public void canCreatePatronNoticePolicy() throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    JsonResponse response = createPatronNoticePolicy(firstPolicy);

    assertThat("Failed to create patron notice policy", response.getStatusCode(), is(201));
  }

  @Test
  public void cannotCreatePatronNoticePolicyWithNotUniqueName() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    createPatronNoticePolicy(firstPolicy);
    JsonResponse response = createPatronNoticePolicy(firstPolicy);
    String code = response.getJson().getJsonArray("errors").getJsonObject(0).getString("code");

    assertThat(response.getStatusCode(), is(422));
    assertThat(code, is(STATUS_CODE_DUPLICATE_NAME));
  }

  @Test
  public void canUpdatePatronNoticePolicy() throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    String id = createPatronNoticePolicy(firstPolicy).getJson().getString("id");

    firstPolicy.setId(id);
    firstPolicy.setDescription("description");
    firstPolicy.setRequestNotices(createRequestNotices());

    Response response = updatePatronNoticePolicy(firstPolicy);

    assertThat("Failed to update patron notice policy", response.getStatusCode(), is(204));
    assertThat(firstPolicy.getRequestNotices().size(), is(1));
    assertThat(firstPolicy.getRequestNotices().get(0).getName(), is("Test request name"));
    assertThat(firstPolicy.getRequestNotices().get(0).getFormat(), is(RequestNotice.Format.EMAIL));
    assertThat(firstPolicy.getRequestNotices().get(0).getFrequency(), is(RequestNotice.Frequency.ONE_TIME));
    assertThat(firstPolicy.getRequestNotices().get(0).getSendOptions().getSendWhen(), is(SendOptions__.SendWhen.RECALL_REQUEST));

    firstPolicy.setLoanNotices(createLoanNotices());

    response = updatePatronNoticePolicy(firstPolicy);

    assertThat("Failed to update patron notice policy", response.getStatusCode(), is(204));
    assertThat(firstPolicy.getLoanNotices().get(0).getSendOptions().getSendWhen(), is(SendOptions.SendWhen.CHECK_IN));
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

    assertThat(response.getStatusCode(), is(422));
    assertThat(code, is(STATUS_CODE_DUPLICATE_NAME));
  }

  @Test
  public void cannotUpdateNonexistentPatronNoticePolicy() throws InterruptedException, MalformedURLException,
    TimeoutException, ExecutionException {

    JsonResponse response = updatePatronNoticePolicy(nonexistentPolicy);

    assertThat(response.getStatusCode(), is(404));
  }

  @Test
  public void canGetAllPatronNoticePolicies() throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    createPatronNoticePolicy(firstPolicy);
    createPatronNoticePolicy(secondPolicy);

    CompletableFuture<JsonResponse> getAllCompleted = new CompletableFuture<>();

    client.get(patronNoticePoliciesStorageUrl(), TENANT_ID, ResponseHandler.json(getAllCompleted));

    JsonResponse response = getAllCompleted.get(5, TimeUnit.SECONDS);

    assertThat("Failed to get all patron notice policies", response.getStatusCode(), is(200));
    JsonArray policies = response.getJson().getJsonArray("patronNoticePolicies");

    assertThat(policies.size(), is(2));
    assertThat(response.getJson().getInteger("totalRecords"), is(2));

    String[] names = policies.stream()
      .map(o -> (JsonObject) o)
      .map(o -> o.mapTo(PatronNoticePolicy.class))
      .map(PatronNoticePolicy::getName)
      .toArray(String[]::new);

    assertThat(names, arrayContainingInAnyOrder(firstPolicy.getName(), secondPolicy.getName()));
  }

  @Test
  public void canGetPatronNoticePolicy() throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    String id = createPatronNoticePolicy(firstPolicy).getJson().getString("id");

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(patronNoticePoliciesStorageUrl("/" + id), TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getJson().getString("id"), is(id));
    assertThat(response.getJson().getString("name"), is(firstPolicy.getName()));
  }

  @Test
  public void cannotGetNonexistentPatronNoticePolicy() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();

    client.get(patronNoticePoliciesStorageUrl("/" + nonexistentPolicy.getId()),
      TENANT_ID, ResponseHandler.json(getCompleted));

    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(404));
    assertThat(response.getBody(), is(NOT_FOUND));
  }

  @Test
  public void canDeletePatronNoticePolicy() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    String id = createPatronNoticePolicy(firstPolicy).getJson().getString("id");
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

    CirculationRules circulationRules = new CirculationRules()
      .withRulesAsText(rulesAsText);

    CompletableFuture<JsonResponse> putCompleted = new CompletableFuture<>();
    client.put(rulesStorageUrl(), circulationRules, TENANT_ID, ResponseHandler.json(putCompleted));
    putCompleted.join();

    JsonResponse response = deletePatronNoticePolicy(inUsePolicyId);
    String message = response.getJson().getJsonArray("errors").getJsonObject(0).getString("message");

    assertThat(response.getStatusCode(), is(422));
    assertThat(message, is("Cannot delete in use notice policy"));
  }

  private JsonResponse createPatronNoticePolicy(PatronNoticePolicy entity) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(patronNoticePoliciesStorageUrl(), entity, TENANT_ID,
      ResponseHandler.json(createCompleted));

    return createCompleted.get(5, TimeUnit.SECONDS);
  }

  private JsonResponse updatePatronNoticePolicy(PatronNoticePolicy entity) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> updateCompleted = new CompletableFuture<>();

    client.put(patronNoticePoliciesStorageUrl("/" + entity.getId()), entity, TENANT_ID,
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

  private List<RequestNotice> createRequestNotices() {
    RequestNotice requestNotice = new RequestNotice();
    requestNotice.setName("Test request name");
    requestNotice.setTemplateId(UUID.randomUUID().toString());
    requestNotice.setTemplateName("Test template name");
    requestNotice.setFormat(RequestNotice.Format.EMAIL);
    requestNotice.setFrequency(RequestNotice.Frequency.ONE_TIME);
    requestNotice.setRealTime(Boolean.TRUE);
    SendOptions__.SendHow sendHow = SendOptions__.SendHow.BEFORE;
    SendOptions__.SendWhen sendWhen = SendOptions__.SendWhen.RECALL_REQUEST;
    SendOptions__ sendOptions = new SendOptions__();
    sendOptions.setSendHow(sendHow);
    sendOptions.setSendWhen(sendWhen);
    requestNotice.setSendOptions(sendOptions);
    return Collections.singletonList(requestNotice);
  }

  private List<LoanNotice> createLoanNotices() {
    LoanNotice loanNotice = new LoanNotice();
    loanNotice.setTemplateId(UUID.randomUUID().toString());
    loanNotice.setFormat(LoanNotice.Format.EMAIL);
    loanNotice.setRealTime(Boolean.TRUE);
    SendOptions sendOptions = new SendOptions();
    sendOptions.setSendWhen(SendOptions.SendWhen.CHECK_IN);
    loanNotice.setSendOptions(sendOptions);
    return Collections.singletonList(loanNotice);
  }
}
