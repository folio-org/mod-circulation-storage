package org.folio.rest.support;

import org.folio.rest.api.StorageTestSuite;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;

/**
 * Owns the shared integration-test environment for the lifetime of a Jupiter test plan.
 */
public final class StorageTestEnvironmentExtension implements BeforeAllCallback {
  private static final Namespace NAMESPACE = Namespace.create(StorageTestEnvironmentExtension.class);

  @Override
  public void beforeAll(ExtensionContext context) {
    context.getRoot().getStore(NAMESPACE)
      .getOrComputeIfAbsent(TestEnvironment.class, ignored -> new TestEnvironment());
  }

  private static final class TestEnvironment implements CloseableResource {
    private TestEnvironment() {
      try {
        StorageTestSuite.before();
      } catch (Exception e) {
        throw new IllegalStateException("Failed to start the storage integration-test environment", e);
      }
    }

    @Override
    public void close() throws Exception {
      StorageTestSuite.after();
    }
  }
}
