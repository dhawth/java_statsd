package org.devnull.statsd;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import java.io.IOException;

public abstract class JsonBase
{
	protected static final ObjectMapper mapper = new ObjectMapper();

	static
	{
		mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		//
		// guarantee consistent ordering of fields so our unit tests pass
		//
		mapper.configure(SerializationConfig.Feature.SORT_PROPERTIES_ALPHABETICALLY, true);
		mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
	}

	public String toString()
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

