package org.folio.rest.api;

import io.vertx.core.json.JsonObject;
import org.folio.rest.support.ApiTests;
import org.junit.jupiter.api.Test;

import static org.folio.support.JsonPropertyWriter.write;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JsonPropertyWriterTest extends ApiTests {

  @Test
  void writeExistingValue() {
    JsonObject jsonObject = new JsonObject();
    write(jsonObject, "key", "value");
    assertThat(jsonObject.size(), is(1));
  }

  @Test
  void writeNullValue() {
    JsonObject jsonObject = new JsonObject();
    write(jsonObject, "key", (String) null);
    assertThat(jsonObject.size(), is(0));
  }
}
