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
	ant
	java 1.6 or later (tested with openjdk and sun java)
	fpm

Build Process
=============

	1.  if updating version, perhaps for package management, edit the VERSION file
	2.  mvn package
	3.  ant package

This will create a .deb package that will install into /opt/statsd/bin/Statsd.jar,
but this can be changed by modifying the fpm command in the build.xml file.

Runtime/Testing Dependencies
============================

Have zmq library version 2.2.x installed and in your library path, as well as JZMQ.
The jzmq jar file (zmq.jar) is included in the monolithic jar file that maven will build.
User must have permissions to bind to the interface and ports specified in the config file.

Expects to have a config file and a log4j properties file, and the default paths for these are:

	* /etc/statsd/statsd.conf
	* /etc/statsd/statsd.log4j.conf

Running Statsd
==============

	java -jar Statsd.jar -c <path to .conf> -l <path to log4j.conf> [-D]
	java -cp Statsd.jar org.devnull.statsd.Statsd -c <path to .conf> -l <path to log4j.conf> [-D]
