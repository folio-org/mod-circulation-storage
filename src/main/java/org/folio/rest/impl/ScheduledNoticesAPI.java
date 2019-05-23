package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

import org.folio.rest.jaxrs.model.ScheduledNotice;
import org.folio.rest.jaxrs.model.ScheduledNotices;
import org.folio.rest.jaxrs.resource.ScheduledNoticeStorage;
import org.folio.rest.persist.PgUtil;

public class ScheduledNoticesAPI implements ScheduledNoticeStorage {

  private static final String SCHEDULED_NOTICE_TABLE = "scheduled_notice";

  @Override
  public void getScheduledNoticeStorageScheduledNotices(int offset,
                                                        int limit,
                                                        String query,
                                                        String lang,
                                                        Map<String, String> okapiHeaders,
                                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                                        Context vertxContext) {

    PgUtil.get(SCHEDULED_NOTICE_TABLE, ScheduledNotice.class, ScheduledNotices.class, query, offset, limit,
      okapiHeaders, vertxContext, GetScheduledNoticeStorageScheduledNoticesResponse.class, asyncResultHandler);
  }

  @Override
  public void postScheduledNoticeStorageScheduledNotices(String lang,
                                                         ScheduledNotice entity,
                                                         Map<String, String> okapiHeaders,
                                                         Handler<AsyncResult<Response>> asyncResultHandler,
                                                         Context vertxContext) {

    PgUtil.post(SCHEDULED_NOTICE_TABLE, entity, okapiHeaders, vertxContext,
      PostScheduledNoticeStorageScheduledNoticesResponse.class, asyncResultHandler);
  }

  @Override
  public void getScheduledNoticeStorageScheduledNoticesByScheduledNoticeId(String scheduledNoticeId,
                                                                           String lang,
                                                                           Map<String, String> okapiHeaders,
                                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                                           Context vertxContext) {

    PgUtil.getById(SCHEDULED_NOTICE_TABLE, ScheduledNotice.class, scheduledNoticeId, okapiHeaders, vertxContext,
      GetScheduledNoticeStorageScheduledNoticesByScheduledNoticeIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteScheduledNoticeStorageScheduledNoticesByScheduledNoticeId(String scheduledNoticeId,
                                                                              String lang, Map<String, String> okapiHeaders,
                                                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                                                              Context vertxContext) {

    PgUtil.deleteById(SCHEDULED_NOTICE_TABLE, scheduledNoticeId, okapiHeaders, vertxContext,
      DeleteScheduledNoticeStorageScheduledNoticesByScheduledNoticeIdResponse.class, asyncResultHandler);

  }

  @Override
  public void putScheduledNoticeStorageScheduledNoticesByScheduledNoticeId(String scheduledNoticeId,
                                                                           String lang,
                                                                           ScheduledNotice entity,
                                                                           Map<String, String> okapiHeaders,
                                                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                                                           Context vertxContext) {

    PgUtil.put(SCHEDULED_NOTICE_TABLE, entity, scheduledNoticeId, okapiHeaders, vertxContext,
      PutScheduledNoticeStorageScheduledNoticesByScheduledNoticeIdResponse.class, asyncResultHandler);

  }
}
