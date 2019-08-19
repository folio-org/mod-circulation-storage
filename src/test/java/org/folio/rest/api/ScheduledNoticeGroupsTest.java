package org.folio.rest.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.NoticeConfig;
import org.folio.rest.jaxrs.model.NoticeGroup;
import org.folio.rest.jaxrs.model.ScheduledNotice;
import org.folio.rest.jaxrs.model.ScheduledNoticeGroups;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class ScheduledNoticeGroupsTest extends ApiTests {

  private static final String SCHEDULED_NOTICE_GROUPS_PATH = "/scheduled-notice-groups";

  @Before
  public void beforeEach() throws MalformedURLException {
    StorageTestSuite.deleteAll(scheduledNoticesStorageUrl("/scheduled-notices"));
  }

  @Test
  public void canGetScheduledNoticeGroupedByUserId() throws Exception {
    String userId1 = UUID.randomUUID().toString();
    String userId2 = UUID.randomUUID().toString();
    String templateId = UUID.randomUUID().toString();
    NoticeGroup.TriggeringEvent triggeringEvent = NoticeGroup.TriggeringEvent.DUE_DATE;
    NoticeGroup.Timing timing = NoticeGroup.Timing.UPON_AT;
    NoticeGroup.Format format = NoticeGroup.Format.EMAIL;
    int numberOfCopies = 5;
    createScheduledNotice(new ScheduledNotice()
      .withUserId(userId1)
      .withTriggeringEvent(triggeringEvent)
      .withNextRunTime(new Date())
      .withNoticeConfig(new NoticeConfig()
        .withTemplateId(templateId)
        .withFormat(format)
        .withTiming(timing)), numberOfCopies);
    createScheduledNotice(new ScheduledNotice()
      .withUserId(userId2)
      .withTriggeringEvent(triggeringEvent)
      .withNextRunTime(new Date())
      .withNoticeConfig(new NoticeConfig()
        .withTemplateId(templateId)
        .withFormat(format)
        .withTiming(timing)), numberOfCopies);

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl(SCHEDULED_NOTICE_GROUPS_PATH), TENANT_ID, ResponseHandler.json(getCompleted));
    JsonResponse response = getCompleted.get(5, SECONDS);
    ScheduledNoticeGroups scheduledNoticeGroups = response.getJson().mapTo(ScheduledNoticeGroups.class);

    List<NoticeGroup> noticeGroups = scheduledNoticeGroups.getNoticeGroups();
    assertThat(noticeGroups, hasSize(2));

    List<String> userIds = noticeGroups.stream()
      .map(NoticeGroup::getUserId).collect(Collectors.toList());
    assertThat(userIds, containsInAnyOrder(userId1, userId2));

    assertTrue(noticeGroups.stream()
      .map(NoticeGroup::getScheduledNotices)
      .map(List::size).allMatch(size -> size.equals(numberOfCopies)));
  }

  @Test
  public void canGetScheduledNoticeGroupedByTemplateId() throws Exception {
    String userId = UUID.randomUUID().toString();
    String templateId1 = UUID.randomUUID().toString();
    String templateId2 = UUID.randomUUID().toString();
    NoticeGroup.TriggeringEvent triggeringEvent = NoticeGroup.TriggeringEvent.DUE_DATE;
    NoticeGroup.Timing timing = NoticeGroup.Timing.UPON_AT;
    NoticeGroup.Format format = NoticeGroup.Format.EMAIL;
    int numberOfCopies = 5;
    createScheduledNotice(new ScheduledNotice()
      .withUserId(userId)
      .withTriggeringEvent(triggeringEvent)
      .withNextRunTime(new Date())
      .withNoticeConfig(new NoticeConfig()
        .withTemplateId(templateId1)
        .withFormat(format)
        .withTiming(timing)), numberOfCopies);
    createScheduledNotice(new ScheduledNotice()
      .withUserId(userId)
      .withTriggeringEvent(triggeringEvent)
      .withNextRunTime(new Date())
      .withNoticeConfig(new NoticeConfig()
        .withTemplateId(templateId2)
        .withFormat(format)
        .withTiming(timing)), numberOfCopies);

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl(SCHEDULED_NOTICE_GROUPS_PATH), TENANT_ID, ResponseHandler.json(getCompleted));
    JsonResponse response = getCompleted.get(5, SECONDS);
    ScheduledNoticeGroups scheduledNoticeGroups = response.getJson().mapTo(ScheduledNoticeGroups.class);

    List<NoticeGroup> noticeGroups = scheduledNoticeGroups.getNoticeGroups();
    assertThat(noticeGroups, hasSize(2));

    List<String> templateIds = noticeGroups.stream()
      .map(NoticeGroup::getTemplateId).collect(Collectors.toList());
    assertThat(templateIds, containsInAnyOrder(templateId1, templateId2));

    assertTrue(noticeGroups.stream()
      .map(NoticeGroup::getScheduledNotices)
      .map(List::size).allMatch(size -> size.equals(numberOfCopies)));
  }

  @Test
  public void canGetScheduledNoticeGroupedByTriggeringEvent() throws Exception {
    String userId = UUID.randomUUID().toString();
    String templateId = UUID.randomUUID().toString();
    NoticeGroup.TriggeringEvent triggeringEvent1 = NoticeGroup.TriggeringEvent.DUE_DATE;
    NoticeGroup.TriggeringEvent triggeringEvent2 = NoticeGroup.TriggeringEvent.REQUEST_EXPIRATION;
    NoticeGroup.Timing timing = NoticeGroup.Timing.UPON_AT;
    NoticeGroup.Format format = NoticeGroup.Format.EMAIL;
    int numberOfCopies = 5;
    createScheduledNotice(new ScheduledNotice()
      .withUserId(userId)
      .withTriggeringEvent(triggeringEvent1)
      .withNextRunTime(new Date())
      .withNoticeConfig(new NoticeConfig()
        .withTemplateId(templateId)
        .withFormat(format)
        .withTiming(timing)), numberOfCopies);
    createScheduledNotice(new ScheduledNotice()
      .withUserId(userId)
      .withTriggeringEvent(triggeringEvent2)
      .withNextRunTime(new Date())
      .withNoticeConfig(new NoticeConfig()
        .withTemplateId(templateId)
        .withFormat(format)
        .withTiming(timing)), numberOfCopies);

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl(SCHEDULED_NOTICE_GROUPS_PATH), TENANT_ID, ResponseHandler.json(getCompleted));
    JsonResponse response = getCompleted.get(5, SECONDS);
    ScheduledNoticeGroups scheduledNoticeGroups = response.getJson().mapTo(ScheduledNoticeGroups.class);

    List<NoticeGroup> noticeGroups = scheduledNoticeGroups.getNoticeGroups();
    assertThat(noticeGroups, hasSize(2));

    List<NoticeGroup.TriggeringEvent> triggeringEvents = noticeGroups.stream()
      .map(NoticeGroup::getTriggeringEvent).collect(Collectors.toList());
    assertThat(triggeringEvents, containsInAnyOrder(triggeringEvent1, triggeringEvent2));

    assertTrue(noticeGroups.stream()
      .map(NoticeGroup::getScheduledNotices)
      .map(List::size).allMatch(size -> size.equals(numberOfCopies)));
  }

  @Test
  public void canGetScheduledNoticeGroupedByTiming() throws Exception {
    String userId = UUID.randomUUID().toString();
    String templateId = UUID.randomUUID().toString();
    NoticeGroup.TriggeringEvent triggeringEvent = NoticeGroup.TriggeringEvent.DUE_DATE;
    NoticeGroup.Timing timing1 = NoticeGroup.Timing.BEFORE;
    NoticeGroup.Timing timing2 = NoticeGroup.Timing.AFTER;
    NoticeGroup.Format format = NoticeGroup.Format.EMAIL;
    int numberOfCopies = 5;
    createScheduledNotice(new ScheduledNotice()
      .withUserId(userId)
      .withTriggeringEvent(triggeringEvent)
      .withNextRunTime(new Date())
      .withNoticeConfig(new NoticeConfig()
        .withTemplateId(templateId)
        .withFormat(format)
        .withTiming(timing1)), numberOfCopies);
    createScheduledNotice(new ScheduledNotice()
      .withUserId(userId)
      .withTriggeringEvent(triggeringEvent)
      .withNextRunTime(new Date())
      .withNoticeConfig(new NoticeConfig()
        .withTemplateId(templateId)
        .withFormat(format)
        .withTiming(timing2)), numberOfCopies);

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl(SCHEDULED_NOTICE_GROUPS_PATH), TENANT_ID, ResponseHandler.json(getCompleted));
    JsonResponse response = getCompleted.get(5, SECONDS);
    ScheduledNoticeGroups scheduledNoticeGroups = response.getJson().mapTo(ScheduledNoticeGroups.class);

    List<NoticeGroup> noticeGroups = scheduledNoticeGroups.getNoticeGroups();
    assertThat(noticeGroups, hasSize(2));

    List<NoticeGroup.Timing> timings = noticeGroups.stream()
      .map(NoticeGroup::getTiming).collect(Collectors.toList());
    assertThat(timings, containsInAnyOrder(timing1, timing2));

    assertTrue(noticeGroups.stream()
      .map(NoticeGroup::getScheduledNotices)
      .map(List::size).allMatch(size -> size.equals(numberOfCopies)));
  }


  @Test
  public void canGetScheduledNoticeGroupedByFormat() throws Exception {
    String userId = UUID.randomUUID().toString();
    String templateId = UUID.randomUUID().toString();
    NoticeGroup.TriggeringEvent triggeringEvent = NoticeGroup.TriggeringEvent.DUE_DATE;
    NoticeGroup.Timing timing = NoticeGroup.Timing.BEFORE;
    NoticeGroup.Format format1 = NoticeGroup.Format.EMAIL;
    NoticeGroup.Format format2 = NoticeGroup.Format.SMS;
    int numberOfCopies = 5;
    createScheduledNotice(new ScheduledNotice()
      .withUserId(userId)
      .withTriggeringEvent(triggeringEvent)
      .withNextRunTime(new Date())
      .withNoticeConfig(new NoticeConfig()
        .withTemplateId(templateId)
        .withFormat(format1)
        .withTiming(timing)), numberOfCopies);
    createScheduledNotice(new ScheduledNotice()
      .withUserId(userId)
      .withTriggeringEvent(triggeringEvent)
      .withNextRunTime(new Date())
      .withNoticeConfig(new NoticeConfig()
        .withTemplateId(templateId)
        .withFormat(format2)
        .withTiming(timing)), numberOfCopies);

    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl(SCHEDULED_NOTICE_GROUPS_PATH), TENANT_ID, ResponseHandler.json(getCompleted));
    JsonResponse response = getCompleted.get(5, SECONDS);
    ScheduledNoticeGroups scheduledNoticeGroups = response.getJson().mapTo(ScheduledNoticeGroups.class);

    List<NoticeGroup> noticeGroups = scheduledNoticeGroups.getNoticeGroups();
    assertThat(noticeGroups, hasSize(2));

    List<NoticeGroup.Format> formats = noticeGroups.stream()
      .map(NoticeGroup::getFormat).collect(Collectors.toList());
    assertThat(formats, containsInAnyOrder(format1, format2));

    assertTrue(noticeGroups.stream()
      .map(NoticeGroup::getScheduledNotices)
      .map(List::size).allMatch(size -> size.equals(numberOfCopies)));
  }

  @Test
  public void canGetScheduledNoticeGroupsFilteredByCql() throws Exception {
    String userId = UUID.randomUUID().toString();
    String templateId = UUID.randomUUID().toString();
    NoticeGroup.TriggeringEvent triggeringEvent = NoticeGroup.TriggeringEvent.DUE_DATE;
    NoticeGroup.Timing timing = NoticeGroup.Timing.UPON_AT;
    NoticeGroup.Format format = NoticeGroup.Format.EMAIL;

    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    ZonedDateTime inPast = now.minusDays(2);
    ZonedDateTime inFuture = now.plusDays(2);

    int numberOfCopies = 5;
    createScheduledNotice(new ScheduledNotice()
      .withUserId(userId)
      .withTriggeringEvent(triggeringEvent)
      .withNextRunTime(Date.from(inPast.toInstant()))
      .withNoticeConfig(new NoticeConfig()
        .withTemplateId(templateId)
        .withFormat(format)
        .withTiming(timing)), numberOfCopies);
    createScheduledNotice(new ScheduledNotice()
      .withUserId(userId)
      .withTriggeringEvent(triggeringEvent)
      .withNextRunTime(Date.from(inFuture.toInstant()))
      .withNoticeConfig(new NoticeConfig()
        .withTemplateId(templateId)
        .withFormat(format)
        .withTiming(timing)), numberOfCopies);

    String cql = String.format("query=nextRunTime<\"%s\"", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now));
    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(scheduledNoticesStorageUrl(SCHEDULED_NOTICE_GROUPS_PATH), cql, TENANT_ID, ResponseHandler.json(getCompleted));
    JsonResponse response = getCompleted.get(5, SECONDS);
    ScheduledNoticeGroups scheduledNoticeGroups = response.getJson().mapTo(ScheduledNoticeGroups.class);

    List<NoticeGroup> noticeGroups = scheduledNoticeGroups.getNoticeGroups();
    assertThat(noticeGroups, hasSize(1));
  }


  private void createScheduledNotice(ScheduledNotice scheduledNotice, int times)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {

    for (int i = 0; i < times; i++) {
      createEntity(JsonObject.mapFrom(scheduledNotice), scheduledNoticesStorageUrl("/scheduled-notices"))
        .getJson()
        .mapTo(ScheduledNotice.class);
    }
  }

  private static URL scheduledNoticesStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/scheduled-notice-storage" + subPath);
  }
}
