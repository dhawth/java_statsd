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
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

//
// configuration models
//
import org.devnull.statsd.models.*;

public class ZMQShipper implements Shipper
{
	private static Logger log = Logger.getLogger(ZMQShipper.class);
	private HashMap<String, Long> counters = null;
	private HashMap<String, DescriptiveStatistics> timers = null;
	private long now = 0;

	private NodeConfig nodeConfig = null;
	private ZMQShipperConfig config = null;
        private Context context = null;
        private Socket  socket  = null;
        private Poller  items = null;

	public ZMQShipper()
	{
		context = ZMQ.context(1);
		socket = context.socket(ZMQ.PUSH);
		socket.setLinger(0L);
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

		if (null == c.configuration)
		{
			throw new IllegalArgumentException("ShipperConfig 'configuration' is null");
		}

                Gson gson = new Gson();
                config = gson.fromJson(c.configuration, ZMQShipperConfig.class);

		if (null == config.zmq_url)
		{
			throw new IllegalArgumentException("zmq_url is null in shipper's configuration");
		}

                socket.setLinger(0);
                socket.setHWM(1L);
                socket.connect(config.zmq_url);
	}

	public void run()
	{
		try
		{
			String nowSuffix = " " + now + "\n";

			for (String name : counters.keySet())
			{
				//
				// ignore the return here, because we'd rather drop messages than
				// blow up memory storing old hashmaps
				//
				if (nodeConfig.suffix != null)
				{
					socket.send(("stats." + name + "." + nodeConfig.suffix + " " + counters.get(
						name) + nowSuffix).getBytes(), 0);
				}
				else
				{
					socket.send(("stats." + name + " " + counters.get(name) + nowSuffix).getBytes(), 0);
				}
			}
	
			for (String name : timers.keySet())
			{
				if (nodeConfig.suffix == null)
				{
					socket.send(("stats.timers." + name + ".mean" + " " + (int) (timers.get(
						name).getMean()) + nowSuffix).getBytes(), 0);
					socket.send(("stats.timers." + name + ".min" + " " + (int) (timers.get(
						name).getMin()) + nowSuffix).getBytes(), 0);
					socket.send(("stats.timers." + name + ".max" + " " + (int) (timers.get(
						name).getMax()) + nowSuffix).getBytes(), 0);
					socket.send(("stats.timers." + name + ".stddev" + " " + (int) (timers.get(
						name).getStandardDeviation()) + nowSuffix).getBytes(), 0);

					if (nodeConfig.timer_percentiles_to_calculate != null)
					{
						for (Integer i : nodeConfig.timer_percentiles_to_calculate)
						{
							socket.send(("stats.timers." + name + "." + i + "th" + " " + (int) (timers.get(
								name).getPercentile(i)) + nowSuffix).getBytes(), 0);
						}
					}
				}
				else
				{
					socket.send(("stats.timers." + name + ".mean." + nodeConfig.suffix + " " + (int) (timers.get(
						name).getMean()) + nowSuffix).getBytes(), 0);
					socket.send(("stats.timers." + name + ".min." + nodeConfig.suffix + " " + (int) (timers.get(
						name).getMin()) + nowSuffix).getBytes(), 0);
					socket.send(("stats.timers." + name + ".max." + nodeConfig.suffix + " " + (int) (timers.get(
						name).getMax()) + nowSuffix).getBytes(), 0);
					socket.send(("stats.timers." + name + ".stddev." + nodeConfig.suffix + " " + (int) (timers.get(
						name).getStandardDeviation()) + nowSuffix).getBytes(), 0);

					if (nodeConfig.timer_percentiles_to_calculate != null)
					{
						for (Integer i : nodeConfig.timer_percentiles_to_calculate)
						{
							socket.send(("stats.timers." + name + "." + i + "th." + nodeConfig.suffix + " " + (int) (timers.get(
								name).getPercentile(i)) + nowSuffix).getBytes(), 0);
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			log.info("exception in socket send: " + e);
		}
		finally
		{
			socket.close();
		}
	}
}
