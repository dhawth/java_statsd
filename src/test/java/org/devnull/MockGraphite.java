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
import java.net.*;
import java.util.*;

import org.apache.log4j.*;

//
// this class is not intended to be threadsafe
//

class MockGraphite implements Runnable
{
	private static final Logger log = Logger.getLogger(MockGraphite.class);

	private HashMap<String, TreeMap<Integer, Long>> results = new HashMap<String, TreeMap<Integer, Long>>();
	private volatile boolean done = false;
	private static final Integer PORT = 12003;
	
	public void shutdown()
	{
		done = true;
	}

	public void run()
	{
		ServerSocket sock = new ServerSocket(PORT);
		sock.setSoTimeout(100);

		while (!done)
		{
			Socket incoming = null;

			try
			{
				try
				{
					incoming = sock.accept();
				}
				catch (SocketTimeoutException)
				{
					continue;
				}
	
				if (null == incoming)
				{
					continue;
				}
	
				BufferedReader reader = new BufferedReader(new InputStreamReader(incoming.getInputStream()));
				String message = reader.readLine();
	
				if (null == message)
				{
					log.debug("received null message");
					continue;
				}

				for (String line : message.split("\\n"))
				{
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
			}
			catch (Exception e)
			{
				log.info("exception in handle message: " + e);
			}
			finally
			{
				if (null != incoming)
				{
					try
					{
						incoming.close();
					}
					catch (Exception e)
					{
						log.info("exception closing socket: " + e);
					}
				}
			}
		}
	}

	public HashMap<String, TreeMap<Integer, Long>> getResults()
	{
		return results;
	}
}
