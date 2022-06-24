package org.folio.rest.api;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.rest.jaxrs.model.ActualCostRecord;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.jaxrs.model.Identifier;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.folio.rest.support.matchers.UUIDMatchers;
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
import static org.folio.rest.jaxrs.model.ActualCostRecord.ItemLossType.AGED_TO_LOST;
import static org.folio.rest.support.matchers.JsonMatchers.hasSameProperties;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
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
    JsonObject actualCostRecord1 = toJsonObject(createActualCostRecord());
    JsonObject actualCostRecord2 = toJsonObject(createActualCostRecord());
    JsonObject createResult1 = actualCostRecordClient.create(actualCostRecord1).getJson();
    JsonObject createResult2 = actualCostRecordClient.create(actualCostRecord2).getJson();

    assertThat(createResult1, hasSameProperties(actualCostRecord1));
    assertThat(createResult2, hasSameProperties(actualCostRecord2));

    List<JsonObject> actualCostRecords = new ArrayList<>(
      actualCostRecordClient.getMany("itemLossType==Aged to lost").getRecords());

    assertThat(actualCostRecords, hasItems(hasSameProperties(createResult1),
      hasSameProperties(createResult2)));

    for (JsonObject current : actualCostRecords) {
      actualCostRecordClient.deleteById(UUID.fromString(current.getString("id")));
    }

    assertThat(actualCostRecordClient.getAll().getTotalRecords(), is(0));
  }

  @Test
  @SneakyThrows
  public void canCreateAndGetAndUpdateActualCostRecord() {
    JsonObject actualCostRecord = toJsonObject(createActualCostRecord());
    JsonObject createResult = actualCostRecordClient.create(actualCostRecord).getJson();

    assertThat(createResult, hasSameProperties(actualCostRecord));

    UUID accountId = UUID.randomUUID();
    JsonObject updatedJson = createResult.put("accountId", accountId.toString());
    updateActualCostRecordAndCheckThatAllPropertiesAreSame(updatedJson);

    updatedJson.remove("accountId");
    updateActualCostRecordAndCheckThatAllPropertiesAreSame(updatedJson);
  }

  private void updateActualCostRecordAndCheckThatAllPropertiesAreSame(JsonObject updatedJson)
    throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    actualCostRecordClient.attemptPutById(updatedJson);
    JsonObject fetchedJson = actualCostRecordClient.getById(updatedJson.getString("id")).getJson();
    fetchedJson.remove("metadata");
    assertThat(updatedJson, hasSameProperties(fetchedJson));
  }

  private ActualCostRecord createActualCostRecord() {
    return new ActualCostRecord()
      .withUserId(UUID.randomUUID().toString())
      .withUserBarcode("777")
      .withLoanId(UUID.randomUUID().toString())
      .withItemLossType(AGED_TO_LOST)
      .withDateOfLoss(new DateTime(DateTimeZone.UTC).toDate())
      .withTitle("Test")
      .withIdentifiers(List.of(new Identifier()
        .withValue("9781466636897")
        .withIdentifierTypeId(UUID.randomUUID().toString())))
      .withItemBarcode("888")
      .withLoanType("Can Circulate")
      .withEffectiveCallNumberComponents(new EffectiveCallNumberComponents()
        .withCallNumber("callnumber")
        .withPrefix("prefix")
        .withSuffix("suffix"))
      .withPermanentItemLocation("Main circ desk")
      .withFeeFineOwnerId(UUID.randomUUID().toString())
      .withFeeFineOwner("Main circ desk")
      .withFeeFineTypeId(UUID.randomUUID().toString())
      .withFeeFineType("Lost Item fee (actual cost)");
  }

  private JsonObject toJsonObject(ActualCostRecord actualCostRecord1)
    throws JsonProcessingException {
    return new JsonObject(objectMapper.writeValueAsString(actualCostRecord1));
  }

}
