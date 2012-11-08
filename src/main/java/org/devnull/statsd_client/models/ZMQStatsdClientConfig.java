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

package org.devnull.statsd_client.models;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;

final public class ZMQStatsdClientConfig
{
        private static final ObjectMapper mapper = new ObjectMapper();

        //
        // url of the zmq endpoint to connect to and send data to
        //
        @SerializedName("zmq_url")
        public String zmq_url = null;

	//
	// how often in seconds to ship data to statsd
	// default: 10 seconds
	//
	@SerializedName("period")
	public Integer period = 10;

	//
	// what prepend strings to use, e.g.
	// 	orgName.appType.appName
	//	3crowd.stats_system.CassandraWebAPI
	//
	@SerializedName("prepend_strings")
	public List<String> prepend_strings = new LinkedList<String>();

        public String toString()
        {
                synchronized(mapper)
                {
                        try
                        {
                                return mapper.writeValueAsString(this);
                        }
                        catch (IOException e)
                        {
                                return "unable to write value as string: " + e.getMessage();
                        }
                }
        }
}
