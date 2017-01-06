package dk.nykredit.bank.account.exposure.rs.model;

import dk.nykredit.bank.account.exposure.rs.EventServiceExposure;
import dk.nykredit.jackson.dataformat.hal.HALLink;
import dk.nykredit.jackson.dataformat.hal.annotation.Link;
import dk.nykredit.jackson.dataformat.hal.annotation.Resource;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.ws.rs.core.UriInfo;

/**
 * a very simple metadata representation for Events
 *
 */
@Resource
@ApiModel(value="EventsMetadata",
        description="A very simple way of delivering metadata to the consumer of a service")

public class EventsMetadataRepresentation {

    @Link
    private HALLink self;

    private String metadata;

    @ApiModelProperty(
            access = "public",
            name = "self",
            notes = "link to the reconciled transaction list.")
    public HALLink getSelf() {
        return self;
    }


    private String DEFAULT = "{\"events\": {\n" +
            "           \"description\": \"This is a very simple non-persisted edition of the metadata for events on account\","+
            "           \"purpose\": \"To show that it is easy to deliver information as part of the service and not only in the API docs\""+
            "           \"supported-versions\": \"This is only in the current initial version 1\""+
            "}}";

    /**
      * @param metadata must be formatted as valid JSON, rig now it is ignored and replaced with a static JSON document
     */
    public EventsMetadataRepresentation(String metadata, UriInfo uriInfo) {
        this.metadata = DEFAULT;
        this.self = new HALLink.Builder(uriInfo.getBaseUriBuilder()
                .path(EventServiceExposure.class)
                .build())
                .build();
    }

    @ApiModelProperty(
            access = "public",
            name = "metadata",
            notes = "json formatted description of relevant metadata",
            value = "Read-only")
    public String getMetadata() {
        return metadata;
    }

}