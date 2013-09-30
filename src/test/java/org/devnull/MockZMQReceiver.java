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

package org.devnull;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;

import org.apache.log4j.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

//
// this class is not intended to be threadsafe
//

class MockZMQReceiver implements Runnable
{
	private static final Logger log = Logger.getLogger(MockZMQReceiver.class);

	@NotNull
	private HashMap<String, TreeMap<Integer, Long>> results = new HashMap<String, TreeMap<Integer, Long>>();
	private volatile boolean done = false;
	private static final String url = "tcp://127.0.0.1:12004";
	@Nullable
	private Context context = null;
	@Nullable
	private Socket  socket  = null;
	@Nullable
	private Poller  items = null;

	public void shutdown()
	{
		done = true;
	}

	public MockZMQReceiver()
	{
		context = ZMQ.context(1);
		socket = context.socket(ZMQ.PULL);
		socket.setLinger(0L);
		socket.setHWM(1L);
		socket.bind(url);
		items = context.poller(1);
		items.register(socket, Poller.POLLIN);
	}

	@NotNull
	public HashMap<String, TreeMap<Integer, Long>> getResults()
	{
		return results;
	}

	public void run()
	{
		while (!done)
		{
			try
			{
				//
				// poll returns the number of items that were signaled in this call
				//
				if (0 == items.poll(100))
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

				String line = new String(socket.recv(0));

				if (null == line)
				{
					log.debug("received null message");
					continue;
				}

				String[] fields = line.split(" ");

				if (fields.length != 3)
				{
					log.info("too few fields in line: " + line);
					continue;
				}

				if (!results.containsKey(fields[0]))
				{
					results.put(fields[0], new TreeMap<Integer, Long>());
				}

				Integer timestamp = Integer.parseInt(fields[2]);

				if (results.get(fields[0]).get(timestamp) == null)
				{
					results.get(fields[0]).put(timestamp, 0L);
				}

				Long value = Long.parseLong(fields[1]);

				results.get(fields[0]).put(timestamp, value + results.get(fields[0]).get(timestamp));
			}
			catch (Exception e)
			{
				log.info("exception in socket send: " + e);
			}
		}
	}
}
