package org.folio.rest.support;

import org.folio.rest.api.StorageTestSuite;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class ApiTests {
  private static boolean runningOnOwn;

  protected final HttpClient client = new HttpClient(StorageTestSuite.getVertx());

  @BeforeClass
  public static void before()
    throws Exception {

    if(StorageTestSuite.isNotInitialised()) {
      System.out.println("Running test on own, initialising suite manually");
      runningOnOwn = true;
      StorageTestSuite.before();
    }
  }

  @AfterClass
  public static void after()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    if(runningOnOwn) {
      System.out.println("Running test on own, un-initialising suite manually");
      StorageTestSuite.after();
    }
  }
}
