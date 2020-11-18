package org.folio.rest.support.http;

import java.net.MalformedURLException;
import java.net.URL;

import org.folio.rest.api.StorageTestSuite;

public class InterfaceUrls {
  private InterfaceUrls() { }

  public static URL loanStorageUrl() throws MalformedURLException {
    return loanStorageUrl("");
  }

  public static URL loanStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/loan-storage/loans" + subPath);
  }

  public static URL patronActionSessionStorageUrl(String subPath)
    throws MalformedURLException {

    return StorageTestSuite.storageUrl("/patron-action-session-storage/patron-action-sessions" + subPath);
  }

  public static URL anonymizeLoansURL() throws MalformedURLException {
    return StorageTestSuite.storageUrl("/anonymize-storage-loans");
  }

  public static URL checkInsStorageUrl(String subPath) throws MalformedURLException {
    return StorageTestSuite.storageUrl("/check-in-storage/check-ins" + subPath);
  }

  public static URL requestExpirationUrl() throws MalformedURLException {
    return StorageTestSuite.storageUrl("/scheduled-request-expiration");
  }
}
