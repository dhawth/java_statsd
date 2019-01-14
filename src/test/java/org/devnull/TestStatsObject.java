package org.devnull;

import org.apache.log4j.*;

import java.io.*;
import java.util.*;

import org.testng.annotations.*;
import static org.testng.AssertJUnit.*;

import org.devnull.statsd_client.StatsObject;

public final class TestStatsObject
{
	private Map<String, Long> results;
	private static Logger log = Logger.getLogger(TestStatsObject.class);

	@Test
	public void testStatsObject() throws IOException, FileNotFoundException
	{
		Properties logProperties = new Properties();

		logProperties.put("log4j.rootLogger", "DEBUG, stdout");
		logProperties.put("log4j.appender.stdout", "org.apache.log4j.ConsoleAppender");
		logProperties.put("log4j.appender.stdout.layout", "org.apache.log4j.EnhancedPatternLayout");
		logProperties.put("log4j.appender.stdout.layout.ConversionPattern", "%d [%F:%L] [%p] %C{1}: %m%n");
		logProperties.put("log4j.appender.stdout.immediateFlush", "true");
		logProperties.put("log4j.appender.null", "org.apache.log4j.varia.NullAppender");

		BasicConfigurator.resetConfiguration();
		PropertyConfigurator.configure(logProperties);

		log = Logger.getLogger(TestStatsObject.class);	// logs via root logger

		//
		// check raw log output
		//

		StatsObject so = StatsObject.getInstance();

		so.update(StatsObject.ValueType.SUM, "sum_test", 50L);
		so.update(StatsObject.ValueType.SUM, "sum_test", 100L);
		so.update(StatsObject.ValueType.MIN, "min_test", 100L);
		so.update(StatsObject.ValueType.MIN, "min_test", 50L);
		so.update(StatsObject.ValueType.MAX, "max_test", 100L);
		so.update(StatsObject.ValueType.MAX, "max_test", 500L);
		so.update(StatsObject.ValueType.AVG, "avg_test", 10L);
		so.update(StatsObject.ValueType.AVG, "avg_test", 20L);
		so.update(StatsObject.ValueType.AVG, "avg_test", 30L);
		so.update(StatsObject.ValueType.AVG, "avg_test", 40L);
		so.update(StatsObject.ValueType.AVG, "avg_test", 50L);

		try {
			Thread.sleep(1000);
		}
		catch (Exception e) {}

		results = so.getMap();
		assertTrue("results map is null", results != null);

		assertTrue("results[sum_test] is null: " + results.toString(), results.get("sum_test") != null);
		assertTrue("results[min_test] is null", results.get("min_test") != null);
		assertTrue("results[max_test] is null", results.get("max_test") != null);
		assertTrue("results[avg_test] is null", results.get("avg_test") != null);

		assertTrue("sum_test != 150 (sum_test = " + results.get("sum_test") + ")", results.get("sum_test") == 150L);
		assertTrue("min_test != 50", results.get("min_test") == 50L);
		assertTrue("max_test != 500", results.get("max_test") == 500L);
		assertTrue("avg_test != 30 (avg_test = " + results.get("avg_test") + ")", results.get("avg_test") == 30L);

		//
		// test the getValueByName function and make sure they agree with expectations
		//
		assertTrue("getValueByName(sum_test) != 150", so.getValueByName("sum_test") == 150L);
		assertTrue("getValueByName(avg_test) != 150", so.getValueByName("avg_test") == 30L);

		//
		// give it time to get written to the output log
		//
		try
		{
			//noinspection BusyWait
			Thread.sleep(2000);
		}
		catch (InterruptedException x)
		{
			log.debug(x.toString());
		}

		//
		// manually clear the map now that the default behavior is to not clear it
		//
		so.clearMap();

		results = so.getMap();
		assertTrue("results map is null", results != null);
		assertTrue("results map is not empty", results.isEmpty());

		//
		// reinsert the same data but don't write it out to logfile, clear it and
		// make sure it was cleared
		//
		so.update(StatsObject.ValueType.SUM, "sum_test", 50L);
		so.update(StatsObject.ValueType.SUM, "sum_test", 100L);
		so.update(StatsObject.ValueType.MIN, "min_test", 100L);
		so.update(StatsObject.ValueType.MIN, "min_test", 50L);
		so.update(StatsObject.ValueType.MAX, "max_test", 100L);
		so.update(StatsObject.ValueType.MAX, "max_test", 500L);
		so.update(StatsObject.ValueType.AVG, "avg_test", 10L);
		so.update(StatsObject.ValueType.AVG, "avg_test", 20L);
		so.update(StatsObject.ValueType.AVG, "avg_test", 30L);
		so.update(StatsObject.ValueType.AVG, "avg_test", 40L);
		so.update(StatsObject.ValueType.AVG, "avg_test", 50L);

		try {
			Thread.sleep(1000);
		}
		catch (Exception e) {}

		results = so.getMap();
		assertTrue("results map is null", results != null);

		assertTrue("results[sum_test] is null: " + results.toString(), results.get("sum_test") != null);
		assertTrue("results[min_test] is null", results.get("min_test") != null);
		assertTrue("results[max_test] is null", results.get("max_test") != null);
		assertTrue("results[avg_test] is null", results.get("avg_test") != null);

		assertTrue("sum_test != 150 (sum_test = " + results.get("sum_test") + ")", results.get("sum_test") == 150L);
		assertTrue("min_test != 50", results.get("min_test") == 50L);
		assertTrue("max_test != 500", results.get("max_test") == 500L);
		assertTrue("avg_test != 30 (avg_test = " + results.get("avg_test") + ")", results.get("avg_test") == 30L);

		so.timing("foo", 1);
		so.timing("foo", 2);
		so.timing("foo", 3);
		so.timing("foo", 1);
		so.timing("foo", 2);
		so.timing("foo", 3);

		Map<String, String> timers = so.getTimers();

		assertTrue(timers.toString(), timers.size() == 1);

		StatsObject referenceStatsObject = StatsObject.getInstance();
		referenceStatsObject.set("avg_test", 30);
		referenceStatsObject.set("max_test", 500);
		referenceStatsObject.set("sum_test", 150);
		referenceStatsObject.set("min_test", 50);
		referenceStatsObject.timing("foo", 1);
		referenceStatsObject.timing("foo", 2);
		referenceStatsObject.timing("foo", 3);
		referenceStatsObject.timing("foo", 1);
		referenceStatsObject.timing("foo", 2);
		referenceStatsObject.timing("foo", 3);
		assertEquals(so.getMap().get("avg_test"),referenceStatsObject.getMap().get("avg_test"));
		assertEquals(so.getMap().get("sum_test"),referenceStatsObject.getMap().get("sum_test"));
		assertEquals(so.getMap().get("max_test"),referenceStatsObject.getMap().get("max_test"));
		assertEquals(so.getMap().get("min_test"),referenceStatsObject.getMap().get("min_test"));
		assertEquals(so.getMap().get("timers"),referenceStatsObject.getMap().get("timers"));
	}
}
