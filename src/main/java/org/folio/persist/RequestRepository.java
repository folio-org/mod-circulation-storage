package org.folio.persist;

import static org.folio.rest.persist.PgUtil.postgresClient;
import static org.folio.support.ModuleConstants.REQUEST_CLASS;
import static org.folio.support.ModuleConstants.REQUEST_TABLE;

import java.util.Map;

import org.folio.rest.jaxrs.model.Location;
import org.folio.rest.jaxrs.model.Request;
import org.folio.rest.jaxrs.model.Servicepoint;

import com.github.benmanes.caffeine.cache.Cache;

import io.vertx.core.Context;

public class RequestRepository extends AbstractRepository<Request> {
  private final Cache<String, Location> locationCache;
  private final Cache<String, Servicepoint> servicePointCache;

  public RequestRepository(Context context, Map<String, String> okapiHeaders) {
    this(context, okapiHeaders, null, null);
  }

  public RequestRepository(Context context, Map<String, String> okapiHeaders,
    Cache<String, Location> locationCache, Cache<String, Servicepoint> servicePointCache) {
    super(postgresClient(context, okapiHeaders), REQUEST_TABLE, REQUEST_CLASS);
    this.locationCache = locationCache;
    this.servicePointCache = servicePointCache;
  }

  public Cache<String, Location> getLocationCache() {
    return locationCache;
  }

  public Cache<String, Servicepoint> getServicePointCache() {
    return servicePointCache;
  }
}
