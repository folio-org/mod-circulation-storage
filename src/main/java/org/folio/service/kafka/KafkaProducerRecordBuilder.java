package org.folio.service.kafka;

import static io.vertx.kafka.client.producer.KafkaProducerRecord.create;

import static org.folio.dbschema.ObjectMapperTool.getMapper;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.URL;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

import org.folio.service.kafka.topic.KafkaTopic;

public final class KafkaProducerRecordBuilder<K, V> {

  private static final Set<String> FORWARDER_HEADERS = Set.of(URL.toLowerCase(),
      TENANT.toLowerCase());

  private V value;
  private K key;
  private String topic;
  private final Map<String, String> headers = new HashMap<>();


  public KafkaProducerRecordBuilder<K, V> value(V value) {
    this.value = value;
    return this;
  }

  public KafkaProducerRecordBuilder<K, V> key(K key) {
    this.key = key;
    return this;
  }

  public KafkaProducerRecordBuilder<K, V> topic(KafkaTopic topic) {
    this.topic = topic.getQualifiedName();
    return this;
  }

  public KafkaProducerRecordBuilder<K, V> header(String key, String value) {
    this.headers.put(key, value);
    return this;
  }

  public KafkaProducerRecordBuilder<K, V> propagateOkapiHeaders(Map<String, String> okapiHeaders) {
    okapiHeaders.entrySet().stream()
        .filter(entry -> FORWARDER_HEADERS.contains(entry.getKey().toLowerCase()))
        .forEach(entry -> header(entry.getKey(), entry.getValue()));

    return this;
  }

  public KafkaProducerRecord<K, String> build() {
    try {
      var valueAsString = getMapper().writeValueAsString(this.value);

      var result = create(topic, key, valueAsString);
      headers.forEach(result::addHeader);

      return result;
    } catch (JsonProcessingException ex) {
      throw new RuntimeException(ex);
    }
  }
  
}
