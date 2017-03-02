
package org.folio.rest.jaxrs.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    "loans",
    "totalRecords"
})
public class Loans {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("loans")
    @Valid
    @NotNull
    private List<Loan> loans = new ArrayList<Loan>();
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("totalRecords")
    @NotNull
    private Integer totalRecords;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * 
     * (Required)
     * 
     * @return
     *     The loans
     */
    @JsonProperty("loans")
    public List<Loan> getLoans() {
        return loans;
    }

    /**
     * 
     * (Required)
     * 
     * @param loans
     *     The loans
     */
    @JsonProperty("loans")
    public void setLoans(List<Loan> loans) {
        this.loans = loans;
    }

    public Loans withLoans(List<Loan> loans) {
        this.loans = loans;
        return this;
    }

    /**
     * 
     * (Required)
     * 
     * @return
     *     The totalRecords
     */
    @JsonProperty("totalRecords")
    public Integer getTotalRecords() {
        return totalRecords;
    }

    /**
     * 
     * (Required)
     * 
     * @param totalRecords
     *     The totalRecords
     */
    @JsonProperty("totalRecords")
    public void setTotalRecords(Integer totalRecords) {
        this.totalRecords = totalRecords;
    }

    public Loans withTotalRecords(Integer totalRecords) {
        this.totalRecords = totalRecords;
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

    public Loans withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

}
