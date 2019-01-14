package org.devnull.statsd;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.devnull.statsd.models.StatsdConfig;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import org.apache.log4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created with IntelliJ IDEA.
 * User: dhawth
 * Date: 9/30/13
 * Time: 8:41 AM
 * To change this template use File | Settings | File Templates.
 */
public class UDPListener implements Listener, Runnable
{
	private static final Logger log = Logger.getLogger(UDPListener.class);

	private boolean done = false;

	private DatagramSocket socket = null;

	private final HashMap<String, Long> counters;
	private final HashMap<String, DescriptiveStatistics> timers;

	public UDPListener(@NotNull final StatsdConfig config, @NotNull final HashMap<String, Long> counters, @NotNull final HashMap<String, DescriptiveStatistics> timers)
		throws Exception
	{
		this.counters = counters;
		this.timers = timers;
		InetSocketAddress sockAddr = new InetSocketAddress(config.udp_host, config.udp_port);
		socket   = new DatagramSocket(sockAddr);
	}

	public void run()
	{
		byte[] receiveBuff = new byte[1500];

		while (!done)
		{
			try
			{
				Arrays.fill(receiveBuff, (byte)0);
				DatagramPacket p = new DatagramPacket(receiveBuff, receiveBuff.length);
				socket.receive(p);
				String data = new String(p.getData(), 0, p.getLength());

				if (log.isDebugEnabled())
				{
					log.debug("received packet: " + data);
				}

				ArrayList<String> lines = new ArrayList<String>();

				if (data.contains("\n"))
				{
					Collections.addAll(lines, data.split("\\n"));
				}
				else
				{
					lines.add(data);
				}

				for (String line : lines)
				{
					String[] fields;

					//
					// format is name[:|]value|(c|ms|t)
					//
					if (line.contains(":"))
					{
						fields = line.split(":");
					}
					else
					{
						fields = line.split("\\|", 2);
					}

					if (fields.length != 2)
					{
						continue;
					}

					String name = fields[0];
					fields = fields[1].split("\\|");

					if (fields.length < 2)
					{
						continue;
					}

					String value = fields[0];
					String type  = fields[1];

					if (type.equals("ms") || type.equals("t"))
					{
						if (fields.length > 2)
						{
							continue;
						}

						//
						// update timer
						//
						synchronized (timers)
						{
							if (!timers.containsKey(name))
							{
								timers.put(name, new DescriptiveStatistics());
							}

							if (value.contains(","))
							{
								for (String v : value.split(","))
								{
									timers.get(name).addValue(Double.valueOf(v));
								}
							}
							else
							{
								timers.get(name).addValue(Double.valueOf(value));
							}
						}
					}
					else if (type.equals("c"))
					{
						double v = Double.valueOf(value);

						if (fields.length > 2)
						{
							fields[2] = fields[2].replace("@", "");
							v *= Double.valueOf(fields[2]);
						}

						synchronized (counters)
						{
							if (!counters.containsKey(name))
							{
								counters.put(name, 0L);
							}

							counters.put(name, counters.get(name) + (long)v);
						}
					}
					else
					{
						log.debug("unknown type: " + type);
					}
				}
			}
			catch (Exception e)
			{
				log.info("exception in socket receive: " + e);
			}
		}
	}

	public void shutdown()
	{
		done = true;
	}
}
