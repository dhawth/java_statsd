package org.devnull.statsd_client.models;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;

import org.devnull.statsd.JsonBase;

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
