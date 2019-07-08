package org.folio.rest.persist;

import java.lang.reflect.Method;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.resource.support.ResponseDelegate;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * Temporary only.
 *
 * @deprecated Remove, or move into RMB domain-models-runtime PgUtil.
 */
@Deprecated
public final class MyPgUtil {
  private static final Logger logger = LoggerFactory.getLogger(PgUtil.class);

  private static final String RESPOND_204                       = "respond204";
  private static final String RESPOND_400_WITH_TEXT_PLAIN       = "respond400WithTextPlain";
  private static final String RESPOND_500_WITH_TEXT_PLAIN       = "respond500WithTextPlain";

  private MyPgUtil() {
    throw new UnsupportedOperationException("Cannot instantiate utility class.");
  }

  /**
   * Insert or update entity to table and use 204 on success (both insert and update).
   *
   * <p>TODO: Using 204 on insert is wrong, only 201 is correct,
   * see https://tools.ietf.org/html/rfc2616#section-9.6
   *
   * <p>TODO: Remove or move into RMB domain-models-runtime PgUtil.
   *
   * <p>This method is intended to not make a breaking change. Don't use for new APIs!
   *
   * <p>All exceptions are caught and reported via the asyncResultHandler.
   *
   * @param table  table name
   * @param entity  the entity to post. If the id field is missing or null it is set to a random UUID.
   * @param id  UUID for primary key id and for URI
   * @param okapiHeaders  http headers provided by okapi
   * @param vertxContext  the current context
   * @param clazz  the ResponseDelegate class generated as defined by the RAML file, must have these methods:
   *               respond204(), respond400WithTextPlain(Object), respond500WithTextPlain(Object).
   * @param asyncResultHandler  where to return the result created by clazz
   */
  @SuppressWarnings("squid:S1523")  // suppress "Dynamically executing code is security-sensitive"
                                    // we use only hard-coded names
  public static <T> void putUpsert204(String table, T entity, String id,
      Map<String, String> okapiHeaders, Context vertxContext,
      Class<? extends ResponseDelegate> clazz,
      Handler<AsyncResult<Response>> asyncResultHandler) {

    final Method respond500;

    try {
      respond500 = clazz.getMethod(RESPOND_500_WITH_TEXT_PLAIN, Object.class);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(Future.failedFuture(e));
      return;
    }

    try {
      Method respond204 = clazz.getMethod(RESPOND_204);
      Method respond400 = clazz.getMethod(RESPOND_400_WITH_TEXT_PLAIN, Object.class);
      PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);

      postgresClient.upsert(table, id, entity, reply -> {
        if (reply.failed()) {
          asyncResultHandler.handle(PgUtil.response(reply.cause(), respond400, respond500));
          return;
        }
        // TODO: 204 is correct for update only, for insert 201 must be used
        asyncResultHandler.handle(PgUtil.response(respond204, respond500));
      });
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      asyncResultHandler.handle(PgUtil.response(e.getMessage(), respond500, respond500));
    }
  }

}
