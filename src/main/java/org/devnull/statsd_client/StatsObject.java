package org.devnull.statsd_client;

/**
 * Singleton data bag for counters and timers.  This is the main interface between
 * your application and the submission of data to statsd.  The StatsObject can grow
 * unbounded unless it is cleared periodically by a StatsdShipper class, namely
 * {@link UDPStatsdShipper UDPStatsdShipper} or {@link ZMQStatsdShipper ZMQStatsdShipper}.
 *
 * @author David Hawthorne
 * @version %I%, %G%
 * @see     UDPStatsdShipper
 * @see     ZMQStatsdShipper
 * @since 1.0
 */

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import org.apache.log4j.Logger;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.map.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;

//
// for storage of timer data until submission:
//

public final class StatsObject extends JsonBase
{
	private static Logger log = Logger.getLogger(StatsObject.class);
	private static final ObjectMapper mapper = new ObjectMapper();

	@NotNull
	private StringBuilder sb = new StringBuilder(65536);

	@NotNull
	private final HashMap<String, StatObject> currentValues = new HashMap<String, StatObject>();

	@NotNull
	private final HashMap<String, IntArrayFIFOQueue> currentTimers = new HashMap<String, IntArrayFIFOQueue>();

	//
	// a place to store unused IntArrayFIFOQueues when they are not in use,
	// to avoid object creation
	//
	@NotNull
	private ArrayBlockingQueue<IntArrayFIFOQueue> queueQueue = new ArrayBlockingQueue<IntArrayFIFOQueue>(1000);

	//
	// NB: This is the number of values that will be used to calculate
	// timer values like mean, min, max, 95th/90th
	//
	private static final int INITIAL_SIZE = 10000;

	public enum ValueType
	{
		MIN, MAX, AVG, SUM
	}

	private StatsObject()
	{
	}

	private static class SingletonHolder
	{
		public static final StatsObject INSTANCE = new StatsObject();
	}

	@NotNull
	public static StatsObject getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	/*
	 * getMap returns a String -> Long map of the currentValues hashmap,
	 * translating the StatObjects into their Long representations.
	 * returned map is unordered.
	 */

	@NotNull
	public Map<String, Long> getMap()
	{
		Map<String, Long> map = new HashMap<String, Long>();

		synchronized (currentValues)
		{
			for (StatObject s : currentValues.values())
			{
				if (null == s)
				{
					continue;
				}
				switch (s.getType())
				{
					case AVG:
						map.put(s.getKey(), s.getValue() / s.getCount());
						break;
					case SUM:
					case MIN:
					case MAX:
						map.put(s.getKey(), s.getValue());
					default:
						break;
				}
			}
		}
		return map;
	}

	//
	// returns the same as getMap but also clears the local map after copying
	//
	@NotNull
	@JsonIgnore
	public HashMap<String, Long> getMapAndClear()
	{
		HashMap<String, Long> map = new HashMap<String, Long>();

		synchronized (currentValues)
		{
			for (StatObject s : currentValues.values())
			{
				if (null == s)
				{
					continue;
				}
				switch (s.getType())
				{
					case AVG:
						map.put(s.getKey(), s.getValue() / s.getCount());
						break;
					case SUM:
					case MIN:
					case MAX:
						map.put(s.getKey(), s.getValue());
					default:
						break;
				}
			}
			currentValues.clear();
		}
		return map;
	}

	//
	// returns a list of timers by name with values being a comma-delimited
	// list of integers representing the values for those timers.
	// clears the individual queues of the timers, but DOES NOT CLEAR the
	// currentTimers hashmap itself.  This is a performance optimization,
	// because if we cleared the currentTimers hashmap, we would just create a
	// new FIFOQueue the next time a timer with the same name as a previous timer
	// was added.
	// unused timer FIFOQueues are removed on a second call of getTimersAndClear if
	// they are still 0 length, and they are put on the queueQueue if it has
	// enough space for them.
	// this is the only function that adds items to the queueQueue
	//
	@NotNull
	@JsonIgnore
	public HashMap<String, String> getTimersAndClear()
	{
		IntArrayFIFOQueue q;
		HashMap<String, String> map = new HashMap<String, String>();

		synchronized (currentTimers)
		{
			for (String name : currentTimers.keySet())
			{
				sb.setLength(0);
				q = currentTimers.get(name);

				if (q.size() == 0)
				{
					//
					// remove timer because it was unused between iterations
					//
					currentTimers.remove(name);

					//
					// attempt to put the IntArrayFIFOQueue on the queueQueue,
					// but do not block.
					//
					queueQueue.offer(q);

					continue;
				}

				try
				{
					sb.append(q.dequeueInt());

					//
					// dequeueInt works until it throws a No Such Element exception
					//
					while (!q.isEmpty())
					{
						sb.append(",").append(q.dequeueInt());
					}
				}
				catch (NoSuchElementException e)
				{
				}

				if (sb.length() > 0)
				{
					//
					// remove trailing comma
					//
					sb.setLength(sb.length() - 1);
				}

				map.put(name, sb.toString());

				q.clear();
			}
		}

		return map;
	}

	@NotNull
	@JsonIgnore
	public HashMap<String, IntArrayFIFOQueue> getTimerQueuesAndClear()
	{
		HashMap<String, IntArrayFIFOQueue> map = new HashMap<String, IntArrayFIFOQueue>();

		synchronized (currentTimers)
		{
			for (String name : currentTimers.keySet())
			{
				map.put(name, currentTimers.get(name));
			}

			currentTimers.clear();
		}

		return map;
	}

	//
	// returns an unsorted map of timers by name with values being a comma-delimited
	// list of integers representing the values for those timers.
	// does not modify the currentTimers hashmap or values.
	//
	@NotNull
	public Map<String, String> getTimers()
	{
		IntArrayFIFOQueue q;
		HashMap<String, String> map = new HashMap<String, String>();

		synchronized (currentTimers)
		{
			for (String name : currentTimers.keySet())
			{
				sb.setLength(0);
				q = currentTimers.get(name);

				if (q.size() == 0)
				{
					map.put(name, "");
					continue;
				}

				int foo;

				//
				// pop int, put in string, then push int back onto end of queue
				// it's the only way to iterate over this structure without
				// modifying it.
				//
				foo = q.dequeueInt();
				sb.append(foo);
				q.enqueue(foo);

				for (int i = 0; i < q.size() - 1; i++)
				{
					foo = q.dequeueInt();
					sb.append(",").append(foo);
					q.enqueue(foo);
				}

				map.put(name, sb.toString());
			}
		}

		return map;
	}

	//
	// returns the counter value of the named metric
	//
	@Nullable
	public Long getValueByName(String name)
	{
		StatObject s;

		synchronized (currentValues)
		{
			s = currentValues.get(name);
		}

		if (null == s)
		{
			return null;
		}

		switch (s.getType())
		{
			case AVG:
				return s.getValue() / s.getCount();
			case SUM:
			case MIN:
			case MAX:
				return s.getValue();
			default:
				return null;
		}
		//
		// notreached
		//
	}

	//
	// these functions provided primarily for unit testing purposes
	//
	public void clear()
	{
		clearMap();
		clearTimers();
	}

	public void clearMap()
	{
		synchronized (currentValues)
		{
			currentValues.clear();
		}
	}

	public void clearTimers()
	{
		synchronized (currentTimers)
		{
			currentTimers.clear();
		}
	}

	public void clear(final String key)
	{
		synchronized (currentValues)
		{
			currentValues.remove(key);
		}
	}

	public void set(final String key, final long value)
	{
		StatObject s = new StatObject(ValueType.SUM, key, value);
		synchronized (currentValues)
		{
			currentValues.remove(key);
			currentValues.put(key, s);
		}
	}

	//
	// both of these timing calls will only enqueue up to INITIAL_SIZE
	// values before dropping the rest on the floor.  This avoids resizing
	// and copying the array underlying the IntArrayFIFOQueue object.
	//
	public void timing(final String name, final int value)
	{
		synchronized (currentTimers)
		{
			if (currentTimers.containsKey(name))
			{
				if (currentTimers.get(name).size() < INITIAL_SIZE)
				{
					currentTimers.get(name).enqueue(value);
				}
			}
			else
			{
				IntArrayFIFOQueue q = queueQueue.poll();
				if (null == q)
				{
					q = new IntArrayFIFOQueue(INITIAL_SIZE);
				}
				currentTimers.put(name, q);
				currentTimers.get(name).enqueue(value);
			}
		}
	}

	public void timing(final String name, final long value)
	{
		timing(name, (int)value);
	}

	public void increment(final String key)
	{
		update(ValueType.SUM, key, 1L);
	}

	public void increment(final String key, final long value)
	{
		update(ValueType.SUM, key, value);
	}

	public void increment(final String key, final int value)
	{
		update(ValueType.SUM, key, (long)value);
	}

	public void average(final String key, final long value)
	{
		update(ValueType.AVG, key, value);
	}

	public void average(final String key, final int value)
	{
		update(ValueType.AVG, key, (long)value);
	}

	public void min(final String key, final long value)
	{
		update(ValueType.MIN, key, value);
	}

	public void min(final String key, final int value)
	{
		update(ValueType.MIN, key, (long)value);
	}

	public void max(final String key, final long value)
	{
		update(ValueType.MAX, key, value);
	}

	public void max(final String key, final int value)
	{
		update(ValueType.MAX, key, (long)value);
	}

	/**
	 * update
	 * the update() method is the main external interface point.
	 * usage: update(StatsObject.ValueType.SUM, "some value that represents a sum", 5)
	 */

	public void update(@NotNull final ValueType type, @Nullable final String name, final long value)
	{
		if (name == null || value <= 0)
		{
			return;
		}

		synchronized (currentValues)
		{
			StatObject s = currentValues.get(name);
			if (s == null)
			{
				s = new StatObject(type, name, value);
				currentValues.put(name, s);
				return;
			}

			switch (type)
			{
				case SUM:
					s.setValue(s.getValue() + value);
					break;
				case MIN:
					if (value < s.getValue())
					{
						s.setValue(value);
					}
					break;
				case MAX:
					if (value > s.getValue())
					{
						s.setValue(value);
					}
					break;
				case AVG:
					s.incrementCount();
					s.setValue(s.getValue() + value);
					break;
				default:
					break;
			}
		}

	}

	private final static class StatObject extends JsonBase
	{
		private String key;
		private ValueType type;
		protected Long value;
		protected Long count;

		protected StatObject(final ValueType t, final String k, final Long v)
		{
			type = t;
			key = k;
			value = v;
			count = 1L;
		}

		protected StatObject(final ValueType t, final String k, final Long v, final Long c)
		{
			type = t;
			key = k;
			value = v;
			count = c;
		}

		protected String getKey()
		{
			return key;
		}

		protected ValueType getType()
		{
			return type;
		}

		protected void setValue(final Long v)
		{
			value = v;
		}

		protected Long getValue()
		{
			return value;
		}

		protected void setCount(final Long c)
		{
			count = c;
		}

		protected Long getCount()
		{
			return count;
		}

		protected void incrementCount()
		{
			count++;
		}
	}
}
