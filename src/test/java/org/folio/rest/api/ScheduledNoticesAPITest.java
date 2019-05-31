package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import static org.folio.rest.api.StorageTestSuite.TENANT_ID;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import io.vertx.core.json.JsonObject;

import org.junit.Before;
import org.junit.Test;

import org.folio.rest.jaxrs.model.NoticeConfig;
import org.folio.rest.jaxrs.model.RecurringPeriod;
import org.folio.rest.jaxrs.model.ScheduledNotice;
import org.folio.rest.jaxrs.model.ScheduledNotices;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;

public class ScheduledNoticesAPITest extends ApiTests {

  private static final RecurringPeriod ONE_DAY_PERIOD = new RecurringPeriod()
    .withDuration(1)
    .withIntervalId(RecurringPeriod.IntervalId.DAYS);

  private static final RecurringPeriod ONE_MONTH_PERIOD = new RecurringPeriod()
    .withDuration(1)
    .withIntervalId(RecurringPeriod.IntervalId.MONTHS);

  @Before
  public void beforeEach() throws MalformedURLException {
    StorageTestSuite.deleteAll(scheduledNoticesStorageUrl("/scheduled-notices"));
  }

  @Test
  public void canSaveScheduledNotice() throws MalformedURLException,InterruptedException, ExecutionException,
    TimeoutException {

    Date nextRunTime = new Date();
    NoticeConfig.Timing timing = NoticeConfig.Timing.BEFORE;
    String templateId = "a0d83326-d47e-43c6-8da6-f972015c3b52";
    NoticeConfig.Format format = NoticeConfig.Format.EMAIL;

    String createdNoticeId = createScheduledNotice(nextRunTime, timing, ONE_DAY_PERIOD, templateId, format).getId();

    ScheduledNotice createdNotice = getById(scheduledNoticesStorageUrl("/scheduled-notices/" + createdNoticeId))
      .mapTo(ScheduledNotice.class);
    NoticeConfig createdConfig = createdNotice.getNoticeConfig();

    assertThat(createdNotice.getNextRunTime(), is(nextRunTime));
    assertThat(createdConfig.getTiming(), is(timing));
    assertThat(createdConfig.getRecurringPeriod().getIntervalId(), is(ONE_DAY_PERIOD.getIntervalId()));
    assertThat(createdConfig.getRecurringPeriod().getDuration(), is(ONE_DAY_PERIOD.getDuration()));
    assertThat(createdConfig.getTemplateId(), is(templateId));
    assertThat(createdConfig.getFormat(), is(format));
  }

  @Test
  public void canGetScheduledNoticesCollection() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    createScheduledNotice(NoticeConfig.Timing.BEFORE, ONE_DAY_PERIOD,
      "a0d83326-d47e-43c6-8da6-f972015c3b52", NoticeConfig.Format.EMAIL);

    createScheduledNotice(NoticeConfig.Timing.AFTER, ONE_MONTH_PERIOD,
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

    createScheduledNotice(NoticeConfig.Timing.BEFORE, ONE_DAY_PERIOD,
      "a0d83326-d47e-43c6-8da6-f972015c3b52", NoticeConfig.Format.EMAIL);

    createScheduledNotice(NoticeConfig.Timing.AFTER, ONE_DAY_PERIOD,
      templateId, NoticeConfig.Format.SMS);

    createScheduledNotice(NoticeConfig.Timing.UPON_AT, ONE_MONTH_PERIOD,
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

    Date nextRunTime = new Date();
    NoticeConfig.Timing timing = NoticeConfig.Timing.BEFORE;
    String templateId = "a0d83326-d47e-43c6-8da6-f972015c3b52";
    NoticeConfig.Format format = NoticeConfig.Format.EMAIL;

    String createdNoticeId = createScheduledNotice(nextRunTime, timing, ONE_DAY_PERIOD, templateId, format).getId();

    ScheduledNotice receivedNotice = getById(scheduledNoticesStorageUrl("/scheduled-notices/" + createdNoticeId))
      .mapTo(ScheduledNotice.class);
    NoticeConfig receivedConfig = receivedNotice.getNoticeConfig();

    assertThat(receivedNotice.getNextRunTime(), is(nextRunTime));
    assertThat(receivedConfig.getTiming(), is(timing));
    assertThat(receivedConfig.getRecurringPeriod().getIntervalId(), is(ONE_DAY_PERIOD.getIntervalId()));
    assertThat(receivedConfig.getRecurringPeriod().getDuration(), is(ONE_DAY_PERIOD.getDuration()));
    assertThat(receivedConfig.getTemplateId(), is(templateId));
    assertThat(receivedConfig.getFormat(), is(format));
  }

  @Test
  public void canUpdateScheduledNoticeById() throws MalformedURLException, InterruptedException,ExecutionException,
    TimeoutException {

    String noticeId = createScheduledNotice(NoticeConfig.Timing.BEFORE, ONE_DAY_PERIOD,
      "a0d83326-d47e-43c6-8da6-f972015c3b52", NoticeConfig.Format.EMAIL).getId();

    RecurringPeriod period = new RecurringPeriod()
      .withDuration(1)
      .withIntervalId(RecurringPeriod.IntervalId.DAYS);

    NoticeConfig newConfig = new NoticeConfig()
      .withTiming(NoticeConfig.Timing.AFTER)
      .withRecurringPeriod(period)
      .withTemplateId("9e249cc2-fca6-46e0-b3bb-803489c7726b")
      .withFormat(NoticeConfig.Format.SMS);

    ScheduledNotice newNotice = new ScheduledNotice()
      .withNextRunTime(new Date())
      .withNoticeConfig(newConfig);

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();
    client.put(scheduledNoticesStorageUrl("/scheduled-notices/" + noticeId), JsonObject.mapFrom(newNotice),
      TENANT_ID, ResponseHandler.empty(putCompleted));
    putCompleted.get(5, SECONDS);

    ScheduledNotice updatedNotice = getById(scheduledNoticesStorageUrl("/scheduled-notices/" + noticeId)).mapTo(ScheduledNotice.class);
    NoticeConfig updatedConfig = updatedNotice.getNoticeConfig();

    assertThat(updatedNotice.getNextRunTime(), is(newNotice.getNextRunTime()));
    assertThat(updatedConfig.getTiming(), is(newConfig.getTiming()));
    assertThat(updatedConfig.getTemplateId(), is(newConfig.getTemplateId()));
    assertThat(updatedConfig.getFormat(), is(newConfig.getFormat()));
  }

  @Test
  public void canDeleteScheduledNoticeById() throws InterruptedException, MalformedURLException, TimeoutException,
    ExecutionException {

    String noticeId = createScheduledNotice(NoticeConfig.Timing.BEFORE, ONE_DAY_PERIOD,
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

    createScheduledNotice(NoticeConfig.Timing.BEFORE, ONE_DAY_PERIOD,
      "a0d83326-d47e-43c6-8da6-f972015c3b52", NoticeConfig.Format.EMAIL);

    createScheduledNotice(NoticeConfig.Timing.AFTER, ONE_MONTH_PERIOD,
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

    createScheduledNotice(NoticeConfig.Timing.BEFORE, ONE_DAY_PERIOD,
      "a0d83326-d47e-43c6-8da6-f972015c3b52", NoticeConfig.Format.EMAIL);

    createScheduledNotice(NoticeConfig.Timing.AFTER, ONE_DAY_PERIOD,
      templateId, NoticeConfig.Format.SMS);

    createScheduledNotice(NoticeConfig.Timing.UPON_AT, ONE_MONTH_PERIOD,
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

  private ScheduledNotice createScheduledNotice(Date nextRunTime,
                                                NoticeConfig.Timing timing,
                                                RecurringPeriod recurringPeriod,
                                                String templateId,
                                                NoticeConfig.Format format)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    ScheduledNotice notice = buildScheduledNotice(nextRunTime, timing, recurringPeriod, templateId, format);

    return createEntity(JsonObject.mapFrom(notice), scheduledNoticesStorageUrl("/scheduled-notices"))
      .getJson()
      .mapTo(ScheduledNotice.class);
  }

  private ScheduledNotice createScheduledNotice(NoticeConfig.Timing timing,
                                                RecurringPeriod recurringPeriod,
                                                String templateId,
                                                NoticeConfig.Format format)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    return createScheduledNotice(new Date(), timing, recurringPeriod, templateId, format);
  }

  private ScheduledNotice buildScheduledNotice(Date nextRunTime,
                                               NoticeConfig.Timing timing,
                                               RecurringPeriod recurringPeriod,
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

  private ScheduledNotice buildScheduledNotice(NoticeConfig.Timing timing,
                                               RecurringPeriod recurringPeriod,
                                               String templateId,
                                               NoticeConfig.Format format) {

    return buildScheduledNotice(new Date(), timing, recurringPeriod, templateId, format);
  }

  private static URL scheduledNoticesStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/scheduled-notice-storage" + subPath);
  }
}
