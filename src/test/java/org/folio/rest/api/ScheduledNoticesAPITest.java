package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import io.vertx.core.json.JsonObject;

import org.junit.Before;
import org.junit.Test;

import org.folio.rest.jaxrs.model.NoticeConfig;
import org.folio.rest.jaxrs.model.ScheduledNotice;
import org.folio.rest.jaxrs.model.ScheduledNotices;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;

public class ScheduledNoticesAPITest extends ApiTests {

  @Before
  public void beforeEach() throws MalformedURLException {
    StorageTestSuite.deleteAll(scheduledNoticesStorageUrl("/scheduled-notices"));
  }

  @Test
  public void canSaveScheduledNotice() throws MalformedURLException,InterruptedException, ExecutionException,
    TimeoutException {

    Double nextRunTime = 1557981117909.0;
    NoticeConfig.Timing timing = NoticeConfig.Timing.BEFORE;
    Double recurringPeriod = 1117909.0;
    String templateId = "a0d83326-d47e-43c6-8da6-f972015c3b52";
    NoticeConfig.Format format = NoticeConfig.Format.EMAIL;

    String createdNoticeId = createScheduledNotice(nextRunTime, timing, recurringPeriod, templateId, format).getId();

    ScheduledNotice createdNotice = getById(scheduledNoticesStorageUrl("/scheduled-notices/" + createdNoticeId))
      .mapTo(ScheduledNotice.class);
    NoticeConfig createdConfig = createdNotice.getNoticeConfig();

    assertThat(createdNotice.getNextRunTime(), is(nextRunTime));
    assertThat(createdConfig.getTiming(), is(timing));
    assertThat(createdConfig.getRecurringPeriod(), is(recurringPeriod));
    assertThat(createdConfig.getTemplateId(), is(templateId));
    assertThat(createdConfig.getFormat(), is(format));
  }

  @Test
  public void canGetScheduledNoticesCollection() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    createScheduledNotice(1557981117909.0, NoticeConfig.Timing.BEFORE, 1117909.0,
      "a0d83326-d47e-43c6-8da6-f972015c3b52", NoticeConfig.Format.EMAIL);

    createScheduledNotice(1777983337909.0, NoticeConfig.Timing.AFTER, 2224355.0,
      "b84b721e-76ca-49ef-b486-f3debd92371d", NoticeConfig.Format.SMS);


    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl("/scheduled-notices"), TENANT_ID, ResponseHandler.json(getCompleted));
    JsonResponse response = getCompleted.get(5, SECONDS);
    ScheduledNotices scheduledNotices = response.getJson().mapTo(ScheduledNotices.class);

    assertThat(scheduledNotices.getScheduledNotices().size(), is(2));
    assertThat(scheduledNotices.getTotalRecords(), is(2));
  }

  @Test
  public void canGetScheduledNoticesCollectionByQuery() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    String templateId = "b84b721e-76ca-49ef-b486-f3debd92371d";

    createScheduledNotice(1557981117909.0, NoticeConfig.Timing.BEFORE, 1117909.0,
      "a0d83326-d47e-43c6-8da6-f972015c3b52", NoticeConfig.Format.EMAIL);

    createScheduledNotice(1777983337911.0, NoticeConfig.Timing.AFTER, 2224355.0,
      templateId, NoticeConfig.Format.SMS);

    createScheduledNotice(2883982217988.0, NoticeConfig.Timing.UPON_AT, 3337909.0,
      templateId, NoticeConfig.Format.SMS);

    String query = "query=noticeConfig.templateId=" + templateId;

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl("/scheduled-notices?" + query), TENANT_ID, ResponseHandler.json(getCompleted));
    JsonResponse response = getCompleted.get(5, SECONDS);

    ScheduledNotices scheduledNotices = response.getJson().mapTo(ScheduledNotices.class);

    assertThat(scheduledNotices.getScheduledNotices().size(), is(2));
    assertThat(scheduledNotices.getTotalRecords(), is(2));
  }

  @Test
  public void canGetScheduledNoticeById() throws MalformedURLException, InterruptedException,ExecutionException,
    TimeoutException {

    Double nextRunTime = 1557981117909.0;
    NoticeConfig.Timing timing = NoticeConfig.Timing.BEFORE;
    Double recurringPeriod = 1117909.0;
    String templateId = "a0d83326-d47e-43c6-8da6-f972015c3b52";
    NoticeConfig.Format format = NoticeConfig.Format.EMAIL;

    String createdNoticeId = createScheduledNotice(nextRunTime, timing, recurringPeriod, templateId, format).getId();

    ScheduledNotice receivedNotice = getById(scheduledNoticesStorageUrl("/scheduled-notices/" + createdNoticeId))
      .mapTo(ScheduledNotice.class);
    NoticeConfig receivedConfig = receivedNotice.getNoticeConfig();

    assertThat(receivedNotice.getNextRunTime(), is(nextRunTime));
    assertThat(receivedConfig.getTiming(), is(timing));
    assertThat(receivedConfig.getRecurringPeriod(), is(recurringPeriod));
    assertThat(receivedConfig.getTemplateId(), is(templateId));
    assertThat(receivedConfig.getFormat(), is(format));
  }

  @Test
  public void canUpdateScheduledNoticeById() throws MalformedURLException, InterruptedException,ExecutionException,
    TimeoutException {

    String noticeId = createScheduledNotice(1557981117909.0, NoticeConfig.Timing.BEFORE, 1117909.0,
      "a0d83326-d47e-43c6-8da6-f972015c3b52", NoticeConfig.Format.EMAIL).getId();

    NoticeConfig newConfig = new NoticeConfig()
      .withTiming(NoticeConfig.Timing.AFTER)
      .withRecurringPeriod(2226811.0)
      .withTemplateId("9e249cc2-fca6-46e0-b3bb-803489c7726b")
      .withFormat(NoticeConfig.Format.SMS);

    ScheduledNotice newNotice = new ScheduledNotice()
      .withNextRunTime(1437981227918.0)
      .withNoticeConfig(newConfig);

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();
    client.put(scheduledNoticesStorageUrl("/scheduled-notices/" + noticeId), JsonObject.mapFrom(newNotice),
      TENANT_ID, ResponseHandler.empty(putCompleted));
    putCompleted.get(5, SECONDS);

    ScheduledNotice updatedNotice = getById(scheduledNoticesStorageUrl("/scheduled-notices/" + noticeId)).mapTo(ScheduledNotice.class);
    NoticeConfig updatedConfig = updatedNotice.getNoticeConfig();

    assertThat(updatedNotice.getNextRunTime(), is(newNotice.getNextRunTime()));
    assertThat(updatedConfig.getTiming(), is(newConfig.getTiming()));
    assertThat(updatedConfig.getRecurringPeriod(), is(newConfig.getRecurringPeriod()));
    assertThat(updatedConfig.getTemplateId(), is(newConfig.getTemplateId()));
    assertThat(updatedConfig.getFormat(), is(newConfig.getFormat()));
  }

  @Test
  public void canDeleteScheduledNoticeById() throws InterruptedException, MalformedURLException, TimeoutException,
    ExecutionException {

    String noticeId = createScheduledNotice(1557981117909.0, NoticeConfig.Timing.BEFORE, 1117909.0,
      "a0d83326-d47e-43c6-8da6-f972015c3b52", NoticeConfig.Format.EMAIL).getId();

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    client.delete(scheduledNoticesStorageUrl("/scheduled-notices/" + noticeId), TENANT_ID, ResponseHandler.empty(deleteCompleted));
    deleteCompleted.get(5, SECONDS);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl("/scheduled-notices/" + noticeId), TENANT_ID, ResponseHandler.empty(getCompleted));
    Response response = getCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(404));
  }

  @Test
  public void canDeleteAllScheduledNotices() throws InterruptedException, MalformedURLException, TimeoutException,
    ExecutionException {

    createScheduledNotice(1557981117909.0, NoticeConfig.Timing.BEFORE, 1117909.0,
      "a0d83326-d47e-43c6-8da6-f972015c3b52", NoticeConfig.Format.EMAIL);

    createScheduledNotice(1777983337909.0, NoticeConfig.Timing.AFTER, 2224355.0,
      "b84b721e-76ca-49ef-b486-f3debd92371d", NoticeConfig.Format.SMS);

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    client.delete(scheduledNoticesStorageUrl("/scheduled-notices"), TENANT_ID, ResponseHandler.empty(deleteCompleted));
    deleteCompleted.get(5, SECONDS);

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl("/scheduled-notices"), TENANT_ID, ResponseHandler.json(getCompleted));
    JsonResponse response = getCompleted.get(5, SECONDS);
    ScheduledNotices scheduledNotices = response.getJson().mapTo(ScheduledNotices.class);

    assertThat(scheduledNotices.getScheduledNotices().size(), is(0));
    assertThat(scheduledNotices.getTotalRecords(), is(0));
  }

  @Test
  public void canDeleteScheduledNoticesByQuery() throws Exception {

    String templateId = "b84b721e-76ca-49ef-b486-f3debd92371d";

    createScheduledNotice(1557981117909.0, NoticeConfig.Timing.BEFORE, 1117909.0,
      "a0d83326-d47e-43c6-8da6-f972015c3b52", NoticeConfig.Format.EMAIL);

    createScheduledNotice(1777983337911.0, NoticeConfig.Timing.AFTER, 2224355.0,
      templateId, NoticeConfig.Format.SMS);

    createScheduledNotice(2883982217988.0, NoticeConfig.Timing.UPON_AT, 3337909.0,
      templateId, NoticeConfig.Format.SMS);

    String query = "query=noticeConfig.templateId=" + templateId;

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    client.delete(scheduledNoticesStorageUrl("/scheduled-notices?" + query), TENANT_ID, ResponseHandler.empty(deleteCompleted));
    deleteCompleted.get(5, SECONDS);

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl("/scheduled-notices?" + query), TENANT_ID, ResponseHandler.json(getCompleted));
    JsonResponse response = getCompleted.get(5, SECONDS);

    ScheduledNotices scheduledNotices = response.getJson().mapTo(ScheduledNotices.class);

    assertThat(scheduledNotices.getScheduledNotices().size(), is(0));
    assertThat(scheduledNotices.getTotalRecords(), is(0));
  }

  @Test
  public void cannotDeleteScheduledNoticesWithInvalidQuery() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    String query = "query=invalidQuery";

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    client.delete(scheduledNoticesStorageUrl("/scheduled-notices?" + query), TENANT_ID, ResponseHandler.empty(deleteCompleted));
    Response response = deleteCompleted.get(5, SECONDS);

    assertThat(response.getStatusCode(), is(400));
  }

  @Test
  public void canSaveScheduledNoticeCollection() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    ScheduledNotice notice1 = buildScheduledNotice(2883982217988.0, NoticeConfig.Timing.UPON_AT, 3337909.0,
      "b84b721e-76ca-49ef-b486-f3debd92371d", NoticeConfig.Format.SMS);

    ScheduledNotice notice2 = buildScheduledNotice(1557981117909.0, NoticeConfig.Timing.BEFORE, 1117909.0,
      "a0d83326-d47e-43c6-8da6-f972015c3b52", NoticeConfig.Format.EMAIL);

    ScheduledNotice notice3 = buildScheduledNotice(1777983337911.0, NoticeConfig.Timing.AFTER, 2224355.0,
      "b84b721e-76ca-49ef-b486-f3debd92371d", NoticeConfig.Format.SMS);

    ScheduledNotices collection = new ScheduledNotices()
      .withScheduledNotices(Arrays.asList(notice1, notice2, notice3));

    CompletableFuture<JsonResponse> saveCompleted = new CompletableFuture<>();
    client.post(scheduledNoticesStorageUrl("/bulk-save"), JsonObject.mapFrom(collection), TENANT_ID,
      ResponseHandler.json(saveCompleted));
    saveCompleted.get(5, SECONDS);

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl("/scheduled-notices"), TENANT_ID, ResponseHandler.json(getCompleted));
    JsonResponse response = getCompleted.get(5, SECONDS);
    ScheduledNotices scheduledNotices = response.getJson().mapTo(ScheduledNotices.class);

    assertThat(scheduledNotices.getScheduledNotices().size(), is(3));
    assertThat(scheduledNotices.getTotalRecords(), is(3));
  }

  private ScheduledNotice createScheduledNotice(Double nextRunTime,
                                                NoticeConfig.Timing timing,
                                                Double recurringPeriod,
                                                String templateId,
                                                NoticeConfig.Format format)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    ScheduledNotice notice = buildScheduledNotice(nextRunTime, timing, recurringPeriod, templateId, format);

    return createEntity(JsonObject.mapFrom(notice), scheduledNoticesStorageUrl("/scheduled-notices"))
      .getJson()
      .mapTo(ScheduledNotice.class);
  }

  private ScheduledNotice buildScheduledNotice(Double nextRunTime,
                                               NoticeConfig.Timing timing,
                                               Double recurringPeriod,
                                               String templateId,
                                               NoticeConfig.Format format) {

    NoticeConfig config = new NoticeConfig()
      .withTiming(timing)
      .withRecurringPeriod(recurringPeriod)
      .withTemplateId(templateId)
      .withFormat(format);

    return new ScheduledNotice()
      .withNextRunTime(nextRunTime)
      .withNoticeConfig(config);
  }

  private static URL scheduledNoticesStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/scheduled-notice-storage" + subPath);
  }
}
