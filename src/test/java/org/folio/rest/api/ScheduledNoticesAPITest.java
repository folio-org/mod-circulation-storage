package org.folio.rest.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.json.JsonObject;

import org.junit.Before;
import org.junit.Test;

import org.folio.rest.jaxrs.model.NoticeConfig;
import org.folio.rest.jaxrs.model.ScheduledNotice;
import org.folio.rest.jaxrs.model.ScheduledNotices;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;

public class ScheduledNoticesAPITest extends ApiTests {

  private static final String SCHEDULED_NOTICE_TABLE = "scheduled_notice";

  private NoticeConfig config = new NoticeConfig()
    .withTiming(NoticeConfig.Timing.BEFORE)
    .withRecurringPeriod(1117909.0)
    .withTemplateId("a0d83326-d47e-43c6-8da6-f972015c3b52")
    .withFormat(NoticeConfig.Format.EMAIL);

  private ScheduledNotice notice = new ScheduledNotice()
    .withNextRunTime(1557981117909.0)
    .withNoticeConfig(config);

  @Before
  public void beforeEach() throws InterruptedException, ExecutionException, TimeoutException {

    PostgresClient pgClient = PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_ID);
    CompletableFuture<Void> deleteCompleted = new CompletableFuture<>();
    pgClient.delete(SCHEDULED_NOTICE_TABLE, new Criterion(), delete -> deleteCompleted.complete(null));
    deleteCompleted.get(5, TimeUnit.SECONDS);
  }

  @Test
  public void canCreateScheduledNotice() throws MalformedURLException,InterruptedException, ExecutionException,
    TimeoutException {

    ScheduledNotice createdNotice = createEntity(JsonObject.mapFrom(notice), scheduledNoticesStorageUrl())
      .getJson().mapTo(ScheduledNotice.class);
    NoticeConfig createdConfig = createdNotice.getNoticeConfig();

    assertThat(createdNotice.getNextRunTime(), is(notice.getNextRunTime()));
    assertThat(createdConfig.getTiming(), is(config.getTiming()));
    assertThat(createdConfig.getRecurringPeriod(), is(config.getRecurringPeriod()));
    assertThat(createdConfig.getTemplateId(), is(config.getTemplateId()));
    assertThat(createdConfig.getFormat(), is(config.getFormat()));
  }

  @Test
  public void canGetScheduledNoticesCollection() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    createEntity(JsonObject.mapFrom(notice), scheduledNoticesStorageUrl());

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl(), TENANT_ID, ResponseHandler.json(getCompleted));
    JsonResponse response = getCompleted.get(5, TimeUnit.SECONDS);
    ScheduledNotices scheduledNotices = response.getJson().mapTo(ScheduledNotices.class);

    assertThat(scheduledNotices.getTotalRecords(), is(1));
  }

  @Test
  public void canGetScheduledNoticeById() throws MalformedURLException, InterruptedException,ExecutionException,
    TimeoutException {

    ScheduledNotice createdNotice = createScheduledNotice();
    ScheduledNotice receivedNotice = getById(scheduledNoticesStorageUrl("/" + createdNotice.getId()))
      .mapTo(ScheduledNotice.class);
    NoticeConfig recievedConfig = receivedNotice.getNoticeConfig();

    assertThat(createdNotice.getNextRunTime(), is(notice.getNextRunTime()));
    assertThat(recievedConfig.getTiming(), is(config.getTiming()));
    assertThat(recievedConfig.getRecurringPeriod(), is(config.getRecurringPeriod()));
    assertThat(recievedConfig.getTemplateId(), is(config.getTemplateId()));
    assertThat(recievedConfig.getFormat(), is(config.getFormat()));
  }

  @Test
  public void canUpdateScheduledNoticeById() throws MalformedURLException, InterruptedException,ExecutionException,
    TimeoutException {

    String noticeId = createScheduledNotice().getId();

    NoticeConfig newConfig = new NoticeConfig()
      .withTiming(NoticeConfig.Timing.AFTER)
      .withRecurringPeriod(2226811.0)
      .withTemplateId("9e249cc2-fca6-46e0-b3bb-803489c7726b")
      .withFormat(NoticeConfig.Format.SMS);

    ScheduledNotice newNotice = new ScheduledNotice()
      .withNextRunTime(1437981227918.0)
      .withNoticeConfig(newConfig);

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();
    client.put(scheduledNoticesStorageUrl("/" + noticeId), JsonObject.mapFrom(newNotice),
      TENANT_ID, ResponseHandler.empty(putCompleted));
    putCompleted.get(5, TimeUnit.SECONDS);

    ScheduledNotice updatedNotice = getById(scheduledNoticesStorageUrl("/" + noticeId)).mapTo(ScheduledNotice.class);
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

    String noticeId = createScheduledNotice().getId();

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    client.delete(scheduledNoticesStorageUrl("/" + noticeId), TENANT_ID, ResponseHandler.empty(deleteCompleted));
    deleteCompleted.get(5, TimeUnit.SECONDS);

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl("/" + noticeId), TENANT_ID, ResponseHandler.empty(getCompleted));
    Response response = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(404));
  }

  private ScheduledNotice createScheduledNotice() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {
    return createEntity(JsonObject.mapFrom(notice), scheduledNoticesStorageUrl())
      .getJson()
      .mapTo(ScheduledNotice.class);
  }

  private static URL scheduledNoticesStorageUrl() throws MalformedURLException {
    return scheduledNoticesStorageUrl("");
  }

  private static URL scheduledNoticesStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/scheduled-notice-storage/scheduled-notices" + subPath);
  }
}
