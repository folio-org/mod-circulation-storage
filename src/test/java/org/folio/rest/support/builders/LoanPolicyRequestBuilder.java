package org.folio.rest.support.builders;

import java.util.UUID;

import org.folio.rest.jaxrs.model.Period;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.With;

@AllArgsConstructor
@With
public class LoanPolicyRequestBuilder extends JsonBuilder {
  private final UUID id;
  private final String name;
  private final String description;
  private final String profile;
  private final String fixedDueDateScheduleId;
  private final String alternateFixedDueDateScheduleId;
  private final Period holdsRenewalLoanPeriod;
  private final Period loanPeriod;
  private final Period renewalPeriod;

  public JsonObject create() {
    JsonObject request = new JsonObject();

    if (id != null) {
      request.put("id", id.toString());
    }

    request.put("name", this.name);
    request.put("description", this.description);
    request.put("loanable", true);

    JsonObject loansPolicy = new JsonObject();

    loansPolicy.put("profileId", profile);
    if (loanPeriod != null) {
      loansPolicy.put("period", JsonObject.mapFrom(loanPeriod));
    }

    loansPolicy.put("closedLibraryDueDateManagementId", "CURRENT_DUE_DATE");
    loansPolicy.put("gracePeriod", createPeriod(7, "Days"));
    loansPolicy.put("openingTimeOffset", createPeriod(3, "Hours"));
    put(loansPolicy, "fixedDueDateScheduleId", fixedDueDateScheduleId);

    JsonObject requestManagement = new JsonObject();
    JsonObject recalls = new JsonObject();
    recalls.put("alternateGracePeriod", createPeriod(1, "Months"));
    recalls.put("minimumGuaranteedLoanPeriod", createPeriod(1, "Weeks"));
    recalls.put("recallReturnInterval", createPeriod(1, "Days"));
    recalls.put("alternateRecallReturnInterval", createPeriod(4, "Hours"));
    recalls.put("allowRecallsExtendOverdueLoan", true);

    if (holdsRenewalLoanPeriod != null) {
      JsonObject holds = new JsonObject();
      holds.put("alternateCheckoutLoanPeriod", createPeriod(2, "Months"));
      holds.put("renewItemsWithRequest", true);
      holds.put("alternateRenewalLoanPeriod", JsonObject.mapFrom(holdsRenewalLoanPeriod));

      requestManagement.put("holds", holds);
    }

    JsonObject pages = new JsonObject();
    pages.put("alternateCheckoutLoanPeriod", createPeriod(3, "Months"));
    pages.put("renewItemsWithRequest", true);
    pages.put("alternateRenewalLoanPeriod", createPeriod(3, "Days"));
    requestManagement.put("recalls", recalls);
    requestManagement.put("pages", pages);

    request.put("loansPolicy", loansPolicy);
    request.put("requestManagement", requestManagement);
    request.put("renewable",
      renewalPeriod != null || alternateFixedDueDateScheduleId != null);

    JsonObject renewalsPolicy = new JsonObject();

    renewalsPolicy.put("unlimited", true);
    renewalsPolicy.put("renewFromId", "CURRENT_DUE_DATE");
    renewalsPolicy.put("differentPeriod", true);
    put(renewalsPolicy, "alternateFixedDueDateScheduleId", alternateFixedDueDateScheduleId);

    if (renewalPeriod != null) {
        renewalsPolicy.put("period", JsonObject.mapFrom(renewalPeriod));
    }
    request.put("renewalsPolicy", renewalsPolicy);

    return request;
  }

  public LoanPolicyRequestBuilder withNoId() {
    return withId(null);
  }

  public LoanPolicyRequestBuilder fixed(String fixedDueDateScheduleId) {
    return withProfile("Fixed").withFixedDueDateScheduleId(fixedDueDateScheduleId);
  }

  public static LoanPolicyRequestBuilder defaultRollingPolicy() {
    return new LoanPolicyRequestBuilder(UUID.randomUUID(),
      "Example Loan Policy",
      "An example loan policy",
      "Rolling",
      null,
      null,
      createPeriod(2, "Days").mapTo(Period.class),
      createPeriod(1, "Months").mapTo(Period.class),
      createPeriod(30, "Days").mapTo(Period.class)
    );
  }

  public static LoanPolicyRequestBuilder emptyPolicy() {
    return new LoanPolicyRequestBuilder(UUID.randomUUID(),
      "Example Loan Policy",
      "An example loan policy",
      null,
      null,
      null,
      null,
      null,
      null
    );
  }

  private static JsonObject createPeriod(Integer duration, String intervalId) {
    JsonObject period = new JsonObject();

    period.put("duration", duration);
    period.put("intervalId", intervalId);

    return period;
  }
}
