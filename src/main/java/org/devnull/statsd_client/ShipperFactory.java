package org.devnull.statsd_client;

/**
 * Created with IntelliJ IDEA.
 * User: dhawth
 * Date: 10/3/13
 * Time: 10:31 AM
 * To change this template use File | Settings | File Templates.
 */
public class ShipperFactory
{
	public static Shipper getInstance(final String type) throws Exception
	{
		if (type == null)
		{
			return new NullStatsdShipper();
		}

		String lType = type.toLowerCase();

		if (lType.equals("udp"))
		{
			return new UDPStatsdShipper();
		}

		if (lType.equals("zmq"))
		{
			return new ZMQStatsdShipper();
		}

		throw new IllegalArgumentException("unknown type of statsd shipper: " + type);
	}
}
