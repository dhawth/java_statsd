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

package org.devnull.statsd;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;

import com.google.gson.*;
import org.apache.commons.cli.*;
import org.apache.log4j.*;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jetbrains.annotations.NotNull;

//
// configuration models
//
import org.devnull.statsd.models.*;

public class GraphiteShipper implements Shipper
{
	private static Logger log = Logger.getLogger(GraphiteShipper.class);
	private HashMap<String, Long> counters = null;
	private HashMap<String, DescriptiveStatistics> timers = null;
	private long now = 0;

	private NodeConfig nodeConfig = null;
	private GraphiteShipperConfig config = null;
	private InetAddress addr = null;
	private SocketAddress sockAddr = null;
	private Socket socket = null;

	public GraphiteShipper()
	{
	}

	public void configure(@NotNull final NodeConfig nodeConfig,
			      @NotNull final ShipperConfig c,
			      final long now,
			      @NotNull final HashMap<String, Long> counters,
			      @NotNull final HashMap<String, DescriptiveStatistics> timers)

		throws Exception
	{
		if (null == nodeConfig)
		{
			throw new IllegalArgumentException("nodeConfig can not be null");
		}
		if (null == c)
		{
			throw new IllegalArgumentException("ShipperConfig can not be null");
		}
		if (null == c.configuration)
		{
			throw new IllegalArgumentException("ShipperConfig 'configuration' is null");
		}
		if (null == counters)
		{
			throw new IllegalArgumentException("counters can not be null");
		}
		if (null == timers)
		{
			throw new IllegalArgumentException("timers can not be null");
		}

		this.nodeConfig = nodeConfig;
		this.counters = counters;
		this.timers = timers;
		this.now = now;

                Gson gson = new Gson();
                config = gson.fromJson(c.configuration, GraphiteShipperConfig.class);

		if (null == config.graphite_host)
		{
			throw new IllegalArgumentException("graphite_host is null in shipper's configuration");
		}

		String[] fields = config.graphite_host.split(":");

		if (fields.length != 2)
		{	
			throw new IllegalArgumentException("too few fields in graphite_host, expected host:port");
		}

		addr = InetAddress.getByName(fields[0]);
		sockAddr = new InetSocketAddress(addr, Integer.valueOf(fields[1]));
		socket = new Socket();
	}

	public void run()
	{
		try
		{
			//
			// 2 second timeout
			//
			socket.connect(sockAddr, 2000);
			
			DataOutputStream outToServer = new DataOutputStream(socket.getOutputStream());

			String nowSuffix = " " + now + "\n";
	
			for (String name : counters.keySet())
			{
				if (nodeConfig.suffix == null)
				{
					outToServer.writeBytes("stats." + name + " " + counters.get(name) + nowSuffix);
				}
				else
				{
					outToServer.writeBytes("stats." + name + "." + nodeConfig.suffix + " " + counters.get(name) + nowSuffix);
				}
			}
	
			for (String name : timers.keySet())
			{
				if (nodeConfig.suffix == null)
				{
					outToServer.writeBytes("stats.timers." + name + ".mean" + " " + (int)(timers.get(name).getMean()) + nowSuffix);
					outToServer.writeBytes("stats.timers." + name + ".min" + " " + (int)(timers.get(name).getMin()) + nowSuffix);
					outToServer.writeBytes("stats.timers." + name + ".max" + " " + (int)(timers.get(name).getMax()) + nowSuffix);
					outToServer.writeBytes("stats.timers." + name + ".stddev" + " " + (int)(timers.get(name).getStandardDeviation()) + nowSuffix);

                                        if (nodeConfig.timer_percentiles_to_calculate != null)
                                        {
                                                for (Integer i : nodeConfig.timer_percentiles_to_calculate)
                                                {
							outToServer.writeBytes("stats.timers." + name + ".90th" + " " + 
								(int)(timers.get(name).getPercentile(i)) + nowSuffix);
						}
					}
				}
				else
				{
					outToServer.writeBytes("stats.timers." + name + ".mean." + nodeConfig.suffix + " " + (int)(timers.get(name).getMean()) + nowSuffix);
					outToServer.writeBytes("stats.timers." + name + ".min." + nodeConfig.suffix + " " + (int)(timers.get(name).getMin()) + nowSuffix);
					outToServer.writeBytes("stats.timers." + name + ".max." + nodeConfig.suffix + " " + (int)(timers.get(name).getMax()) + nowSuffix);
					outToServer.writeBytes("stats.timers." + name + ".stddev." + nodeConfig.suffix + " " + (int)(timers.get(name).getStandardDeviation()) + nowSuffix);

                                        if (nodeConfig.timer_percentiles_to_calculate != null)
                                        {
                                                for (Integer i : nodeConfig.timer_percentiles_to_calculate)
                                                {
							outToServer.writeBytes("stats.timers." + name + ".90th." + nodeConfig.suffix + " " + 
								(int)(timers.get(name).getPercentile(i)) + nowSuffix);
						}
					}
				}
			}
	
			socket.close();
		}
		catch (Exception e)
		{
			log.info("exception in socket send: " + e);
		}
	}
}
