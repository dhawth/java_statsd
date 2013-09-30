package org.devnull.statsd.models;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.annotate.JsonProperty;

import org.devnull.statsd.JsonBase;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

//
// This class describes the fields in the configuration files and must
// be updated if that configuration format changes.
//

final public class ShipperConfig extends JsonBase
{
	//
	// name of the class of the shipper, e.g. org.devnull.statsd.GraphiteShipper
	//
	@Nullable
	@JsonProperty("class")
	public String className = null;

	//
	// serialized json string of teh configuration for the instantiated shipper,
	// e.g. ..., "configuration" : "{\"graphite_host\":\"localhost:2003\"}", ...
	//
	@Nullable
	public JsonNode config = null;
}
