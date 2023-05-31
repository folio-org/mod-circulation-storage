package org.folio.service.event.handler;

import static org.apache.commons.lang3.ObjectUtils.notEqual;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.persist.RequestRepository;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ServicePointUpdateEventHandler extends UpdateEventAbstractHandler<Request> {
  private static final Logger log = LogManager.getLogger(ItemUpdateEventHandler.class);
  private static final String SERVICE_POINT_NAME_KEY = "name";

  private final Context context;

  @Override
  protected List<Change<Request>> collectRelevantChanges(JsonObject oldObject, JsonObject newObject) {
    List<Change<Request>> changes = new ArrayList<>();

    // compare service point names
    String oldServicePointName = oldObject.getString(SERVICE_POINT_NAME_KEY);
    String newServicePointName = newObject.getString(SERVICE_POINT_NAME_KEY);
    if (notEqual(oldServicePointName, newServicePointName)) {
      log.info("collectRelevantChanges:: changing searchIndex.pickupServicePointName from {} to {}",
        oldServicePointName, newServicePointName);
      changes.add(new Change<>(request -> request.getSearchIndex()
        .setPickupServicePointName(newServicePointName)));
    }

    return changes;
  }

  @Override
  protected Future<List<Request>> applyChanges(List<Change<Request>> changes,
    KafkaConsumerRecord<String, String> event, JsonObject oldObject, JsonObject newObject) {

    log.debug("applyChanges:: applying searchIndex.pickupServicePointName changes");

    RequestRepository requestRepository = new RequestRepository(context, getKafkaHeaders(event));

    return findRequestsByPickupServicePointId(requestRepository, oldObject.getString("id"))
      .compose(requests -> applyDbUpdates(requests, changes, requestRepository));
  }

  private Future<List<Request>> findRequestsByPickupServicePointId(
    RequestRepository requestRepository, String pickupServicePointId) {

    log.info("findRequestsByServicePointId:: fetching requests for pickupServicePointId {}",
      pickupServicePointId);

    return requestRepository.get(new Criterion(
      new Criteria()
        .addField("'pickupServicePointId'")
        .setOperation("=")
        .setVal(pickupServicePointId)));
  }
}
