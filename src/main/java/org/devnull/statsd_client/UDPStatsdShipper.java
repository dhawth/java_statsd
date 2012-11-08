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

package org.devnull.statsd_client;

//
// this class ships counters to statsd from the StatsObject instance every $period seconds
//

import org.apache.log4j.*;

import java.util.HashMap;
import java.net.UnknownHostException;
import java.net.SocketException;

import org.devnull.statsd_client.models.UDPStatsdClientConfig;

final public class UDPStatsdShipper implements Runnable
{
	private static Logger log 	= Logger.getLogger(UDPStatsdShipper.class);
	private StatsObject so 		= null;
	private UDPStatsdClient client 	= null;
	private UDPStatsdClientConfig config 	= null;

	public UDPStatsdShipper()
	{
	}

	public UDPStatsdShipper(final UDPStatsdClientConfig c)
		throws IllegalArgumentException, UnknownHostException, SocketException
	{
		configure(c);
	}

	public void configure(final UDPStatsdClientConfig c)
		throws IllegalArgumentException, UnknownHostException, SocketException
	{
		if (null == c) {
			throw new IllegalArgumentException("config argument is null");
		}
		if (null == c.hostname || c.hostname.isEmpty()) {
			throw new IllegalArgumentException("statsd_config hostname is missing or is empty");
		}
		if (null == c.port || c.port < 1) {
			throw new IllegalArgumentException("statsd_config port is missing or is < 1");
		}
		if (null == c.period || c.period < 1) {
			throw new IllegalArgumentException("statsd_config period is missing or is < 1 second");
		}
		if (null == c.prepend_strings || c.prepend_strings.isEmpty()) {
			throw new IllegalArgumentException("statsd_config prepend_string is missing or is empty");
		}

		log 	= Logger.getLogger(UDPStatsdShipper.class);
		config 	= c;
		so 	= StatsObject.getInstance();
		client	= new UDPStatsdClient(c.hostname, c.port);
		so.registerUDPStatsdClient(client);
	}

	//
	// this assumes that timers are all sent as they occur
	// from within the StatsObject class as a result of registering the
	// UDPStatsdClient object with the StatsObject class.
	// So the only stats we need to box up and ship out are the counters.
	//
	public void run()
	{
		while (true)
		{
			try
			{
				Thread.sleep(config.period);

				HashMap<String, Long> stats_map = so.getMapAndClear();

				if (null == stats_map || stats_map.isEmpty())
				{
					continue;
				}

				if (log.isDebugEnabled())
				{
					log.debug("shipping " + stats_map.size() + " stats to statsd");
				}

				for (String key : stats_map.keySet())
				{
					//
					// prepend the keys with the prepend value from the config
					//
					for (String prepend : config.prepend_strings)
					{
						String real_key = prepend + "." + key;

						//
						// a little fixup: replace spaces with underscores to help out the
						// destination names from the config
						//
						real_key = real_key.replaceAll(" ", "_");
	
						//
						// ship off to statsd
						//
						client.increment(real_key, stats_map.get(key).intValue());
					}
				}
			}
			catch (Exception e)
			{
				log.warn(e);
			}
		}
	}
}
