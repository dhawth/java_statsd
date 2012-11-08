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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import org.apache.log4j.*;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

//
// configuration models
//
import org.devnull.statsd.models.*;

public class Statsd
{
	private static Logger log = null;

	private String configFile = "/etc/statsd/statsd.conf";
	private String log4jConfig = "/etc/statsd/statsd.log4j.conf";
	private boolean debug = false;
	private NodeConfig config = null;
	private ArrayList<Thread> threads = new ArrayList<Thread>();
	private HashMap<String, Long> counters = new HashMap<String, Long>();
	private HashMap<String, DescriptiveStatistics> timers = new HashMap<String, DescriptiveStatistics>();
	private final Object countersLock = new Object();
	private final Object timersLock = new Object();

	public static void main(String[] args) throws Exception
	{
		log = Logger.getLogger(Statsd.class);
		BasicConfigurator.configure();

		Statsd ic = new Statsd();

		try
		{
			ic.doMain(args);
		}
		catch (Exception e)
		{
			log.error("Error", e);
			e.printStackTrace();
			throw new Exception(e);
		}
	}

	private void doMain(String[] args) throws Exception
	{
		if (!parseOptions(args))
		{
			return;
		}

		setupLogging();

		config = ConfigReaders.ReadNodeConfig(configFile);

		checkConfig(config);

		//
		// instantiate and configure the listeners
		//
		UDPListener udpListener = new UDPListener();
		ZMQListener zmqListener = new ZMQListener();

		threads.add(new Thread(udpListener, "UDPListener"));
		threads.add(new Thread(zmqListener, "ZMQListener"));

		//
		// instantiate and configure the shipper manager
		//
		DataShipper dataShipper = new DataShipper(config);

		threads.add(new Thread(dataShipper, "DataShipper"));

		for (Thread t : threads)
		{
			t.start();
		}

		//
		// program runs continuously
		//
		while (true)
		{
			try
			{
				Thread.sleep(100000);
			}
			catch (InterruptedException ie)
			{
			}
		}
		
		//
		// end of program
		//
	}

	private void checkConfig(final NodeConfig config)
	{
		if (null == config)
		{
			throw new IllegalArgumentException("config is null");
		}
	}

	private void setupLogging()
	{
		LogManager.resetConfiguration();

		File log4jFile = new File(log4jConfig);

		if (!log4jFile.exists())
		{
			throw new IllegalArgumentException("log4j config file does not exist: " + log4jConfig);
		}

		//
		// load log4j config file
		//
		PropertyConfigurator.configure(log4jConfig);

		if (debug)
		{
			Logger.getRootLogger().setLevel(Level.toLevel("DEBUG"));
		}

		log = Logger.getLogger(Statsd.class);
	}

	private boolean parseOptions(String[] args) throws Exception
	{
		CommandLineParser parser = new GnuParser();
		CommandLine cl = parser.parse(getOptions(), args);
		Option[] options = cl.getOptions();

		for (Option option : options)
		{
			if (option.getOpt().equals("c"))
			{
				File tmp = new File(option.getValue());
				if (!tmp.exists())
				{
					throw new RuntimeException("Path to config file is invalid: " + tmp);
				}
				configFile = option.getValue();
			}
			else if (option.getOpt().equals("D"))
			{
				debug = true;
			}
			else if (option.getOpt().equals("l"))
			{
				File tmp = new File(option.getValue());
				if (!tmp.exists())
				{
					throw new RuntimeException("Path to log4j config file is invalid: " + tmp);
				}
				log4jConfig = option.getValue();
			}
			else if (option.getOpt().equals("h"))
			{
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("java -jar Statsd.jar", getOptions());
				return false;
			}
		}
		return true;
	}

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
		private ExecutorService pool = null;
		private NodeConfig config = null;
		private boolean done = false;

		public DataShipper(final NodeConfig config)
		{
			this.config = config;
			pool = Executors.newFixedThreadPool(config.shippers.size());
		}

		public void run()
		{
			while (!done)
			{
				try
				{
					Thread.sleep(config.submit_interval * 1000);

					HashMap<String, Long> myCounters = null;
					HashMap<String, DescriptiveStatistics> myTimers = null;

					synchronized(countersLock)
					{
						synchronized(timersLock)
						{
							myCounters = counters;
							myTimers = timers;
							counters = new HashMap<String, Long>();
							timers = new HashMap<String, DescriptiveStatistics>();
						}
					}

					long now = System.currentTimeMillis() / 1000;

					for (ShipperConfig c : config.shippers)
					{
						try
						{
							Shipper s = (Shipper)Class.forName(c.className).newInstance();
							s.configure(config, c, now, myCounters, myTimers);
							pool.execute(s);
						}
						catch (Exception e2)
						{
							log.warn("could not start shipper: " + e2);
						}
					}
					pool.shutdown();
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

	private class UDPListener implements Runnable
	{
		private InetSocketAddress sockAddr = null;
		private DatagramSocket socket = null;
		private boolean done = false;

		public UDPListener()
			throws Exception
		{
			sockAddr = new InetSocketAddress(config.udp_host, config.udp_port);
			socket = new DatagramSocket(sockAddr);
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
					String data = new String(p.getData());

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
							fields = line.split("|", 2);
						}

						if (fields.length != 2)
							continue;

						String name = fields[0];
						fields = fields[1].split("|");

						if (fields.length < 2)
							continue;

						String value = fields[0];
						String type  = fields[1];

						if (type.equals("ms") || type.equals("t"))
						{
							if (fields.length > 2)
								continue;

							//
							// update timer
							//
							synchronized(timersLock)
							{
								if (!timers.containsKey(name))
									timers.put(name, new DescriptiveStatistics());

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

							synchronized(countersLock)
							{
								if (!counters.containsKey(name))
									counters.put(name, 0L);

								counters.put(name, counters.get(name) + (long)v);
							}
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

	private class ZMQListener implements Runnable
	{
		private boolean done = false;
	        private Context context = null;
	        private Socket  socket  = null;
	        private Poller  items = null;

		public ZMQListener()
		{
			context = ZMQ.context(1);
	                socket = context.socket(ZMQ.PULL);
	                socket.setLinger(0L);
	                socket.bind(config.zmq_url);
	                items = context.poller(1);
	                items.register(socket, Poller.POLLIN);
		}

		public void run()
		{
			String data = null;

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
							continue;

						if (!fields[0].equals("1"))
							continue;

						if (fields[1].contains(":"))
						{
							fields = fields[1].split(":");
						}
						else
						{
							fields = fields[1].split("|", 2);
						}

						if (fields.length != 2)
							continue;

						String name = fields[0];

						fields = fields[1].split("|");

						if (fields.length < 2)
							continue;

						String value = fields[0];
						String type  = fields[1];

						if (type.equals("ms") || type.equals("t"))
						{
							if (fields.length > 2)
								continue;

							//
							// update timer
							//
							synchronized(timersLock)
							{
								if (!timers.containsKey(name))
									timers.put(name, new DescriptiveStatistics());

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

							synchronized(countersLock)
							{
								if (!counters.containsKey(name))
									counters.put(name, 0L);

								counters.put(name, counters.get(name) + (long)v);
							}
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
}
