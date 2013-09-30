package org.devnull.statsd;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.log4j.Logger;
import org.devnull.statsd.models.StatsdConfig;
import org.devnull.statsd.models.ShipperConfig;
import org.devnull.statsd.models.ZMQShipperConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;
import org.codehaus.jackson.map.ObjectMapper;

import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ZMQShipper implements Shipper
{
	private static Logger log = Logger.getLogger(ZMQShipper.class);
	@Nullable
	private StatsdConfig statsdConfig = null;
	private boolean done = false;

	@Nullable
	private LinkedBlockingQueue<Map.Entry<String, Long>> countersQueue = null;
	@Nullable
	private LinkedBlockingQueue<Map.Entry<String, DescriptiveStatistics>> timersQueue = null;

	@Nullable
	private Socket  socket  = null;

	public ZMQShipper()
	{
	}

	public void configure(@NotNull final StatsdConfig statsdConfig,
			      @NotNull final ShipperConfig c,
			      @NotNull final LinkedBlockingQueue<Map.Entry<String, Long>> countersQueue,
			      @NotNull final LinkedBlockingQueue<Map.Entry<String, DescriptiveStatistics>> timersQueue)

		throws Exception
	{
		if (null == statsdConfig)
		{
			throw new IllegalArgumentException("statsdConfig can not be null");
		}
		if (null == c)
		{
			throw new IllegalArgumentException("ShipperConfig can not be null");
		}
		if (null == countersQueue)
		{
			throw new IllegalArgumentException("counters can not be null");
		}
		if (null == timersQueue)
		{
			throw new IllegalArgumentException("timers can not be null");
		}

		this.statsdConfig = statsdConfig;
		this.countersQueue = countersQueue;
		this.timersQueue = timersQueue;

		if (null == c.configuration)
		{
			throw new IllegalArgumentException("ShipperConfig 'configuration' is null");
		}

		ObjectMapper mapper = new ObjectMapper();
		ZMQShipperConfig config = mapper.readValue(c.configuration, ZMQShipperConfig.class);

		if (null == config.zmq_url)
		{
			throw new IllegalArgumentException("zmq_url is null in shipper's configuration");
		}

		Context context = ZMQ.context(1);
		socket = context.socket(ZMQ.PUSH);
		socket.setLinger(0L);
		socket.setHWM(1L);
		socket.connect(config.zmq_url);
	}

	public void shutdown()
	{
		done = false;
	}

	public void run()
	{
		Map.Entry<String, Long> counterEntry;
		Map.Entry<String, DescriptiveStatistics> timerEntry;
		String nowSuffix;
		StringBuilder suffixBuilder = new StringBuilder(1024);
		long now;

		while (!done)
		{
			try
			{
				now = System.currentTimeMillis() / 1000;

				suffixBuilder.setLength(0);
				suffixBuilder.append(" ").append(now).append("\n");
				nowSuffix = suffixBuilder.toString();

				while ((counterEntry = countersQueue.poll(10, TimeUnit.MILLISECONDS)) != null)
				{
					//
					// ignore the return here, because we'd rather drop messages than
					// blow up memory storing old hashmaps
					//
					if (statsdConfig.suffix != null)
					{
						socket.send(("stats." + counterEntry.getKey() + "." + statsdConfig.suffix + " " +
							counterEntry.getValue() + nowSuffix).getBytes(), 0);
					}
					else
					{
						socket.send(("stats." + counterEntry.getKey() + " " + counterEntry.getValue() + nowSuffix).getBytes(), 0);
					}
				}

				while ((timerEntry = timersQueue.poll(10, TimeUnit.MILLISECONDS)) != null)
				{
					if (statsdConfig.suffix == null)
					{
						socket.send(("stats.timers." + timerEntry.getKey() + ".mean" + " " + (int)(timerEntry.getValue().getMean()) + nowSuffix).getBytes(), 0);
						socket.send(("stats.timers." + timerEntry.getKey() + ".min" + " " + (int)(timerEntry.getValue().getMin()) + nowSuffix).getBytes(), 0);
						socket.send(("stats.timers." + timerEntry.getKey() + ".max" + " " + (int)(timerEntry.getValue().getMax()) + nowSuffix).getBytes(), 0);
						socket.send(("stats.timers." + timerEntry.getKey() + ".stddev" + " " + (int)(timerEntry.getValue().getStandardDeviation()) + nowSuffix).getBytes(), 0);

						if (statsdConfig.timer_percentiles_to_calculate != null)
						{
							for (Integer i : statsdConfig.timer_percentiles_to_calculate)
							{
								socket.send(("stats.timers." + timerEntry.getKey() + "." + i + "th" + " " + (int)(timerEntry.getValue().getPercentile(i)) + nowSuffix).getBytes(), 0);
							}
						}
					}
					else
					{
						socket.send(("stats.timers." + timerEntry.getKey() + ".mean." + statsdConfig.suffix + " " + (int)(timerEntry.getValue().getMean()) + nowSuffix).getBytes(), 0);
						socket.send(("stats.timers." + timerEntry.getKey() + ".min." + statsdConfig.suffix + " " + (int)(timerEntry.getValue().getMin()) + nowSuffix).getBytes(), 0);
						socket.send(("stats.timers." + timerEntry.getKey() + ".max." + statsdConfig.suffix + " " + (int)(timerEntry.getValue().getMax()) + nowSuffix).getBytes(), 0);
						socket.send(("stats.timers." + timerEntry.getKey() + ".stddev." + statsdConfig.suffix + " " + (int)(timerEntry.getValue().getStandardDeviation()) + nowSuffix).getBytes(), 0);

						if (statsdConfig.timer_percentiles_to_calculate != null)
						{
							for (Integer i : statsdConfig.timer_percentiles_to_calculate)
							{
								socket.send(("stats.timers." + timerEntry.getKey() + "." + i + "th." + statsdConfig.suffix + " " + (int)(timerEntry.getValue().getPercentile(i)) + nowSuffix).getBytes(), 0);
							}
						}
					}
				}
			}
			catch (Exception e)
			{
				log.info("exception in socket send: " + e);
			}
		}
	}
}
