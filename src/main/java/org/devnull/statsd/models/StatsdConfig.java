package org.devnull.statsd.models;

import org.codehaus.jackson.map.ObjectMapper;
import org.devnull.statsd.JsonBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//
// This class describes the fields in the configuration files and must
// be updated if that configuration format changes.
//

final public class StatsdConfig extends JsonBase
{
	//
	// a suffix to be used on all stats, e.g. sjc1.hostname
	// useful for globbing and grouping by datacenter, rack, etc
	//
	@Nullable
	public String suffix = null;

	//
	// how long in seconds between stats submissions
	//
	@NotNull
	public Integer submit_interval = 10;

	//
	// if udp_host is null, UDPListener will not be started
	//
	@Nullable
	public String  udp_host = "127.0.0.1";
	@NotNull
	public Integer udp_port = 8125;

	//
	// if null, ZMQListener will not be started.
	// zmq url to bind to.
	// e.g., "tcp://127.0.0.1:8765"
	//
	@Nullable
	public String zmq_url = null;

	//
	// list of places to ship things to, entirely dynamic
	//
	@Nullable
	public List<ShipperConfig> shippers = null;

	//
	// when it comes to calculating the percentile values of timers,
	// normally statsd will only calculate the 90th %.
	// this can be changed here:
	//
	@NotNull
	public List<Integer> timer_percentiles_to_calculate = new ArrayList<Integer>(Arrays.asList(90));
}
