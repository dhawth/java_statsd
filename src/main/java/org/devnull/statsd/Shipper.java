package org.devnull.statsd;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.devnull.statsd.models.StatsdConfig;
import org.devnull.statsd.models.ShipperConfig;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.Map;

public interface Shipper extends Runnable
{
	public void configure(final StatsdConfig statsdConfig,
			      final ShipperConfig config,
			      final LinkedBlockingQueue<Map.Entry<String, Long>> counters,
			      final LinkedBlockingQueue<Map.Entry<String, DescriptiveStatistics>> timers)
		throws Exception;

	public void shutdown();
}
