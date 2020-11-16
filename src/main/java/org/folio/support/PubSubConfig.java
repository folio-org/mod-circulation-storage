package org.folio.support;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class PubSubConfig {

  private static final Properties properties = new Properties();
  private static final Logger logger = LoggerFactory.getLogger(PubSubConfig.class);

  static {
    try (InputStream is = PubSubConfig.class.getClassLoader()
      .getResourceAsStream("okapi.properties")) {
      properties.load(is);
    } catch (IOException e) {
      logger.error("PubSub config not found", e);
    }
  }

  public static String getOkapiHost() {
    return properties.getProperty("okapi.host");
  }

  public static Integer getOkapiPort() {
    return Integer.parseInt(properties.getProperty("okapi.port"));
  }

}
