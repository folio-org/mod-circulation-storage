package org.folio.support;

public class Environment {
  private Environment() { }

  public static String environmentName() {
    return System.getenv().getOrDefault("ENV", "folio");
  }
}
