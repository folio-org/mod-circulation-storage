
package org.folio.rest.jaxrs.model;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Generated;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
    "id",
    "userId",
    "itemId",
    "status",
    "loanDate",
    "returnDate"
})
public class Loan {

    @JsonProperty("id")
    private String id;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("userId")
    @NotNull
    private String userId;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("itemId")
    @NotNull
    private String itemId;
    @JsonProperty("status")
    @Valid
    private Status status;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("loanDate")
    @NotNull
    private String loanDate;
    @JsonProperty("returnDate")
    private String returnDate;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * 
     * @return
     *     The id
     */
    @JsonProperty("id")
    public String getId() {
        return id;
    }

    /**
     * 
     * @param id
     *     The id
     */
    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    public Loan withId(String id) {
        this.id = id;
        return this;
    }

    /**
     * 
     * (Required)
     * 
     * @return
     *     The userId
     */
    @JsonProperty("userId")
    public String getUserId() {
        return userId;
    }

    /**
     * 
     * (Required)
     * 
     * @param userId
     *     The userId
     */
    @JsonProperty("userId")
    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Loan withUserId(String userId) {
        this.userId = userId;
        return this;
    }

    /**
     * 
     * (Required)
     * 
     * @return
     *     The itemId
     */
    @JsonProperty("itemId")
    public String getItemId() {
        return itemId;
    }

    /**
     * 
     * (Required)
     * 
     * @param itemId
     *     The itemId
     */
    @JsonProperty("itemId")
    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public Loan withItemId(String itemId) {
        this.itemId = itemId;
        return this;
    }

    /**
     * 
     * @return
     *     The status
     */
    @JsonProperty("status")
    public Status getStatus() {
        return status;
    }

    /**
     * 
     * @param status
     *     The status
     */
    @JsonProperty("status")
    public void setStatus(Status status) {
        this.status = status;
    }

    public Loan withStatus(Status status) {
        this.status = status;
        return this;
    }

    /**
     * 
     * (Required)
     * 
     * @return
     *     The loanDate
     */
    @JsonProperty("loanDate")
    public String getLoanDate() {
        return loanDate;
    }

    /**
     * 
     * (Required)
     * 
     * @param loanDate
     *     The loanDate
     */
    @JsonProperty("loanDate")
    public void setLoanDate(String loanDate) {
        this.loanDate = loanDate;
    }

    public Loan withLoanDate(String loanDate) {
        this.loanDate = loanDate;
        return this;
    }

    /**
     * 
     * @return
     *     The returnDate
     */
    @JsonProperty("returnDate")
    public String getReturnDate() {
        return returnDate;
    }

    /**
     * 
     * @param returnDate
     *     The returnDate
     */
    @JsonProperty("returnDate")
    public void setReturnDate(String returnDate) {
        this.returnDate = returnDate;
    }

    public Loan withReturnDate(String returnDate) {
        this.returnDate = returnDate;
        return this;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    public Loan withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

}
