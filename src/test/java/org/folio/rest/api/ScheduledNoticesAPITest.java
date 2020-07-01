package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.folio.rest.jaxrs.model.NoticeConfig.Format.EMAIL;
import static org.folio.rest.jaxrs.model.NoticeConfig.Timing.UPON_AT;
import static org.folio.rest.support.clients.CqlQuery.exactMatch;
import static org.folio.rest.support.clients.CqlQuery.lessThen;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.joda.time.DateTimeZone.UTC;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.jaxrs.model.NoticeConfig;
import org.folio.rest.jaxrs.model.RecurringPeriod;
import org.folio.rest.jaxrs.model.ScheduledNotice;
import org.folio.rest.jaxrs.model.ScheduledNotices;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.Response;
import org.folio.rest.support.ResponseHandler;
import org.folio.rest.support.clients.ResourceClient;
import org.folio.rest.support.dto.NoticeConfigDto;
import org.folio.rest.support.dto.ScheduledNoticeDto;
import org.folio.rest.support.spring.TestContextConfiguration;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import io.vertx.core.json.JsonObject;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
public class ScheduledNoticesAPITest extends ApiTests {

  private static final RecurringPeriod ONE_DAY_PERIOD = new RecurringPeriod()
    .withDuration(1)
    .withIntervalId(RecurringPeriod.IntervalId.DAYS);

  private static final RecurringPeriod ONE_MONTH_PERIOD = new RecurringPeriod()
    .withDuration(1)
    .withIntervalId(RecurringPeriod.IntervalId.MONTHS);

  @Autowired
  private ResourceClient<ScheduledNoticeDto> scheduledNoticeClient;

  @Before
  public void beforeEach() throws MalformedURLException {
    StorageTestSuite.deleteAll(scheduledNoticesStorageUrl("/scheduled-notices"));
  }

  @Test
  public void canCreateScheduledNotice() throws MalformedURLException,InterruptedException, ExecutionException,
    TimeoutException {

    String templateId = UUID.randomUUID().toString();
    JsonObject noticeConfig = new JsonObject()
      .put("timing", "Upon At")
      .put("templateId", templateId)
      .put("format", "Email");

    DateTime nextRunTime = new DateTime(UTC);
    JsonObject scheduledNotice = new JsonObject()
      .put("nextRunTime", nextRunTime.toString())
      .put("noticeConfig", noticeConfig)
      .put("triggeringEvent", "Request expiration");

    String createdNoticeId = postScheduledNotice(scheduledNotice).getJson().getString("id");

    JsonObject createdNotice = getById(scheduledNoticesStorageUrl("/scheduled-notices/" + createdNoticeId));
    JsonObject createdConfig = createdNotice.getJsonObject("noticeConfig");

    assertThat(createdNotice.getString("triggeringEvent"), is("Request expiration"));
    assertThat(DateTime.parse(createdNotice.getString("nextRunTime")), is(nextRunTime));
    assertThat(createdConfig.getString("timing"), is("Upon At"));
    assertThat(createdConfig.getString("templateId"), is(templateId));
    assertThat(createdConfig.getString("format"), is("Email"));
  }

  @Test
  public void canGetScheduledNoticesCollection() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    JsonObject noticeConfig = new JsonObject()
      .put("timing", "Upon At")
      .put("templateId", UUID.randomUUID().toString())
      .put("format", "Email");
    String nextRunTime = new DateTime(UTC).toString();

    postScheduledNotice(new JsonObject()
      .put("nextRunTime", nextRunTime)
      .put("triggeringEvent", "Request expiration")
      .put("noticeConfig", noticeConfig)
    );

    postScheduledNotice(new JsonObject()
      .put("nextRunTime", nextRunTime)
      .put("triggeringEvent", "Hold expiration")
      .put("noticeConfig", noticeConfig)
    );

    postScheduledNotice(new JsonObject()
      .put("nextRunTime", nextRunTime)
      .put("triggeringEvent", "Due date")
      .put("noticeConfig", noticeConfig)
    );

    postScheduledNotice(new JsonObject()
      .put("nextRunTime", nextRunTime)
      .put("triggeringEvent", "Overdue fine returned")
      .put("noticeConfig", noticeConfig)
    );

    postScheduledNotice(new JsonObject()
      .put("nextRunTime", nextRunTime)
      .put("triggeringEvent", "Overdue fine renewed")
      .put("noticeConfig", noticeConfig)
    );

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl("/scheduled-notices"), TENANT_ID, ResponseHandler.json(getCompleted));
    JsonResponse response = getCompleted.get(5, SECONDS);
    ScheduledNotices scheduledNotices = response.getJson().mapTo(ScheduledNotices.class);

    assertThat(scheduledNotices.getScheduledNotices().size(), is(5));
    assertThat(scheduledNotices.getTotalRecords(), is(5));
  }

  @Test
  public void canGetScheduledNoticesCollectionByQuery() throws MalformedURLException, InterruptedException,
    ExecutionException, TimeoutException {

    String templateId = UUID.randomUUID().toString();

    JsonObject noticeConfig1 = new JsonObject()
      .put("timing", "Upon At")
      .put("templateId", templateId)
      .put("format", "Email");
    JsonObject noticeConfig2 = new JsonObject()
      .put("timing", "Upon At")
      .put("templateId", UUID.randomUUID().toString())
      .put("format", "Email");
    String nextRunTime = new DateTime(UTC).toString();

    postScheduledNotice(new JsonObject()
      .put("nextRunTime", nextRunTime)
      .put("triggeringEvent", "Request expiration")
      .put("noticeConfig", noticeConfig1)
    );

    postScheduledNotice(new JsonObject()
      .put("nextRunTime", nextRunTime)
      .put("triggeringEvent", "Hold expiration")
      .put("noticeConfig", noticeConfig1)
    );

    postScheduledNotice(new JsonObject()
      .put("nextRunTime", nextRunTime)
      .put("triggeringEvent", "Due date")
      .put("noticeConfig", noticeConfig2)
    );

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

    String templateId = UUID.randomUUID().toString();
    JsonObject noticeConfig = new JsonObject()
      .put("timing", "Upon At")
      .put("templateId", templateId)
      .put("format", "Email");

    DateTime nextRunTime = new DateTime(UTC);
    JsonObject scheduledNotice = new JsonObject()
      .put("nextRunTime", nextRunTime.toString())
      .put("noticeConfig", noticeConfig)
      .put("triggeringEvent", "Request expiration");

    String createdNoticeId = postScheduledNotice(scheduledNotice).getJson().getString("id");

    JsonObject receivedNotice = getById(scheduledNoticesStorageUrl("/scheduled-notices/" + createdNoticeId));
    JsonObject receivedNoticeConfig = receivedNotice.getJsonObject("noticeConfig");

    assertThat(DateTime.parse(receivedNotice.getString("nextRunTime")), is(nextRunTime));
    assertThat(receivedNotice.getString("triggeringEvent"), is("Request expiration"));
    assertThat(receivedNoticeConfig.getString("timing"), is("Upon At"));
    assertThat(receivedNoticeConfig.getString("templateId"), is(templateId));
    assertThat(receivedNoticeConfig.getString("format"), is("Email"));
  }

  @Test
  public void canUpdateScheduledNoticeById() throws MalformedURLException, InterruptedException,ExecutionException,
    TimeoutException {

    String noticeId = createScheduledNotice(org.folio.rest.jaxrs.model.NoticeConfig.Timing.BEFORE, ONE_DAY_PERIOD,
      UUID.randomUUID().toString(), EMAIL).getId();

    RecurringPeriod period = new RecurringPeriod()
      .withDuration(1)
      .withIntervalId(RecurringPeriod.IntervalId.DAYS);

    org.folio.rest.jaxrs.model.NoticeConfig newConfig = new org.folio.rest.jaxrs.model.NoticeConfig()
      .withTiming(org.folio.rest.jaxrs.model.NoticeConfig.Timing.AFTER)
      .withRecurringPeriod(period)
      .withTemplateId(UUID.randomUUID().toString())
      .withFormat(org.folio.rest.jaxrs.model.NoticeConfig.Format.SMS);

    org.folio.rest.jaxrs.model.ScheduledNotice newNotice = new org.folio.rest.jaxrs.model.ScheduledNotice()
      .withNextRunTime(new Date())
      .withNoticeConfig(newConfig);

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();
    client.put(scheduledNoticesStorageUrl("/scheduled-notices/" + noticeId), JsonObject.mapFrom(newNotice),
      TENANT_ID, ResponseHandler.empty(putCompleted));
    putCompleted.get(5, SECONDS);

    org.folio.rest.jaxrs.model.ScheduledNotice updatedNotice = getById(scheduledNoticesStorageUrl("/scheduled-notices/" + noticeId)).mapTo(org.folio.rest.jaxrs.model.ScheduledNotice.class);
    org.folio.rest.jaxrs.model.NoticeConfig updatedConfig = updatedNotice.getNoticeConfig();

    assertThat(updatedNotice.getNextRunTime(), is(newNotice.getNextRunTime()));
    assertThat(updatedConfig.getTiming(), is(newConfig.getTiming()));
    assertThat(updatedConfig.getTemplateId(), is(newConfig.getTemplateId()));
    assertThat(updatedConfig.getFormat(), is(newConfig.getFormat()));
  }

  @Test
  public void canDeleteScheduledNoticeById() throws InterruptedException, MalformedURLException, TimeoutException,
    ExecutionException {

    String noticeId = createScheduledNotice(org.folio.rest.jaxrs.model.NoticeConfig.Timing.BEFORE, ONE_DAY_PERIOD,
      UUID.randomUUID().toString(), EMAIL).getId();

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

    createScheduledNotice(org.folio.rest.jaxrs.model.NoticeConfig.Timing.BEFORE, ONE_DAY_PERIOD,
      UUID.randomUUID().toString(), EMAIL);

    createScheduledNotice(org.folio.rest.jaxrs.model.NoticeConfig.Timing.AFTER, ONE_MONTH_PERIOD,
      UUID.randomUUID().toString(), org.folio.rest.jaxrs.model.NoticeConfig.Format.SMS);

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

    String templateId = UUID.randomUUID().toString();

    createScheduledNotice(org.folio.rest.jaxrs.model.NoticeConfig.Timing.BEFORE, ONE_DAY_PERIOD,
      UUID.randomUUID().toString(), EMAIL);

    createScheduledNotice(org.folio.rest.jaxrs.model.NoticeConfig.Timing.AFTER, ONE_DAY_PERIOD,
      templateId, org.folio.rest.jaxrs.model.NoticeConfig.Format.SMS);

    createScheduledNotice(UPON_AT, ONE_MONTH_PERIOD,
      templateId, org.folio.rest.jaxrs.model.NoticeConfig.Format.SMS);

    String query = "query=noticeConfig.templateId=" + templateId;

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();
    client.delete(scheduledNoticesStorageUrl("/scheduled-notices?" + query), TENANT_ID, ResponseHandler.empty(deleteCompleted));
    deleteCompleted.get(5, SECONDS);

    CompletableFuture<JsonResponse> getByQueryCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl("/scheduled-notices?" + query), TENANT_ID, ResponseHandler.json(getByQueryCompleted));

    CompletableFuture<JsonResponse> getAllCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl("/scheduled-notices"), TENANT_ID, ResponseHandler.json(getAllCompleted));

    ScheduledNotices scheduledNotices = getByQueryCompleted.get(5, SECONDS)
      .getJson()
      .mapTo(ScheduledNotices.class);

    ScheduledNotices allScheduledNotices = getAllCompleted.get(5, SECONDS)
      .getJson()
      .mapTo(ScheduledNotices.class);

    assertThat(scheduledNotices.getScheduledNotices().size(), is(0));
    assertThat(scheduledNotices.getTotalRecords(), is(0));
    assertThat(allScheduledNotices.getTotalRecords(), is(1));
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
  public void canFilterNoticesByNextRunTime() {
    final Date firstNextRunTime = DateTime.parse("2020-06-16T08:50:54.306+0000").toDate();
    final Date secondNextRunTime = DateTime.parse("2020-06-19T08:50:54.306+0000").toDate();

    final ScheduledNoticeDto scheduledNoticeToRunOn16th = holdExpirationEmailNotice()
      .nextRunTime(firstNextRunTime).build();

    final ScheduledNoticeDto scheduledNoticeToRunOn19th = holdExpirationEmailNotice()
      .nextRunTime(secondNextRunTime).build();

    scheduledNoticeClient.create(scheduledNoticeToRunOn16th);
    scheduledNoticeClient.create(scheduledNoticeToRunOn19th);

    final List<ScheduledNoticeDto> filteredNotices = scheduledNoticeClient
      .getMany(lessThen("nextRunTime", "2020-06-17"));

    assertThat(filteredNotices, hasSize(1));
    assertThat(filteredNotices, hasItem(scheduledNoticeToRunOn16th));
  }

  @Test
  public void canFilterNoticesByConfigSendInRealTime() {
    final NoticeConfigDto sendInRealTimeConfig = emailUponAtConfig()
      .sendInRealTime(true).build();
    final NoticeConfigDto doNotSendInRealTimeConfig = emailUponAtConfig()
      .sendInRealTime(false).build();

    final ScheduledNoticeDto sendInRealTimeNotice = holdExpirationEmailNotice()
      .noticeConfig(sendInRealTimeConfig).build();
    final ScheduledNoticeDto doNotSendInRealTimeNotice = holdExpirationEmailNotice()
      .noticeConfig(doNotSendInRealTimeConfig).build();

    scheduledNoticeClient.create(sendInRealTimeNotice);
    scheduledNoticeClient.create(doNotSendInRealTimeNotice);

    final List<ScheduledNoticeDto> sendInRealTimeNotices = scheduledNoticeClient
      .getMany(exactMatch("noticeConfig.sendInRealTime", true));

    assertThat(sendInRealTimeNotices, hasSize(1));
    assertThat(sendInRealTimeNotices, hasItem(sendInRealTimeNotice));
  }

  @Test
  public void canFilterNoticesByTriggeringEvent() {
    final ScheduledNoticeDto holdExpirationNotice = holdExpirationEmailNotice().build();
    final ScheduledNoticeDto requestExpirationNotice = holdExpirationEmailNotice()
      .triggeringEvent("Request expiration").build();

    scheduledNoticeClient.create(holdExpirationNotice);
    scheduledNoticeClient.create(requestExpirationNotice);

    final List<ScheduledNoticeDto> holdExpirationNotices = scheduledNoticeClient
      .getMany(exactMatch("triggeringEvent", "Hold expiration"));

    assertThat(holdExpirationNotices, hasSize(1));
    assertThat(holdExpirationNotices, hasItem(holdExpirationNotice));
  }

  @Test
  public void canFilterNoticesByRequestId() {
    final String holdRequestId = UUID.randomUUID().toString();
    final String recallRequestId = UUID.randomUUID().toString();

    final ScheduledNoticeDto holdRequestNotice = holdExpirationEmailNotice()
      .requestId(holdRequestId).build();
    final ScheduledNoticeDto recallRequestNotice = holdExpirationEmailNotice()
      .requestId(recallRequestId).build();

    scheduledNoticeClient.create(holdRequestNotice);
    scheduledNoticeClient.create(recallRequestNotice);

    final List<ScheduledNoticeDto> holdRequestNotices = scheduledNoticeClient
      .getMany(exactMatch("requestId", holdRequestId));

    assertThat(holdRequestNotices, hasSize(1));
    assertThat(holdRequestNotices, hasItem(holdRequestNotice));
  }

  private NoticeConfigDto.NoticeConfigDtoBuilder emailUponAtConfig() {
    return NoticeConfigDto.builder()
      .timing("Upon At")
      .templateId(UUID.randomUUID().toString())
      .format("Email");
  }

  private ScheduledNoticeDto.ScheduledNoticeDtoBuilder holdExpirationEmailNotice() {
    return ScheduledNoticeDto.builder()
      .nextRunTime(DateTime.now(UTC).toDate())
      .triggeringEvent("Hold expiration")
      .noticeConfig(emailUponAtConfig().build());
  }

  private JsonResponse postScheduledNotice(JsonObject entity) throws MalformedURLException,
    InterruptedException, ExecutionException, TimeoutException {

    CompletableFuture<JsonResponse> postCompleted = new CompletableFuture<>();

    client.post(scheduledNoticesStorageUrl("/scheduled-notices"), entity, TENANT_ID,
      ResponseHandler.json(postCompleted));

    return postCompleted.get(5, TimeUnit.SECONDS);
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

  private static URL scheduledNoticesStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/scheduled-notice-storage" + subPath);
  }
}
