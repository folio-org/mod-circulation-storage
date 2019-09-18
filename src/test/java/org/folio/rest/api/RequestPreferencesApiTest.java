package org.folio.rest.api;

import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isCreated;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isNoContent;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isNotFound;
import static org.folio.rest.support.matchers.HttpResponseStatusCodeMatchers.isUnprocessableEntity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.core.Is.is;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.RequestPreference;
import org.folio.rest.jaxrs.model.RequestPreferences;
import org.folio.rest.support.ApiTests;
import org.folio.rest.support.JsonResponse;
import org.folio.rest.support.ResponseHandler;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class RequestPreferencesApiTest extends ApiTests {
  private static final int CONNECTION_TIMEOUT = 5;
  private static final String USER_ID = "1e425b93-501e-44b0-a4c7-b3e66a25c42e";
  private static final String USER_ID2 = "2e425b93-501e-44b0-a4c7-b3e66a25c42e";
  private static final String SERVICE_POINT_ID = "22beccec-8d77-4a97-906a-37cc26b070e5";
  private static final String ADDRESS_TYPE_ID = "27a1b086-20ac-4b1d-b6ac-3be353383f3d";
  private static final boolean HOLD_SHELF = true;
  private static final boolean DELIVERY = true;
  private static final RequestPreference.Fulfillment FULFILLMENT = RequestPreference.Fulfillment.DELIVERY;

  @Before
  public void setUp() {
    cleanupRequestPreferences();
  }

  @Test
  public void canCreateARequestPreference() {
    JsonResponse response = createRequestPreference();
    RequestPreference preference = response.getJson().mapTo(RequestPreference.class);

    assertThat(response, isCreated());
    assertThat(preference.getDefaultServicePointId(), is(SERVICE_POINT_ID));
    assertThat(preference.getUserId(), is(USER_ID));
    assertThat(preference.getDefaultDeliveryAddressTypeId(), is(ADDRESS_TYPE_ID));
    assertThat(preference.getHoldShelf(), is(HOLD_SHELF));
    assertThat(preference.getDelivery(), is(DELIVERY));
    assertThat(preference.getFulfillment(), is(FULFILLMENT));
  }

  @Test
  public void cannotCreateSecondRequestPreferenceForTheSameUser() {
    JsonResponse response1 = createRequestPreference();
    assertThat(response1, isCreated());

    JsonResponse response2 = createRequestPreference();
    assertThat(response2, isUnprocessableEntity());
    assertThat(response2.getJson().toString(), containsString("Request preference for specified user already exists"));
  }

  @Test
  public void cannotCreateRequestWithInvalidId() {
    JsonResponse response1 = createRequestPreference("invalid_id");
    assertThat(response1, isUnprocessableEntity());
  }

  @Test
  public void canGetRequestPreferences() {

    createRequestPreference(USER_ID);
    createRequestPreference(USER_ID2);

    JsonResponse responseGet = getPreferences("");

    List<RequestPreference> preferences = responseGet.getJson().getJsonArray("requestPreferences").stream()
      .map(o -> ((JsonObject) o).mapTo(RequestPreference.class))
      .collect(Collectors.toList());

    assertThat(responseGet.getJson().getInteger("totalRecords"), is(2));

    assertThat(preferences, hasItem(hasProperty("userId", Matchers.is(USER_ID))));
    assertThat(preferences, hasItem(hasProperty("userId", Matchers.is(USER_ID2))));
  }

  @Test
  public void canGetRequestPreferenceByUserIdUsingQuery() {
    RequestPreference createdPreference = createRequestPreference().getJson().mapTo(RequestPreference.class);

    JsonResponse response = getPreferences("query=userId=" + USER_ID);
    RequestPreferences preferences = response.getJson().mapTo(RequestPreferences.class);

    assertThat(preferences.getTotalRecords(), is(1));

    RequestPreference foundPreference = preferences.getRequestPreferences().get(0);
    assertPreferenceEquals(createdPreference, foundPreference);
  }

  @Test
  public void canGetRequestPreferenceById() {

    RequestPreference preference = createRequestPreference().getJson().mapTo(RequestPreference.class);

    RequestPreference responsePreference = getPreference(preference.getId()).getJson().mapTo(RequestPreference.class);
    assertPreferenceEquals(preference, responsePreference);
  }

  @Test
  public void cannotGetRequestPreferenceByNotExistingId() {
    JsonResponse response = getPreference(UUID.randomUUID().toString());
    assertThat(response, isNotFound());
  }

  @Test
  public void canDeleteRequestPreferenceById() {
    RequestPreference preference = createRequestPreference().getJson().mapTo(RequestPreference.class);
    JsonResponse response = deletePreference(preference.getId());
    assertThat(response, isNoContent());
  }

  @Test
  public void cannotDeleteRequestPreferenceByNotExistingId() {
    JsonResponse response = deletePreference(UUID.randomUUID().toString());
    assertThat(response, isNotFound());
  }

  @Test
  public void canUpdateRequestPreference() {
    RequestPreference preference = createRequestPreference().getJson().mapTo(RequestPreference.class);
    preference.setDelivery(false);
    preference.setDefaultDeliveryAddressTypeId(null);
    JsonResponse response = udpatePreference(preference);
    assertThat(response, isNoContent());

    RequestPreference updatedPreference = getPreference(preference.getId()).getJson().mapTo(RequestPreference.class);
    assertPreferenceEquals(preference, updatedPreference);
  }

  @Test
  public void cannotUpdateRequestPreferenceWithNotExistingId() {
    RequestPreference preference = constructDefaultPreference(USER_ID).withId(UUID.randomUUID().toString());
    JsonResponse response = udpatePreference(preference);
    assertThat(response, isNotFound());
  }

  @Test
  public void cannotUpdateRequestPreferenceWithDuplicateUserId() {
    createRequestPreference(USER_ID);
    RequestPreference secondPreference = createRequestPreference(USER_ID2).getJson().mapTo(RequestPreference.class);

    JsonResponse response = udpatePreference(constructDefaultPreference(USER_ID).withId(secondPreference.getId()));
    assertThat(response, isUnprocessableEntity());
    assertThat(response.getJson().toString(), containsString("Request preference for specified user already exists"));
  }

  @Test
  public void cannotUpdateRequestPreferenceWithInvalidId() {
    RequestPreference preference = constructDefaultPreference(USER_ID).withId("invalid_id");
    JsonResponse response = udpatePreference(preference);
    assertThat(response, isUnprocessableEntity());
  }

  private void assertPreferenceEquals(RequestPreference preference1, RequestPreference preference2) {
    assertThat(preference1.getId(), is(preference2.getId()));
    assertThat(preference1.getDefaultServicePointId(), is(preference2.getDefaultServicePointId()));
    assertThat(preference1.getUserId(), is(preference2.getUserId()));
    assertThat(preference1.getDefaultDeliveryAddressTypeId(), is(preference2.getDefaultDeliveryAddressTypeId()));
    assertThat(preference1.getHoldShelf(), is(preference2.getHoldShelf()));
    assertThat(preference1.getDelivery(), is(preference2.getDelivery()));
    assertThat(preference1.getFulfillment(), is(preference2.getFulfillment()));
  }

  private JsonResponse getPreference(String id) {
    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(requestPreferenceStorageUrl("/" + id), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getFromFuture(getCompleted);
  }

  private JsonResponse deletePreference(String id) {
    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.delete(requestPreferenceStorageUrl("/" + id), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getFromFuture(getCompleted);
  }

  private JsonResponse udpatePreference(RequestPreference preference) {
    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.put(requestPreferenceStorageUrl("/" + preference.getId()), preference, StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));

    return getFromFuture(getCompleted);
  }

  private JsonResponse getPreferences(String query) {
    CompletableFuture<JsonResponse> getCompleted = new CompletableFuture<>();
    client.get(requestPreferenceStorageUrl("?" + query), StorageTestSuite.TENANT_ID,
      ResponseHandler.json(getCompleted));
    return getFromFuture(getCompleted);
  }

  private URL requestPreferenceStorageUrl(String path, String... parameterKeyValue) {
    try {
      return StorageTestSuite.storageUrl("/request-preference-storage/request-preference" + path, parameterKeyValue);
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }

  private JsonResponse createRequestPreference() {
    return createRequestPreference(USER_ID);
  }

  private JsonResponse createRequestPreference(String userId) {

    CompletableFuture<JsonResponse> createCompleted = new CompletableFuture<>();

    RequestPreference preference = constructDefaultPreference(userId);

    client.post(requestPreferenceStorageUrl(""),
      preference,
      StorageTestSuite.TENANT_ID,
      ResponseHandler.json(createCompleted));

    return getFromFuture(createCompleted);
  }

  private RequestPreference constructDefaultPreference(String userId) {
    return new RequestPreference()
      .withUserId(userId)
      .withHoldShelf(HOLD_SHELF)
      .withDefaultServicePointId(SERVICE_POINT_ID)
      .withDelivery(DELIVERY)
      .withDefaultDeliveryAddressTypeId(ADDRESS_TYPE_ID)
      .withFulfillment(FULFILLMENT);
  }

  private JsonResponse getFromFuture(CompletableFuture<JsonResponse> createCompleted) {
    JsonResponse response;
    try {
      response = createCompleted.get(CONNECTION_TIMEOUT, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new IllegalStateException(e);
    }
    return response;
  }

  private void cleanupRequestPreferences() {
    RequestPreferences preferences = getPreferences("").getJson().mapTo(RequestPreferences.class);
    for (RequestPreference preference : preferences.getRequestPreferences()) {
      deletePreference(preference.getId());
    }
  }
}
