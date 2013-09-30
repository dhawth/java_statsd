package org.devnull.statsd.models;

import org.devnull.statsd.JsonBase;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

//
// This class describes the fields in the configuration files and must
// be updated if that configuration format changes.
//

final public class GraphiteShipperConfig extends JsonBase
{
	//
	// host and port of the graphite host to connect to
	//
	@Nullable
	public String graphite_host = null;
}
