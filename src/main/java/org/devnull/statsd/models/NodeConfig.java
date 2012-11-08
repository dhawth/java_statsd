/*
 *
 *  * Copyright 2012 David Hawthorne, 3Crowd/XDN, Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package org.devnull.statsd.models;

import com.google.gson.annotations.SerializedName;
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
import java.util.ArrayList;
import java.util.Arrays;

//
// This class describes the fields in the configuration files and must
// be updated if that configuration format changes.
//

final public class NodeConfig
{
	private static final ObjectMapper mapper = new ObjectMapper();

	//
	// a suffix to be used on all stats
	//
	@SerializedName("suffix")
	public String suffix = "sjc1.testnode";

	//
	// how long in seconds between stats submissions
	//
	@SerializedName("submit_interval")
	public Integer submit_interval = 10;

	@SerializedName("udp_host")
	public String udp_host = "127.0.0.1";

	@SerializedName("udp_port")
	public Integer udp_port = 8125;

	//
	// zmq url to bind to
	//
	@SerializedName("zmq_url")
	public String zmq_url = "tcp://127.0.0.1:8765";

	//
	// list of places to ship things to, entirely dynamic
	//
	@SerializedName("shippers")
	public List<ShipperConfig> shippers;

	//
	// when it comes to calculating the percentile values of timers,
	// normally statsd will only calculate the 90th %.
	// this can be changed here:
	//
	@SerializedName("timer_percentiles_to_calculate")
	public List<Integer> timer_percentiles_to_calculate = new ArrayList<Integer>(Arrays.asList(90));

	public String toString()
	{
		synchronized(mapper)
		{
			try
			{
				return mapper.writeValueAsString(this);
			}
			catch (IOException e)
			{
				return "unable to write value as string: " + e.getMessage();
			}
		}
	}
}
