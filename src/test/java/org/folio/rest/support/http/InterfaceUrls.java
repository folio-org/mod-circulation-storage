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
}
