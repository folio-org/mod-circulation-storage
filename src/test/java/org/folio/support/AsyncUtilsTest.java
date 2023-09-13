package org.folio.support;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.folio.support.AsyncUtils.mapSequentially;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import lombok.SneakyThrows;

class AsyncUtilsTest {

  @Test
  @SneakyThrows
  void mapSequentiallyRunsFuturesInOrder() {
    List<Integer> numbers = IntStream.range(0, 1000)
      .boxed()
      .collect(Collectors.toList());

    List<Integer> mappingResults = new ArrayList<>();

    Function<Integer, Future<Integer>> mapper = number ->
      Future.fromCompletionStage(supplyAsync(() -> mappingResults.add(number))
        .thenApply(r -> number));

    Collection<Integer> invocationResults = mapSequentially(numbers, mapper)
      .toCompletionStage()
      .toCompletableFuture()
      .get(5, TimeUnit.SECONDS);

    assertEquals(numbers, mappingResults);
    assertEquals(numbers, invocationResults);
  }
}