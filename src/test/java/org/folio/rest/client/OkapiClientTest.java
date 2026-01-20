package org.folio.rest.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.instanceOf;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class OkapiClientTest {

  @Test
  void noOkapiUrl(VertxTestContext context) {
    new OkapiClient(Vertx.vertx(), Map.of())
      .get("/foo", "cql.allRecords=1", Object.class, "collectionName", 10)
      .onSuccess(result -> context.failNow(new AssertionError("Expected failure but succeeded")))
      .onFailure(cause -> context.verify(() -> {
        assertThat(cause.getCause(), is(instanceOf(MalformedURLException.class)));
        context.completeNow();
      }));
  }

  @Test
  void unknownHost(VertxTestContext context) {
    new OkapiClient(Vertx.vertx(), Map.of("x-okapi-url", "http://www.invalid"))
      .get("/foo", List.of("1"), "foo", Object.class)
      .onSuccess(result -> context.failNow(new AssertionError("Expected failure but succeeded")))
      .onFailure(cause -> context.verify(() -> {
        assertThat(cause.getClass().getName(), containsString("UnknownHost"));
        context.completeNow();
      }));
  }

}
