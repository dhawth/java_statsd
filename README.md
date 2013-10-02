java_statsd
===========

A Java-based statsd server and client implementation.  This implementation is designed
to get around the performance impact of submitting a lot of timer values, and also allows
for multiple ingestion points and formats, as well as multiple output targets, e.g.
graphite AND zmq, for further processing.

Spec: https://gist.github.com/3243702
(also copied into docs/ in case the url goes away)

Build Depends
=============

	mvn 3.x
	java 1.6 or later (tested with openjdk and sun java)

Dependencies for Building Deb Files
===================================

	ant
	fpm

Build Process
=============

	1.  if updating version, perhaps for package management, edit the VERSION file
	2.  mvn package
	3.  ant package

This will create a .deb package that will install into /opt/statsd/bin/Statsd.jar,
but this can be changed by modifying the fpm command in the build.xml file.

mvn package will create two .jar files in target/: one is a monolithic jar (except for jzmq,
which is further explained in the Confguration section) which can be used to run a statsd server,
and a smaller jar for including/linking with your statsd client applications so they can talk to it.

To install the non-monolithic jar for inclusion as a dependency in other projects:

	mvn install

	The version may change here, in general it should match the git tag
	and the version number in the pom file:

        <dependency>
            <groupId>org.devnull.statsd</groupId>
            <artifactId>Statsd</artifactId>
            <version>0.0.1</version>
        </dependency>

Runtime/Testing Dependencies
============================

If you wish to use the ZMQ functionality with statsd either as a server or a client, you will
need to have zmq library version 2.2.x installed and in your library path, as well as JZMQ.
The jzmq jar file (zmq.jar) is not included in the monolithic jar file that maven will build.
User must have permission to bind to the interface and ports specified in the config file.

If you are including the Statsd-0.0.1.jar in your own monolithic build for using the client libs,
you will need to include the following dependencies in your jarball or have them in your CLASSPATH:

	(generated from mvn dependency:list)

	com.google.guava:guava:jar:15.0
	com.intellij:annotations:jar:7.0.3
	commons-cli:commons-cli:jar:1.2
	commons-codec:commons-codec:jar:1.6
	commons-io:commons-io:jar:2.3
	it.unimi.dsi:fastutil:jar:6.1.0
	log4j:log4j:jar:1.2.17
	org.apache.commons:commons-math3:jar:3.0
	org.codehaus.jackson:jackson-core-asl:jar:1.9.13
	org.codehaus.jackson:jackson-mapper-asl:jar:1.9.13

Running Statsd
==============

There are two ways to achieve the same thing:

	java -jar Statsd.jar -c <path to .conf> -l <path to log4j.conf> [-D]
	java -cp Statsd.jar org.devnull.statsd.Statsd -c <path to .conf> -l <path to log4j.conf> [-D]

Configuration
=============

Statsd will listen via UDP (the classical standard for statsd) and/or ZMQ depending on what is configured.

If the zmq_url is not null, it will attempt to bind to that URL and listen for connections.  In order for
ZMQ to work, you will need to have jzmq in the CLASSPATH as well as the underlying C/C++ zmq libraries
installed and in your LD_LIBRARY_PATH.  There is a jzmq jar included in the ./lib directory that is used
during unit tests and the compilation phase, and you *can* use that jar if you like, but it needs to link
to the system libraries for zmq version 2.x and is incompatible with 3.x.  It should also be noted that
2.x and 3.x are not intercompatible, afaik, so everything in your environment needs to match.  This is why
I did not bundle jzmq with the monolithic jarball.

Statsd will listen on UDP if udp_host is not null.  UDP port is 8125 by default.

The default configuration is to listen on UDP localhost:8125 and not to listen via ZMQ.

The default suffix is null, so no suffix will be appended to the stats when they are shipped.  A suffix is
something that identifies the host that the stat came from, e.g. sjc1.web1 for web1.sjc1.domain.com, which
allows you to glob and group more effectively in graphite while maintaining the ability to search by stat
type before digging down into a specific location by keeping the stat name first.

Statsd can ship statistics directly to graphite (the usual scenario) as well as via ZMQ.  You can configure
one or the other or both.

Default Configuration Files
===========================

There is a default log4j.conf config file in src/main/resources/log4j.conf if you do not wish to specify your
own.  It logs at INFO level to stdout.

There is no default statsd.conf file.  The configuration source code is in src/main/java/org/devnull/statsd/models/StatsdConfig.java

Extending
=========

It is possible to extend this statsd by implementing your own Shipping classes, and then specifying them
in the config file while they are in your CLASSPATH.

The Shipper interface is as follows:

	public void configure(final StatsdConfig statsdConfig,
			      final ShipperConfig config,
			      final BlockingQueue<Map.Entry<String, Long>> counters,
			      final BlockingQueue<Map.Entry<String, DescriptiveStatistics>> timers)
		throws Exception;

	public void shutdown();

Examples of how you can use the ShipperConfig to pass in configuration variables specific to your class are
available in the source for org.devnull.statsd.GraphiteShipper and ZMQShipper.
