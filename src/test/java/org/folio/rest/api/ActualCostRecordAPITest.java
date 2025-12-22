package org.folio.rest.api;

import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.rest.support.matchers.JsonMatchers.hasSameProperties;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasMessageContaining;
import static org.folio.rest.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsIterableContaining.hasItems;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.folio.rest.jaxrs.model.ActualCostRecord;
import org.folio.rest.jaxrs.model.ActualCostRecordFeeFine;
import org.folio.rest.jaxrs.model.ActualCostRecordIdentifier;
import org.folio.rest.jaxrs.model.ActualCostRecordInstance;
import org.folio.rest.jaxrs.model.ActualCostRecordItem;
import org.folio.rest.jaxrs.model.ActualCostRecordLoan;
import org.folio.rest.jaxrs.model.ActualCostRecordUser;
import org.folio.rest.jaxrs.model.Contributor;
import org.folio.rest.jaxrs.model.EffectiveCallNumberComponents;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.http.AssertingRecordClient;
import org.folio.rest.support.http.InterfaceUrls;
import org.folio.rest.support.spring.TestContextConfiguration;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

@ContextConfiguration(classes = TestContextConfiguration.class)
@ExtendWith(SpringExtension.class)
class ActualCostRecordAPITest extends ApiTests {
  private static final String ACTUAL_COST_RECORD_TABLE = "actual_cost_record";

  @Autowired
  private ObjectMapper objectMapper;

  private final AssertingRecordClient actualCostRecordClient =
    new AssertingRecordClient(client, StorageTestSuite.TENANT_ID,
      InterfaceUrls::actualCostRecord, "actualCostRecords");

  @BeforeEach
  void beforeEach() throws Exception {
    StorageTestSuite.cleanUpTable(ACTUAL_COST_RECORD_TABLE);
  }

  @Test
  @SneakyThrows
  void canCreateAndGetAndDeleteActualCostRecords() {
    JsonObject actualCostRecord1 = toJsonObject(createActualCostRecord());
    JsonObject actualCostRecord2 = toJsonObject(createActualCostRecord());
    JsonObject createResult1 = actualCostRecordClient.create(actualCostRecord1).getJson();
    JsonObject createResult2 = actualCostRecordClient.create(actualCostRecord2).getJson();

    assertThat(createResult1, hasSameProperties(actualCostRecord1));
    assertThat(createResult2, hasSameProperties(actualCostRecord2));

    List<JsonObject> actualCostRecords = new ArrayList<>(
      actualCostRecordClient.getMany("lossType==Aged to lost").getRecords());

    assertThat(actualCostRecords, hasItems(hasSameProperties(createResult1),
      hasSameProperties(createResult2)));

    for (JsonObject current : actualCostRecords) {
      actualCostRecordClient.deleteById(UUID.fromString(current.getString("id")));
    }

    assertThat(actualCostRecordClient.getAll().getTotalRecords(), is(0));
  }

  @Test
  @SneakyThrows
  void canCreateAndGetAndUpdateActualCostRecord() {
    JsonObject actualCostRecord = toJsonObject(createActualCostRecord());
    JsonObject createResult = actualCostRecordClient.create(actualCostRecord).getJson();

    assertThat(createResult, hasSameProperties(actualCostRecord));

    UUID accountId = UUID.randomUUID();
    JsonObject updatedJson = createResult.put("accountId", accountId.toString());
    updateActualCostRecordAndCheckTheResult(updatedJson);

    updatedJson.remove("accountId");
    updateActualCostRecordAndCheckTheResult(updatedJson);
  }

  @Test
  @SneakyThrows
  void canCreateActualCostRecordWithDefaultStatus() {
    JsonObject recordWithoutStatus = toJsonObject(createActualCostRecord().withStatus(null));
    JsonObject postResponse = actualCostRecordClient.create(recordWithoutStatus).getJson();
    assertThat(postResponse.getString("status"), is("Open"));
  }

  @SneakyThrows
  private void updateActualCostRecordAndCheckTheResult(JsonObject updatedJson) {
    actualCostRecordClient.attemptPutById(updatedJson);
    JsonObject fetchedJson = actualCostRecordClient.getById(updatedJson.getString("id")).getJson();
    fetchedJson.remove("metadata");
    assertThat(updatedJson, hasSameProperties(fetchedJson));
  }

  @Test
  @SneakyThrows
  void canNotCreateActualCostRecordWithNegativeBilledAmount() {
    ActualCostRecord actualCostRecord = createActualCostRecord();
    actualCostRecord.getFeeFine().setBilledAmount(-9.99);
    JsonResponse postResponse = actualCostRecordClient.attemptCreate(toJsonObject(actualCostRecord));
    assertThat(postResponse.getStatusCode(), is(HTTP_UNPROCESSABLE_ENTITY.toInt()));
    assertThat(postResponse.getJson(), hasErrorWith(allOf(
      hasParameter("feeFine.billedAmount", "-9.99"),
      hasMessageContaining("must be greater than or equal to 0")
    )));
  }

  private ActualCostRecord createActualCostRecord() {
    return new ActualCostRecord()
      .withLossType(ActualCostRecord.LossType.AGED_TO_LOST)
      .withLossDate(new DateTime(DateTimeZone.UTC).toDate())
      .withExpirationDate(new DateTime(DateTimeZone.UTC).toDate())
      .withUser(new ActualCostRecordUser()
        .withId(randomId())
        .withBarcode("barcode")
        .withFirstName("firstName")
        .withLastName("lastName")
        .withMiddleName("middleName")
        .withPatronGroupId(randomId())
        .withPatronGroup("patronGroup"))
      .withLoan(new ActualCostRecordLoan()
        .withId(randomId()))
      .withItem(new ActualCostRecordItem()
        .withId(randomId())
        .withBarcode("barcode")
        .withMaterialTypeId(randomId())
        .withMaterialType("material type")
        .withPermanentLocationId(randomId())
        .withPermanentLocation("permanent location")
        .withEffectiveLocationId(randomId())
        .withEffectiveLocation("effective location")
        .withLoanTypeId(randomId())
        .withLoanType("loan type")
        .withHoldingsRecordId(randomId())
        .withEffectiveCallNumberComponents(new EffectiveCallNumberComponents()
          .withCallNumber("call number")
          .withPrefix("prefix")
          .withSuffix("suffix"))
        .withVolume("volume")
        .withEnumeration("enumeration")
        .withChronology("chronology")
        .withDisplaySummary("displaySummary")
        .withCopyNumber("copyNumber"))
      .withInstance(new ActualCostRecordInstance()
        .withId(randomId())
        .withTitle("title")
        .withIdentifiers(List.of(new ActualCostRecordIdentifier()
          .withIdentifierTypeId(randomId())
          .withIdentifierType("identifier type")
          .withValue("identifier value")))
        .withContributors(List.of(new Contributor()
          .withName("Last name, First name"))))
      .withFeeFine(new ActualCostRecordFeeFine()
        .withAccountId(randomId())
        .withBilledAmount(9.99)
        .withOwnerId(randomId())
        .withOwner("fee/fine owner")
        .withTypeId(randomId())
        .withType("Lost Item fee (actual cost)"))
      .withStatus(ActualCostRecord.Status.BILLED)
      .withAdditionalInfoForStaff("Test information for staff")
      .withAdditionalInfoForPatron("Test information for patron");
  }

  private JsonObject toJsonObject(ActualCostRecord actualCostRecord1)
    throws JsonProcessingException {
    return new JsonObject(objectMapper.writeValueAsString(actualCostRecord1));
  }

}
