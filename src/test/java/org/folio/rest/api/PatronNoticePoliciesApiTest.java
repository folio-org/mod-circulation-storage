package org.folio.rest.api;

import org.folio.rest.jaxrs.model.PatronNoticePolicy;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.hamcrest.CoreMatchers;
import org.hamcrest.junit.MatcherAssert;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PatronNoticePoliciesApiTest extends ApiTests {

  @Test
  public void canCreatePatronNoticePolicy() throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    PatronNoticePolicy entity = new PatronNoticePolicy();
    entity.setName("name");
    entity.setDescription("desc");
    entity.setActive(true);

    assertCreatePatronNoticePolicy(entity);
  }

  @Test
  public void canUpdatePatronNoticePolicy() throws MalformedURLException, InterruptedException, ExecutionException,
    TimeoutException {

    PatronNoticePolicy entity = new PatronNoticePolicy();
    entity.setName("testPolicy");
    entity.setDescription("Test Patron Notice Policy");
    entity.setActive(true);

    String id = assertCreatePatronNoticePolicy(entity).getId();

    entity.setId(id);
    entity.setDescription("New description");

    assertUpdatePatronNoticePolicy(entity);
  }

  private IndividualResource assertCreatePatronNoticePolicy(PatronNoticePolicy entity) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    client.post(patronNoticePoliciesStorageUrl(), entity, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    JsonResponse response = createCompleted.get(5, TimeUnit.SECONDS);

    MatcherAssert.assertThat("Failed to create patron notice policy", response.getStatusCode(), CoreMatchers.is(201));

    return new IndividualResource(response);
  }

  private void assertUpdatePatronNoticePolicy(PatronNoticePolicy entity) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<Response> updateCompleted = new CompletableFuture<>();

    client.put(patronNoticePoliciesStorageUrl("/" + entity.getId()), entity, StorageTestSuite.TENANT_ID,
      ResponseHandler.empty(updateCompleted));

    Response response = updateCompleted.get(5, TimeUnit.SECONDS);

    MatcherAssert.assertThat("Failed to update patron notice policy", response.getStatusCode(), CoreMatchers.is(204));
  }

  private static URL patronNoticePoliciesStorageUrl() throws MalformedURLException {
    return patronNoticePoliciesStorageUrl("");
  }

  private static URL patronNoticePoliciesStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/patron-notice-policy-storage/patron-notice-policies" + subPath);
  }
}
