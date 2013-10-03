package org.devnull.statsd_client.models;

import org.devnull.statsd.JsonBase;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

final public class UDPStatsdClientConfig extends JsonBase
{
	//
	// hostname and port of the UDP-based statsd server to talk to
	//
	@NotNull
	public String hostname = "localhost";

	@NotNull
	public Integer port = 8125;

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
