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

package org.devnull;

import org.devnull.statsd.models.*;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.devnull.statsd.*;
import org.devnull.statsd_client.models.*;
import org.devnull.statsd_client.*;
import java.util.*;

import org.testng.annotations.*;
import static org.testng.AssertJUnit.*;

//
// goal:
//	create mock graphite object
//	create mock zmq object
//	craft statsd server config
//	create statsd server with graphite output and zmq output
//	craft udp statsd client config
//	create udp statsd shipper
//	ship stats and timers with udp statsd shipper
//	confirm stats read by graphite and zmq objects are equal
//	confirm stats read by mock graphite object are as expected
//	confirm stats read my mock zmq object are as expected
//

public class TestUDPStatsdShipper
{
	@Test
	public void testShipper() throws Exception
	{
		//
		// put together config for statsd server
		//
		StatsdConfig config = new StatsdConfig();

		config.suffix = "sjc1.testnode";		// host testnode.sjc1.devnull.org
		config.submit_interval = 1;			// 1 second

		//
		// where to listen
		//
		config.udp_host = "127.0.0.1";
		config.udp_port = 8125;
		config.zmq_url = "tcp://127.0.0.1:8126";

		//
		// where to ship data to (our Mock listeners)
		//
		config.shippers = new ArrayList<ShipperConfig>();

		ObjectMapper mapper = new ObjectMapper();

		ShipperConfig c1 = new ShipperConfig();
		c1.className = "org.devnull.statsd.GraphiteShipper";
		c1.config = mapper.readTree("{\"graphite_host\":\"127.0.0.1:12003\"}");
		config.shippers.add(c1);

		ShipperConfig c2 = new ShipperConfig();
		c2.className = "org.devnull.statsd.ZMQShipper";
		c2.config = mapper.readTree("{\"zmq_url\":\"tcp://127.0.0.1:12004\"}");
		config.shippers.add(c2);
	}
}
