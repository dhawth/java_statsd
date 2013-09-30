package org.devnull.statsd_client.models;

import org.devnull.statsd_client.JsonBase;

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

final public class ZMQStatsdClientConfig extends JsonBase
{
        private static final ObjectMapper mapper = new ObjectMapper();

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
