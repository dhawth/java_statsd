package org.devnull.statsd_client;

//
// this class ships stats to statsd from the StatsObject instance every $period seconds
//	getMapAndClear();
// this class also ships the timers from the StatsObject instance every $period seconds
//	getTimersAndClear();
//

import org.apache.log4j.*;

import java.util.*;

import java.io.StringWriter;
import java.io.PrintWriter;

import org.devnull.statsd_client.models.ZMQStatsdClientConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final public class ZMQStatsdShipper implements Runnable
{
	private static Logger log 	= Logger.getLogger(ZMQStatsdShipper.class);

	private boolean done = false;

	@NotNull private StatsObject so 	= StatsObject.getInstance();
	@NotNull private StringBuilder sb	= new StringBuilder(65536);

	private ZMQStatsdClientConfig config = null;
	private ZMQStatsdClient client = null;

	public ZMQStatsdShipper()
	{
	}

	public ZMQStatsdShipper(@NotNull final ZMQStatsdClientConfig c)
		throws IllegalArgumentException
	{
		this.config = c;
		configure(c);
	}

	public void configure(final ZMQStatsdClientConfig c)
		throws IllegalArgumentException
	{
		if (null == config.zmq_url || config.zmq_url.isEmpty())
		{
			throw new IllegalArgumentException("zmq_url is missing or is 0 length");
		}
		if (null == config.period || config.period < 1)
		{
			throw new IllegalArgumentException("period is missing or is < 1");
		}
		if (null == config.prepend_strings || config.prepend_strings.isEmpty())
		{
			throw new IllegalArgumentException("prepend_strings is missing or is empty");
		}

		client	= new ZMQStatsdClient(config.zmq_url);
	}

	public void shutdown()
	{
		done = true;
	}

	public void run()
	{
		while (!done)
		{
			try
			{
				Thread.sleep(config.period);

				HashMap<String, Long> stats_map = so.getMapAndClear();

				if (null != stats_map && !stats_map.isEmpty())
				{
					if (log.isDebugEnabled())
					{
						log.debug("shipping " + stats_map.size() + " stats to statsd");
					}
	
					for (String key : stats_map.keySet())
					{
						for (String p : config.prepend_strings)
						{
							//
							// format: 1;key:values|c
							//
							sb.setLength(0);
							sb.append(1).append(";");
							sb.append(p).append(".").append(key).append(":").append(stats_map.get(key)).append("|").append("c").append("\n");
							client.send(sb.toString());
						}
					}
				}

				HashMap<String, String> timer_map = so.getTimersAndClear();

				if (null != timer_map && !timer_map.isEmpty())
				{
					if (log.isDebugEnabled())
					{
						log.debug("shipping " + timer_map.size() + " timers to statsd");
					}
	
					for (String key : timer_map.keySet())
					{
						for (String p : config.prepend_strings)
						{
							//
							// format: 1;key:values|t
							//
							sb.setLength(0);
							sb.append(1).append(";");
							sb.append(p).append(".").append(key).append(":").append(timer_map.get(key)).append("|").append("t").append("\n");
							client.send(sb.toString());
						}
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
