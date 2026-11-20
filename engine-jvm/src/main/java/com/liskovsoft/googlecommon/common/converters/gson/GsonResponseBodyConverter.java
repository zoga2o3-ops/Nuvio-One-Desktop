/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.liskovsoft.googlecommon.common.converters.gson;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.googlecommon.common.helpers.ReflectionHelper;

import okhttp3.ResponseBody;
import retrofit2.Converter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;

final class GsonResponseBodyConverter<T> implements Converter<ResponseBody, T> {
  private static final String TAG = GsonResponseBodyConverter.class.getSimpleName();
  private final Gson gson;
  private final TypeAdapter<T> adapter;

  GsonResponseBodyConverter(Gson gson, TypeAdapter<T> adapter) {
    this.gson = gson;
    this.adapter = adapter;
  }

  @Override public T convert(ResponseBody value) throws IOException {
    //String response = Helpers.toString(value.byteStream());
    //InputStream newStream = Helpers.toStream(response);
    //JsonReader jsonReader = gson.newJsonReader(new InputStreamReader(newStream));

    String body;

    try {
      body = value.string();
    } finally {
      value.close();
    }

    JsonReader jsonReader = gson.newJsonReader(new StringReader(body));
    try {
      T result = adapter.read(jsonReader);

      // Dumping all data for debug purposes
      //ReflectionHelper.dumpDebugInfo(result.getClass(), response);

      if (jsonReader.peek() != JsonToken.END_DOCUMENT) {
        throw new JsonIOException("JSON document was not fully consumed.");
      }
      return result;
    } catch (IOException | RuntimeException e) {
      int length = body == null ? 0 : Math.min(body.length(), 400);
      Log.e(TAG, "Malformed JSON response: %s", body == null ? "<null>" : body.substring(0, length));
      throw e;
    }
  }
}
