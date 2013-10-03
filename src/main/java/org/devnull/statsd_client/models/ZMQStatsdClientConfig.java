package org.devnull.statsd_client.models;

import org.codehaus.jackson.map.ObjectMapper;
import org.devnull.statsd_client.JsonBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

final public class ZMQStatsdClientConfig extends JsonBase
{
	//
	// url of the zmq endpoint to connect to and send data to
	//
	@Nullable
	public String zmq_url = null;

	//
	// how often in seconds to ship data to statsd
	// default: 10 seconds
	//
	@NotNull
	public Integer period = 10;

	//
	// what prepend strings to use, e.g.
	// 	orgName.appType.appName
	//	3crowd.stats_system.CassandraWebAPI
	//
	@NotNull
	public List<String> prepend_strings = new LinkedList<String>();
}
