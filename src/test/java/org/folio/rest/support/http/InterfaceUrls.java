package org.folio.rest.support.http;

import static org.folio.rest.api.StorageTestSuite.storageUrl;

import java.net.MalformedURLException;
import java.net.URL;

public class InterfaceUrls {
  private InterfaceUrls() { }

  public static URL loanStorageUrl() throws MalformedURLException {
    return loanStorageUrl("");
  }

  public static URL loanStorageUrl(String subPath)
    throws MalformedURLException {

    return storageUrl("/loan-storage/loans" + subPath);
  }

  public static URL actualCostRecord(String subPath) throws MalformedURLException {
    return storageUrl("/actual-cost-record-storage/actual-cost-records" + subPath);
  }

  public static URL loanHistoryUrl(String subPath) throws MalformedURLException {
    return storageUrl("/loan-storage/loan-history" + subPath);
  }

  public static URL patronActionSessionStorageUrl(String subPath)
    throws MalformedURLException {

    return storageUrl("/patron-action-session-storage/patron-action-sessions" + subPath);
  }

  public static URL anonymizeLoansURL() throws MalformedURLException {
    return storageUrl("/anonymize-storage-loans");
  }

  public static URL checkInsStorageUrl(String subPath) throws MalformedURLException {
    return storageUrl("/check-in-storage/check-ins" + subPath);
  }

  public static URL requestExpirationUrl() throws MalformedURLException {
    return storageUrl("/scheduled-request-expiration");
  }

  public static URL checkOutStorageUrl(String subPath) throws MalformedURLException {
    return storageUrl("/check-out-lock-storage" + subPath);
  }

}
