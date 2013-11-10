package org.devnull.statsd;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;
import org.devnull.statsd.models.StatsdConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.apache.log4j.*;

/**
 * Created with IntelliJ IDEA.
 * User: dhawth
 * Date: 9/30/13
 * Time: 8:42 AM
 * To change this template use File | Settings | File Templates.
 */
public class ZMQListener implements Listener, Runnable
{
	private static final Logger log = Logger.getLogger(ZMQListener.class);

	private boolean done = false;

	private final HashMap<String, Long> counters;
	private final HashMap<String, DescriptiveStatistics> timers;

	@Nullable private ZMQ.Context context = null;
	@Nullable private ZMQ.Socket socket = null;
	@Nullable private ZMQ.Poller items = null;

	public ZMQListener(@NotNull final StatsdConfig config, @NotNull final HashMap<String, Long> counters, @NotNull final HashMap<String, DescriptiveStatistics> timers)
	{
		this.timers = timers;
		this.counters = counters;

		try
		{
		context = ZMQ.context(1);
		socket = context.socket(ZMQ.PULL);
		socket.setLinger(0L);
		socket.bind(config.zmq_url);
		items = context.poller(1);
		items.register(socket, ZMQ.Poller.POLLIN);
		}
		catch (NoClassDefFoundError e)
		{
			log.fatal("No class definition found for ZMQ.  Make sure jzmq.jar is in your classpath and " +
				  "the system libraries it requires are in your LD_LIBRARY_PATH");
			System.exit(1);
		}
	}

	public void run()
	{
		String data;

		while (!done)
		{
			try
			{
				//
				// poll returns the number of items that were signaled in this call
				//
				if (0 == items.poll(1000L))
				{
					//
					// poll expired, do nothing
					//
					continue;
				}

				if (!items.pollin(0))
				{
					log.debug("poll returned non-zero, but pollin(0) was not true...");
					continue;
				}

				data = new String(socket.recv(0));

				if (log.isDebugEnabled())
				{
					log.debug("received packet: " + data);
				}

				ArrayList<String> lines = new ArrayList<String>();

				if (data.contains("\\n"))
				{
					Collections.addAll(lines, data.split("\\n"));
				}
				else
				{
					lines.add(data);
				}

				for (String line : lines)
				{
					//
					// format is version;name[:|]value|(c|ms|t)
					//

					String[] fields;

					fields = line.split(";");

					if (fields.length != 2)
					{
						continue;
					}

					if (!fields[0].equals("1"))
					{
						continue;
					}

					if (fields[1].contains(":"))
					{
						fields = fields[1].split(":");
					}
					else
					{
						fields = fields[1].split("\\|", 2);
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
					String type = fields[1];

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
