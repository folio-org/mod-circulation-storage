package org.folio.rest.support.builders;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class LoanPolicyRequestBuilder {
  private final UUID id;
  private final String name;
  private final String description;

  public LoanPolicyRequestBuilder() {
    this(UUID.randomUUID(), "Example Loan Policy", "An example loan policy");
  }

  private LoanPolicyRequestBuilder(UUID id, String name, String description) {
    this.id = id;
    this.name = name;
    this.description = description;
  }

  public JsonObject create() {
    JsonObject request = new JsonObject();

    if(id != null) {
      request.put("id", id.toString());
    }

    request.put("name", this.name);
    request.put("description", this.description);
    request.put("loanable", true);

    JsonObject loansPolicy = new JsonObject();

    loansPolicy.put("profileId", "Rolling");
    loansPolicy.put("period", createPeriod(1, "Months"));
    loansPolicy.put("closedLibraryDueDateManagementId", "CURRENT_DUE_DATE");
    loansPolicy.put("gracePeriod", createPeriod(7, "Days"));
    loansPolicy.put("openingTimeOffset", createPeriod(3, "Hours"));

    JsonObject requestManagement = new JsonObject();
    JsonObject recalls = new JsonObject();
    recalls.put("alternateGracePeriod", createPeriod(1, "Months"));
    recalls.put("minimumGuaranteedLoanPeriod", createPeriod(1, "Weeks"));
    recalls.put("recallReturnInterval", createPeriod(1, "Days"));
    JsonObject holds = new JsonObject();
    holds.put("alternateCheckoutLoanPeriod", createPeriod(2, "Months"));
    holds.put("renewItemsWithRequest", false);
    holds.put("alternateRenewalLoanPeriod", createPeriod(2, "Days"));
    JsonObject pages = new JsonObject();
    pages.put("alternateCheckoutLoanPeriod", createPeriod(3, "Months"));
    pages.put("renewItemsWithRequest", true);
    pages.put("alternateRenewalLoanPeriod", createPeriod(3, "Days"));
    requestManagement.put("recalls", recalls);
    requestManagement.put("holds", holds);
    requestManagement.put("pages", pages);

    request.put("loansPolicy", loansPolicy);
    request.put("requestManagement", requestManagement);
    request.put("renewable", true);

    JsonObject renewalsPolicy = new JsonObject();

    renewalsPolicy.put("unlimited", true);
    renewalsPolicy.put("renewFromId", "CURRENT_DUE_DATE");
    renewalsPolicy.put("differentPeriod", true);
    renewalsPolicy.put("period", createPeriod(30, "Days"));

    request.put("renewalsPolicy", renewalsPolicy);

    return request;
  }

  public LoanPolicyRequestBuilder withId(UUID id) {
    return new LoanPolicyRequestBuilder(id, this.name, this.description);
  }

  public LoanPolicyRequestBuilder withNoId() {
    return new LoanPolicyRequestBuilder(null, this.name, this.description);
  }

  public LoanPolicyRequestBuilder withName(String name) {
    return new LoanPolicyRequestBuilder(this.id, name, this.description);
  }

  public LoanPolicyRequestBuilder withDescription(String description) {
    return new LoanPolicyRequestBuilder(this.id, this.name, description);
  }

  private JsonObject createPeriod(Integer duration, String intervalId) {
    JsonObject period = new JsonObject();

    period.put("duration", duration);
    period.put("intervalId", intervalId);

    return period;
  }

}
