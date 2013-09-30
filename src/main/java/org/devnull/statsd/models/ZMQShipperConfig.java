package org.devnull.statsd.models;

import java.io.IOException;
import org.devnull.statsd.JsonBase;
import org.jetbrains.annotations.Nullable;

//
// This class describes the fields in the configuration files and must
// be updated if that configuration format changes.
//

final public class ZMQShipperConfig extends JsonBase
{
	//
	// url of the zmq endpoint to connect to and send data to
	//
	@Nullable
	public String zmq_url = null;
}
