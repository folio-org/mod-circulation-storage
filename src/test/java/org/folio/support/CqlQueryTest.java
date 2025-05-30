package org.folio.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CqlQueryTest {

  @ParameterizedTest
  @ValueSource(strings = {
      "cql.allRecords=1",
      " CQL.ALLRECORDS = foo ",
  })
  void isMatchingAll_true(String cql) {
    assertTrue(new CqlQuery(cql).isMatchingAll());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {
      "=",
      "a=1 AND b=2",
      "name==\"cql.allRecords=1\"",
  })
  void isMatchingAll_false(String cql) {
    assertFalse(new CqlQuery(cql).isMatchingAll());
  }

}
