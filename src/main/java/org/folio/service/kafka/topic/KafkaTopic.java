package org.folio.service.kafka.topic;

public class KafkaTopic {

  private static final String MODULE_PREFIX = "circulation";

  private final String qualifiedName;


  KafkaTopic(String qualifiedName) {
    this.qualifiedName = qualifiedName;
  }

  public static KafkaTopic request(String tenantId, String environmentName) {
    return forName("request", tenantId, environmentName);
  }

  public static KafkaTopic loan(String tenantId, String environmentName) {
    return forName("loan", tenantId, environmentName);
  }

  public static KafkaTopic checkIn(String tenantId, String environmentName) {
    return forName("check-in", tenantId, environmentName);
  }

  public static KafkaTopic forName(String name, String tenantId, String environmentName) {
    return new KafkaTopic(qualifyName(name, environmentName, tenantId));
  }

  public String getQualifiedName() {
    return qualifiedName;
  }

  private static String qualifyName(String name, String environmentName, String tenantId) {
    return String.join(".", environmentName, tenantId, MODULE_PREFIX, name);
  }

}
