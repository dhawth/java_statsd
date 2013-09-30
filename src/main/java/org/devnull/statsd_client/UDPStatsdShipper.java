package org.devnull.statsd_client;

//
// this class ships counters to statsd from the StatsObject instance every $period seconds
//

import org.apache.log4j.*;

import java.util.HashMap;
import java.net.UnknownHostException;
import java.net.SocketException;

import org.devnull.statsd_client.models.UDPStatsdClientConfig;
import org.jetbrains.annotations.Nullable;

final public class UDPStatsdShipper implements Runnable
{
	private static Logger log 	= Logger.getLogger(UDPStatsdShipper.class);

	private boolean done = false;

	@Nullable private StatsObject so 		= null;
	@Nullable private UDPStatsdClient client 	= null;
	@Nullable private UDPStatsdClientConfig config 	= null;

	public UDPStatsdShipper()
	{
	}

	public UDPStatsdShipper(final UDPStatsdClientConfig c)
		throws IllegalArgumentException, UnknownHostException, SocketException
	{
		configure(c);
	}

	public void configure(@Nullable final UDPStatsdClientConfig c)
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

	private void shutdown()
	{
		done = true;
	}
	//
	// this assumes that timers are all sent as they occur
	// from within the StatsObject class as a result of registering the
	// UDPStatsdClient object with the StatsObject class.
	// So the only stats we need to box up and ship out are the counters.
	//
	public void run()
	{
		while (!done)
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
