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

package org.devnull.statsd;

import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

import com.google.common.io.Files;

import java.io.*;

import org.devnull.statsd.models.*;

//
// This class provides the methods for reading in a config file and returning the
// appropriate data structure for the config file read in.
//

class ConfigReaders
{
	static NodeConfig ReadNodeConfig(final String configuration_file)
		throws Exception
	{
		String file_contents = ReadFile(configuration_file);

		if (file_contents.isEmpty())
		{
			throw new Exception("config file is empty");
		}

		Gson gson = new Gson();
		return gson.fromJson(file_contents, NodeConfig.class);
	}

	@SuppressWarnings({"AssignmentToNull"})
	static String ReadFile(@NotNull final String filename)
		throws Exception
	{

		return Files.toString(new File(filename), Charset.defaultCharset());
	}
}
