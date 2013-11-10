package org.devnull.statsd;

import org.apache.commons.cli.*;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.log4j.*;
import org.devnull.statsd.models.StatsdConfig;
import org.devnull.statsd.models.ShipperConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.codehaus.jackson.map.ObjectMapper;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.BlockingQueue;
import java.io.File;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Map;

public class Statsd extends JsonBase implements Runnable
{
	@Nullable
	private static Logger log = null;

	@Nullable
	private StatsdConfig config = null;
	@Nullable
	private String log4jConfig = null;
	private boolean debug = false;
	private boolean done = false;

	@NotNull
	private ArrayList<Thread> threads = new ArrayList<Thread>();

	@NotNull
	private ArrayList<Listener> listeners = new ArrayList<Listener>();
	@NotNull
	private ArrayList<Shipper>  shippers  = new ArrayList<Shipper>();

	@Nullable
	private DataShipper dataShipper = null;

	//
	// used by DataShipper class as well
	//
	@NotNull
	protected final HashMap<String, Long> counters = new HashMap<String, Long>();
	@NotNull
	protected final HashMap<String, DescriptiveStatistics> timers = new HashMap<String, DescriptiveStatistics>();

	@NotNull
	protected ArrayList<BlockingQueue<Map.Entry<String, Long>>> countersQueueList = new ArrayList<BlockingQueue<Map.Entry<String, Long>>>();
	@NotNull
	protected ArrayList<BlockingQueue<Map.Entry<String, DescriptiveStatistics>>> timersQueueList = new ArrayList<BlockingQueue<Map.Entry<String, DescriptiveStatistics>>>();

	public static void main(String[] args) throws Exception
	{
		Statsd ic = new Statsd(args);
		Thread icThread = new Thread(ic, "Statsd");
		icThread.start();
		icThread.join();
	}

	public Statsd(String[] args) throws Exception
	{
		BasicConfigurator.configure();
		log = Logger.getLogger(Statsd.class);
		parseOptions(args);

		if (config == null)
		{
			throw new Exception("no configuration file specified (-c <path to config>)");
		}

		if (config.udp_host != null)
		{
			UDPListener udpListener = new UDPListener(config, counters, timers);
			listeners.add(udpListener);
			threads.add(new Thread(udpListener, "UDPListener"));
		}

		if (config.zmq_url != null)
		{

			ZMQListener zmqListener = new ZMQListener(config, counters, timers);
			listeners.add(zmqListener);
			threads.add(new Thread(zmqListener, "ZMQListener"));
		}

		int i = 0;

		for (ShipperConfig c : config.shippers)
		{
			BlockingQueue<Map.Entry<String, Long>> countersQueue = new LinkedBlockingQueue<Map.Entry<String, Long>>();
			BlockingQueue<Map.Entry<String, DescriptiveStatistics>> timersQueue = new LinkedBlockingQueue<Map.Entry<String, DescriptiveStatistics>>();

			countersQueueList.add(countersQueue);
			timersQueueList.add(timersQueue);

			log.debug("creating a shipper from class " + c.className);
			Shipper s = (Shipper)Class.forName(c.className).newInstance();
			s.configure(config, c, countersQueue, timersQueue);
			shippers.add(s);
			threads.add(new Thread(s, "Shipper" + i));
		}

		dataShipper = new DataShipper(config);
		threads.add(new Thread(dataShipper, "DataShipper"));
	}

	//
	// continue until shutdown() is called
	//
	public void run()
	{
		for (Thread t : threads)
		{
			log.debug("starting thread " + t.getName());
			t.start();
		}

		while (!done)
		{
			try
			{
				Thread.sleep(100000);
			}
			catch (InterruptedException ie)
			{
			}
		}

		for (Listener l : listeners)
		{
			l.shutdown();
		}

		for (Shipper s : shippers)
		{
			s.shutdown();
		}

		dataShipper.shutdown();

		for (Thread t : threads)
		{
			try
			{
				if (log.isDebugEnabled())
				{
					log.debug("joining thread " + t.getName());
				}

				t.interrupt();
				t.join(100L);
			}
			catch (InterruptedException iex)
			{
			}
		}

		//
		// end of program
		//
	}

	public void shutdown()
	{
		done = true;
	}

	private void setupLogging() throws Exception
	{
		LogManager.resetConfiguration();

		//
		// load default included in jar file
		//
		if (null == log4jConfig)
		{
			PropertyConfigurator.configure(Statsd.class.getResource("/log4j.conf"));
		}
		else
		{
			//
			// load log4j config file
			//
			PropertyConfigurator.configure(log4jConfig);
		}

		if (debug)
		{
			Logger.getRootLogger().setLevel(Level.toLevel("DEBUG"));
		}

		log = Logger.getLogger(Statsd.class);
	}

	private void parseOptions(String[] args) throws Exception
	{
		CommandLineParser parser = new GnuParser();
		CommandLine cl = parser.parse(getOptions(), args);
		Option[] options = cl.getOptions();

		for (Option option : options)
		{
			if (option.getOpt().equals("c"))
			{
				config = mapper.readValue(new File(option.getValue()), StatsdConfig.class);
			}
			else if (option.getOpt().equals("D"))
			{
				debug = true;
			}
			else if (option.getOpt().equals("l"))
			{
				log4jConfig = option.getValue();
			}
			else if (option.getOpt().equals("h"))
			{
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("java -jar Statsd.jar", getOptions());
				System.exit(0);
			}
		}

		setupLogging();
	}

	@NotNull
	Options getOptions()
	{
		Options options = new Options();
		options.addOption("c", true, "Path to statsd.conf");
		options.addOption("l", true, "Path to log4j configuration file");
		options.addOption("D", false, "Enable debug level logging");
		options.addOption("h", false, "Help");
		return options;
	}

	private class DataShipper implements Runnable
	{
		private StatsdConfig config = null;
		private boolean done = false;

		public DataShipper(@NotNull final StatsdConfig config)
		{
			this.config = config;
		}

		public void run()
		{
			log.debug("starting DataShipper");

			while (!done)
			{
				try
				{
					Thread.sleep(config.submit_interval * 1000);

					synchronized(counters)
					{
						for (Map.Entry<String, Long> e : counters.entrySet())
						{
							for (BlockingQueue<Map.Entry<String, Long>> q : countersQueueList)
							{
								q.offer(e);
							}
						}
						counters.clear();
					}

					synchronized(timers)
					{
						for (Map.Entry<String, DescriptiveStatistics> e : timers.entrySet())
						{
							for (BlockingQueue<Map.Entry<String, DescriptiveStatistics>> q : timersQueueList)
							{
								q.offer(e);
							}
						}
						timers.clear();
					}
				}
				catch (Exception e)
				{
				}
			}
		}

		public void shutdown()
		{
			done = true;
		}
	}
}
