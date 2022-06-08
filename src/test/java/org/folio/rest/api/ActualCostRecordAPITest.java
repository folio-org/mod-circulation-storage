package org.folio.rest.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.folio.rest.jaxrs.model.ActualCostRecord;
import org.folio.rest.jaxrs.model.Identifier;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.IndividualResource;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.folio.rest.support.spring.TestContextConfiguration;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;
import static org.folio.rest.jaxrs.model.ActualCostRecord.LossType.AGED_TO_LOST;
import static org.folio.rest.jaxrs.model.ActualCostRecord.LossType.DECLARED_LOST;
import static org.folio.rest.support.matchers.JsonMatchers.hasSameProperties;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsIterableContaining.hasItems;

@ContextConfiguration(classes = TestContextConfiguration.class)
public class ActualCostRecordAPITest extends ApiTests {
  private static final String ACTUAL_COST_RECORD_TABLE = "actual_cost_record";

  @ClassRule
  public static final SpringClassRule classRule = new SpringClassRule();
  @Rule
  public final SpringMethodRule methodRule = new SpringMethodRule();

  @Autowired
  private ObjectMapper objectMapper;

  private final AssertingRecordClient actualCostRecordClient =
    new AssertingRecordClient(client, StorageTestSuite.TENANT_ID,
      InterfaceUrls::actualCostRecord, "actualCostRecords");

  @Before
  public void beforeEach() throws Exception {
    StorageTestSuite.cleanUpTable(ACTUAL_COST_RECORD_TABLE);
  }

  @Test
  @SneakyThrows
  public void canCreateAndGetAndDeleteActualCostRecords() {
    ActualCostRecord actualCostRecord1 = createActualCostRecord();
    ActualCostRecord actualCostRecord2 = createActualCostRecord();
    JsonObject actualCostRecordJson1 = mapFrom(actualCostRecord1);
    JsonObject actualCostRecordJson2 = mapFrom(actualCostRecord2);
    IndividualResource createResult1 = actualCostRecordClient.create(actualCostRecordJson1);
    IndividualResource createResult2 = actualCostRecordClient.create(actualCostRecordJson2);

    assertThat(createResult1.getJson(), hasSameProperties(actualCostRecordJson1));
    assertThat(createResult2.getJson(), hasSameProperties(actualCostRecordJson2));

    List<JsonObject> actualCostRecords = new ArrayList<>(
      actualCostRecordClient.getMany("lossType==Aged to lost").getRecords());

    assertThat(actualCostRecords, hasItems(hasSameProperties(actualCostRecordJson1),
      hasSameProperties(actualCostRecordJson2)));

    for (JsonObject current : actualCostRecords) {
      actualCostRecordClient.deleteById(UUID.fromString(current.getString("id")));
    }
  }

  @Test
  @SneakyThrows
  public void canCreateAndGetAndUpdateActualCostRecord() {
    ActualCostRecord actualCostRecord = createActualCostRecord();
    JsonObject actualCostRecordJson = mapFrom(actualCostRecord);
    IndividualResource createResult = actualCostRecordClient.create(actualCostRecordJson);

    JsonObject json = createResult.getJson();
    assertThat(json, hasSameProperties(actualCostRecordJson));

    JsonObject updatedJson = json.put("lossType", DECLARED_LOST.value());

    actualCostRecordClient.attemptPutById(updatedJson);

    IndividualResource fetchedActualCostRecord = actualCostRecordClient.getById(json.getString("id"));

    JsonObject json1 = fetchedActualCostRecord.getJson();

    json1.remove("metadata");
    assertThat(updatedJson, hasSameProperties(json1));
  }

  private ActualCostRecord createActualCostRecord() {
    return new ActualCostRecord()
      .withUserId(UUID.randomUUID().toString())
      .withUserBarcode("777")
      .withLoanId(UUID.randomUUID().toString())
      .withLossType(AGED_TO_LOST)
      .withDateOfLoss(new DateTime(DateTimeZone.UTC).toDate())
      .withTitle("Test")
      .withIdentifiers(List.of(new Identifier()
        .withValue("9781466636897")
        .withIdentifierTypeId(UUID.randomUUID().toString())))
      .withItemBarcode("888")
      .withLoanType("Can Circulate")
      .withEffectiveCallNumber("f TX809.M17J66 1993")
      .withPermanentItemLocation("Main circ desk")
      .withFeeFineOwnerId(UUID.randomUUID().toString())
      .withFeeFineOwner("Main circ desk")
      .withFeeFineTypeId(UUID.randomUUID().toString())
      .withFeeFineType("Lost Item fee (actual cost)");
  }

  private JsonObject mapFrom(ActualCostRecord actualCostRecord1)
    throws JsonProcessingException {
    return new JsonObject(objectMapper.writeValueAsString(actualCostRecord1));
  }

}
