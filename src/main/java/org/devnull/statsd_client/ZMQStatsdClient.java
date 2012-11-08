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

package org.devnull.statsd_client;

//
// this was copied wholesale from https://raw.github.com/etsy/statsd/master/StatsdClient.java
// which is a reference implementation of a statsd client in java
// it was then heavily modified to support the ZMQ implementation that focuses on
// optimization for timers.
//

/**
 * StatsdClient.java
 *
 * Example usage:
 *
 *    ZMQStatsdClient client = new ZMQStatsdClient("statsd.example.com", 8125);
 *
 *    // increment by 1
 *    client.increment("foo.bar.baz");
 *    // increment by 10
 *    client.increment("foo.bar.baz", 10);
 *    // send a timer with a string of values 1,2,3.4,5,6,6,6,6,6,6
 *    client.timing("foo.bar.baz", "1,2,3.4,5,6,7,6,,4,,3...");
 *
 */

import java.util.Random;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

import org.apache.log4j.Logger;

public class ZMQStatsdClient
{
	private static Logger log = Logger.getLogger(ZMQStatsdClient.class);

	private Context context = null;
	private Socket socket = null;
	private StringBuilder sb = new StringBuilder(65536);

	private final static int VERSION = 1;

	public ZMQStatsdClient(String url)
	{
		context = ZMQ.context(1);
		socket  = context.socket(ZMQ.PUSH);
		socket.setLinger(0);
		socket.setHWM(1L);
		//
		// attempt connect/reconnect at 1s, 2s, 4s, 8s, and then every 10s
		//
		socket.setReconnectIVL(1000L);
		socket.setReconnectIVLMax(10000L);
		socket.connect(url);
	}

	public boolean timing(final String key, final String values)
	{
		return send(key, values, "t");
	}

	public boolean increment(final String key)
	{
		return send(key, 1, "c");
	}

	public boolean increment(final String key, final int value)
	{
		return send(key, value, "c");
	}

	private boolean send(final String key, final String values, final String c)
	{
		synchronized(sb)
		{
			sb.setLength(0);
			sb.append(VERSION).append(";");
			sb.append(key).append(":").append(values).append("|").append(c);
	
			synchronized(socket)
			{
				return socket.send(sb.toString().getBytes(), 0);
			}
		}
	}

	private boolean send(final String key, final int value, final String c)
	{
		synchronized(sb)
		{
			sb.setLength(0);
			sb.append(VERSION).append(";");
			sb.append(key).append(":").append(value).append("|").append(c);
	
			synchronized(socket)
			{
				return socket.send(sb.toString().getBytes(), 0);
			}
		}
	}

	//
	// a method for sending a pre-compiled message, provided for performance reasons
	//
	public boolean send(final String message)
	{
		synchronized(socket)
		{
			return socket.send(message.getBytes(), 0);
		}
	}
}
