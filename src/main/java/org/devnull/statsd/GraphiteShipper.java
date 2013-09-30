package org.devnull.statsd;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.log4j.Logger;
import org.devnull.statsd.models.GraphiteShipperConfig;
import org.devnull.statsd.models.StatsdConfig;
import org.devnull.statsd.models.ShipperConfig;
import org.jetbrains.annotations.NotNull;
import org.codehaus.jackson.map.ObjectMapper;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GraphiteShipper implements Shipper
{
	private static Logger log = Logger.getLogger(GraphiteShipper.class);
	@Nullable
	private StatsdConfig statsdConfig = null;
	private boolean done = false;

	@Nullable
	private LinkedBlockingQueue<Map.Entry<String, Long>> countersQueue = null;
	@Nullable
	private LinkedBlockingQueue<Map.Entry<String, DescriptiveStatistics>> timersQueue = null;

	@Nullable
	private SocketAddress sockAddr = null;
	@Nullable
	private Socket socket = null;

	public GraphiteShipper()
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
		if (null == c.configuration)
		{
			throw new IllegalArgumentException("ShipperConfig 'configuration' is null");
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

		ObjectMapper mapper = new ObjectMapper();
		GraphiteShipperConfig graphiteConfig = mapper.readValue(c.configuration, GraphiteShipperConfig.class);

		if (null == graphiteConfig.graphite_host)
		{
			throw new IllegalArgumentException("graphite_host is null in shipper's configuration");
		}

		String[] fields = graphiteConfig.graphite_host.split(":");

		if (fields.length != 2)
		{
			throw new IllegalArgumentException("too few fields in graphite_host, expected host:port");
		}

		InetAddress addr = InetAddress.getByName(fields[0]);
		sockAddr = new InetSocketAddress(addr, Integer.valueOf(fields[1]));
		socket = new Socket();
	}

	public void shutdown()
	{
		done = true;
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
				if (countersQueue.isEmpty() && timersQueue.isEmpty())
				{
					Thread.sleep(100);
					continue;
				}

				now = System.currentTimeMillis() / 1000;

				suffixBuilder.setLength(0);
				suffixBuilder.append(" ").append(now).append("\n");
				nowSuffix = suffixBuilder.toString();

				//
				// 2 second timeout
				//
				socket.connect(sockAddr, 2000);

				DataOutputStream outToServer = new DataOutputStream(socket.getOutputStream());

				while ((counterEntry = countersQueue.poll(10, TimeUnit.MILLISECONDS)) != null)
				{
					if (statsdConfig.suffix == null)
					{
						outToServer.writeBytes("stats." + counterEntry.getKey() + " " + counterEntry.getValue() + nowSuffix);
					}
					else
					{
						outToServer.writeBytes("stats." + counterEntry.getKey() + "." + statsdConfig.suffix + " " + counterEntry.getValue() + nowSuffix);
					}
				}

				while ((timerEntry = timersQueue.poll(10, TimeUnit.MILLISECONDS)) != null)
				{
					if (statsdConfig.suffix == null)
					{
						outToServer.writeBytes("stats.timers." + timerEntry.getKey() + ".mean" + " " + (int)(timerEntry.getValue().getMean()) + nowSuffix);
						outToServer.writeBytes("stats.timers." + timerEntry.getKey() + ".min" + " " + (int)(timerEntry.getValue().getMin()) + nowSuffix);
						outToServer.writeBytes("stats.timers." + timerEntry.getKey() + ".max" + " " + (int)(timerEntry.getValue().getMax()) + nowSuffix);
						outToServer.writeBytes("stats.timers." + timerEntry.getKey() + ".stddev" + " " + (int)(timerEntry.getValue().getStandardDeviation()) + nowSuffix);

						if (statsdConfig.timer_percentiles_to_calculate != null)
						{
							for (Integer i : statsdConfig.timer_percentiles_to_calculate)
							{
								outToServer.writeBytes("stats.timers." + timerEntry.getKey() + ".90th" + " " +
									(int)(timerEntry.getValue().getPercentile(i)) + nowSuffix);
							}
						}
					}
					else
					{
						outToServer.writeBytes("stats.timers." + timerEntry.getKey() + ".mean." + statsdConfig.suffix + " " + (int)(timerEntry.getValue().getMean()) + nowSuffix);
						outToServer.writeBytes("stats.timers." + timerEntry.getKey() + ".min." + statsdConfig.suffix + " " + (int)(timerEntry.getValue().getMin()) + nowSuffix);
						outToServer.writeBytes("stats.timers." + timerEntry.getKey() + ".max." + statsdConfig.suffix + " " + (int)(timerEntry.getValue().getMax()) + nowSuffix);
						outToServer.writeBytes("stats.timers." + timerEntry.getKey() + ".stddev." + statsdConfig.suffix + " " + (int)(timerEntry.getValue().getStandardDeviation()) + nowSuffix);

						if (statsdConfig.timer_percentiles_to_calculate != null)
						{
							for (Integer i : statsdConfig.timer_percentiles_to_calculate)
							{
								outToServer.writeBytes("stats.timers." + timerEntry.getKey() + ".90th." + statsdConfig.suffix + " " +
									(int)(timerEntry.getValue().getPercentile(i)) + nowSuffix);
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
}
