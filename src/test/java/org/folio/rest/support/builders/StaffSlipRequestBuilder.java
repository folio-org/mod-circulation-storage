package org.folio.rest.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class StaffSlipRequestBuilder {

  public static final String TEST_STAFF_SLIP_NAME = "Test Staff Slip";
  public static final String TEST_STAFF_SLIP_DESCRIPTION = "Test Staff Slip Description";
  public static final String TEST_STAFF_SLIP_Template = "Test Staff Slip Template";
  public static final boolean TEST_STAFF_SLIP_ACTIVE_DEFAULT = true;

  private final UUID id;
  private final String name;
  private final String description;
  private final Boolean active;
  private final String template;

  public StaffSlipRequestBuilder() {
    this(UUID.randomUUID(),
      TEST_STAFF_SLIP_NAME,
      TEST_STAFF_SLIP_DESCRIPTION,
      TEST_STAFF_SLIP_ACTIVE_DEFAULT,
      TEST_STAFF_SLIP_Template
      );
  }

  private StaffSlipRequestBuilder(
    UUID id,
    String name,
    String description,
    boolean active,
    String template) {
	  this.id = id;
	  this.name = name;
	  this.description = description;
	  this.active = active;
	  this.template = template;	  
  }

  public JsonObject create() {
    JsonObject request = new JsonObject();

    if(this.id != null) {
      request.put("id", this.id.toString());
    }

    if(name != null) {
    	request.put("name", name);
    }
    
    if(description != null) {
    	request.put("description", description);
    }
    
    if(active != null) {
    	request.put("active", active);
    }
    
    if(template != null) {
    	request.put("template", template);
    }

    return request;
  }
  
  public StaffSlipRequestBuilder withId(UUID id) {
	    return new StaffSlipRequestBuilder(
	      id,
	      this.name,
	      this.description, 
	      this.active,
	      this.template);
	  }

  public StaffSlipRequestBuilder withName(String name) {
	    return new StaffSlipRequestBuilder(
	      this.id,
	      name,
	      this.description, 
	      this.active,
	      this.template);
	  }
  
  public StaffSlipRequestBuilder withDescription(String description) {
    return new StaffSlipRequestBuilder(
      this.id,
      this.name,
      description, 
      this.active,
      this.template);
  } 
  
  public StaffSlipRequestBuilder withActive(boolean active) {
    return new StaffSlipRequestBuilder(
      this.id,
      this.name,
      this.description, 
      active,
      this.template);
  } 
  
  public StaffSlipRequestBuilder withTemplate(String template) {
    return new StaffSlipRequestBuilder(
      this.id,
      this.name,
      this.description, 
      this.active,
      template);
  } 

}
