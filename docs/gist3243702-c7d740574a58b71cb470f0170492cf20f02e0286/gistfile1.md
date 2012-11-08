make statsd better for high throughput
=======================================
In the interest of shipping high volumes of updates in the shortest amount of time and work, I'm proposing two statsd changes:

1. enhance the timer update format
2. add a zeromq input (in addition to UDP)

Original UDP protocol
---------------------

A series of stats updates separated by `\n`.  Each update can be a timer or counter (originally; there are a few more extensions now, see https://github.com/b/statsd_spec).

### Counters

* `<metric name>:<value>|c`
* `<metric name>:<value>|c|@<sample rate>`

If you are only sampling 1/N events, include N here as the sample rate and we'll count it as a counter update of N*`<value>`. It's probably easier to have your client do the math itself or a little bit of batching and just flush one update every couple seconds per counter instead of this sampling madness.

Counters will be turned into rates (every `$FLUSH_INTERVAL`, we take the total value for the counter and divide by `$FLUSH_INTERVAL` to calculate the rate).

### Timers

* `<metric name>:<value>|ms`

Every `$FLUSH_INTERVAL`, all the values received since the last flush will be aggregated in a few ways: min, mean, upper_90 ("90" being configurable), max.

There's probably a better name than "timer" for this, because this is also how gauges should be recorded. For example, if you wanted to periodically record through statsd how big a certain internal queue is, just send a timer update once a second with the queue size and you'll be able to graph your mean/upper_90/etc queue size.

### Limitations

* A single UDP packet can only be so big, so you have to be really careful with how many metric updates you pack in a single packet if you don't want to silently cause confusion (have your packet get split into two at a bad point). Lots of clients just send one update per packet.
* Some network stack tweaking is needed to up your UDP throughput rate if you're going to be doing a lot of updates.
* Batching timer updates is hard. Batching counter updates is easy: a client can cache that it did 300 counter +1 updates for metric foo and every few seconds, flush the total. Timer updates can't really be aggregated at the statsd client level -- you want to pass the raw values so statsd's aggregations (min/max/upper90/mean) make more sense, and currently that means repeating the metric name once per timer update (and this takes up valuable space in the packet, and will basically cause you to send a lot more updates). Once you hit a certain point (hundreds of timers being updated thousands of times a second) you'll end up flushing UDP packets like mad with tons of repeated content in them.

Proposal 1: Extended timer update format
-----------------------------------------

To specifically address clients that might want to batch their statsd updates, allow specifying more than one value for a timer.

* `<metric name>|<value>[,<value2>,...,<valueN>]|t`
* `<metric name>|<value>[,<value2>,...,<valueN>]|ms`
** The key difference here is we allow multiple values to be passed in. They'll all be counted as samples for <metric name>.
** We accept `t` or `ms` for the timer type (`ms` for legacy reasons; it implies a lot to someone casually reading the code and isn't very descriptive, IMO -- `t` for timer makes more sense here).

Proposal two: add ZeroMQ input
------------------------------

The zeromq input will listen on a `zmq.PULL` socket. This can be *in addition to* the UDP server, not a replacement. They both speak approximately the same protocol.

Each message should start with the protocol version, followed by a `;`, followed by the update payload.

Version "1" update payload is the same format as the UDP protocol uses. We don't take multiple updates separated by `\n` (no need to with zeromq).  

I'm not entirely convinced about the protocol number, but it feels like it leaves the most flexibility for future improvements. I only think the protocol versioning should be on the zeromq input (to start); it would potentially not be backwards compatible with the current UDP clients.

### Sample zeromq messages (v1)

```
1;app.foo.hits|5|c
```

```
1;app.foo.response_time|14.5,10.4,8.9,11.3,12.3|t
```

### Implementation

I'm planning on implementing these in my ruby-statsd repo for testing.