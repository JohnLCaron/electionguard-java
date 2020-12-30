/*
Copyright (c) 2016, Liu Dong
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.sunya.electionguard.publish;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Deal with java optional type
 *
 * @author Liu Dong
 * @author JohnLCaron use "None" instead of "null" for Optional, to match python
 */
class OptionalAdapter<T> extends TypeAdapter<Optional<T>> {
  private final TypeAdapter<T> delegate;

  public OptionalAdapter(TypeAdapter<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void write(JsonWriter out, Optional<T> value) throws IOException {
    // optional should not be null
    if (value == null) {
      out.nullValue();
      return;
    }
    if (!value.isPresent()) {
      out.value("None");
      return;
    }
    delegate.write(out, value.get());
  }

  @Override
  public Optional<T> read(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return Optional.empty();
    }
    return Optional.ofNullable(delegate.read(in));
  }

  @SuppressWarnings("unchecked")
  public static OptionalAdapter getInstance(Gson gson, TypeToken typeToken) {
    TypeAdapter delegate;
    Type type = typeToken.getType();
    if (type instanceof ParameterizedType) {
      Type innerType = ((ParameterizedType) type).getActualTypeArguments()[0];
      delegate = gson.getAdapter(TypeToken.get(innerType));
    } else if (type instanceof Class) {
      delegate = gson.getAdapter(Object.class);
    } else {
      throw new JsonIOException("Unexpected type type:" + type.getClass());
    }
    return new OptionalAdapter<>(delegate);
  }
}
