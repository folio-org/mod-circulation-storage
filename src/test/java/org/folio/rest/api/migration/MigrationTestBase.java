package org.folio.rest.api.migration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.rest.api.StorageTestSuite.TENANT_ID;

import io.vertx.core.Vertx;
import lombok.SneakyThrows;

import org.folio.rest.api.StorageTestSuite;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.support.ApiTests;
import org.folio.util.ResourceUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;

abstract class MigrationTestBase extends ApiTests {
  static String loadScript(String scriptName) {
    return loadScript(scriptName, MigrationTestBase::replaceSchema);
  }

  @SafeVarargs
  static String loadScript(String scriptName, UnaryOperator<String>... replacementFunctions) {
    String resource = ResourceUtil.asString("/templates/db_scripts/" + scriptName);

    for (UnaryOperator<String> replacementFunction : replacementFunctions) {
      resource = replacementFunction.apply(resource);
    }

    return resource;
  }

  static String replaceSchema(String resource) {
    return resource.replace("${myuniversity}", TENANT_ID)
      .replace("${mymodule}", "mod_circulation_storage");
  }

  /**
   * Executes multiply SQL statements separated by a line separator (either CRLF|CR|LF).
   *
   * @param allStatements - Statements to execute (separated by a line separator).
   * @throws InterruptedException
   * @throws ExecutionException
   * @throws TimeoutException
   */
  void executeMultipleSqlStatements(String allStatements)
    throws InterruptedException, ExecutionException, TimeoutException {

    final CompletableFuture<Void> result = new CompletableFuture<>();
    final Vertx vertx = StorageTestSuite.getVertx();

    PostgresClient.getInstance(vertx).runSQLFile(allStatements, true, handler -> {
      if (handler.failed()) {
        result.completeExceptionally(handler.cause());
      } else if (!handler.result().isEmpty()) {
        result.completeExceptionally(new RuntimeException("Failing SQL: " + handler.result().toString()));
      } else {
        result.complete(null);
      }
    });

    result.get(5, SECONDS);
  }

  @SneakyThrows
  protected void executeSqlScript(String filePath) {
    executeMultipleSqlStatements(readResource(filePath));
  }

  @SneakyThrows
  protected String readResource(String filePath) {
    return replaceSchema(
      Files.readString(Path.of(getClass().getResource(filePath).toURI()))
    );
  }
}
