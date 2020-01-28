package org.folio.rest.support.builders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public class RequestItemSummary {
  final String title;
  final String barcode;
  final List<Pair<UUID, String>> identifiers;

  public RequestItemSummary(String title, String barcode) {
    this(title, barcode, Collections.emptyList());
  }

  private RequestItemSummary(String title, String barcode, List<Pair<UUID, String>> identifiers) {
    this.title = title;
    this.barcode = barcode;
    this.identifiers = new ArrayList<>(identifiers);
  }

  public RequestItemSummary addIdentifier(UUID identifierId, String value) {
    final List<Pair<UUID, String>> copiedIdentifiers = new ArrayList<>(identifiers);
    copiedIdentifiers.add(new ImmutablePair<>(identifierId, value));

    return new RequestItemSummary(
      this.title,
      this.barcode,
      copiedIdentifiers
    );
  }
}
