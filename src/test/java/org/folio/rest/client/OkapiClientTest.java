package org.folio.rest.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.instanceOf;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class OkapiClientTest {

  @Test
  public void noOkapiUrl(TestContext context) {
    new OkapiClient(Vertx.vertx(), Map.of())
    .get("/foo", "cql.allRecords=1", 10)
    .onComplete(context.asyncAssertFailure(e -> {
      assertThat(e.getCause(), is(instanceOf(MalformedURLException.class)));
    }));
  }

  @Test
  public void unknownHost(TestContext context) {
    new OkapiClient(Vertx.vertx(), Map.of("x-okapi-url", "http://www.invalid"))
    .get("/foo", List.of("1"), "foo", Object.class)
    .onComplete(context.asyncAssertFailure(e -> {
      assertThat(e.getClass().getName(), containsString("UnknownHost"));
    }));
  }

}
