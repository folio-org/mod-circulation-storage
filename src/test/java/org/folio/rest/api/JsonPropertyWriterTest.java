package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import org.folio.rest.support.ApiTests;
import org.junit.Test;

import static org.folio.support.JsonPropertyWriter.write;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class JsonPropertyWriterTest extends ApiTests {
  @Test
  public void writeExistingValue() {
    JsonObject jsonObject = new JsonObject();
    write(jsonObject, "key", "value");
    assertThat(jsonObject.size(), is(1));
  }

  @Test
  public void writeNullValue() {
    JsonObject jsonObject = new JsonObject();
    write(jsonObject, "key", null);
    assertThat(jsonObject.size(), is(0));
  }
}
