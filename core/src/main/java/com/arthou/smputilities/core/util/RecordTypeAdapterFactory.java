package com.arthou.smputilities.core.util;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads and writes records through their canonical constructor. Gson 2.8.9
 * (shipped by Minecraft 1.18.2 and 1.19.2) cannot fill a record's final
 * fields, so every file holding one (homes, /back, warps) failed to load
 * there. The JSON shape is the one newer Gson uses: one key per component.
 */
final class RecordTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        Class<? super T> raw = type.getRawType();
        if (!raw.isRecord()) {
            return null;
        }
        RecordComponent[] components = raw.getRecordComponents();
        TypeAdapter<Object>[] adapters = new TypeAdapter[components.length];
        Class<?>[] types = new Class<?>[components.length];
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            adapters[i] = (TypeAdapter<Object>) gson.getAdapter(TypeToken.get(components[i].getGenericType()));
            indexes.put(components[i].getName(), i);
            components[i].getAccessor().setAccessible(true);
        }
        Constructor<? super T> constructor;
        try {
            constructor = raw.getDeclaredConstructor(types);
            constructor.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new JsonIOException("No canonical constructor for " + raw.getName(), e);
        }
        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null) {
                    out.nullValue();
                    return;
                }
                out.beginObject();
                for (int i = 0; i < components.length; i++) {
                    out.name(components[i].getName());
                    try {
                        adapters[i].write(out, components[i].getAccessor().invoke(value));
                    } catch (ReflectiveOperationException e) {
                        throw new JsonIOException("Could not read " + components[i].getName() + " of " + raw.getName(), e);
                    }
                }
                out.endObject();
            }

            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }
                Object[] values = new Object[components.length];
                in.beginObject();
                while (in.hasNext()) {
                    Integer index = indexes.get(in.nextName());
                    if (index == null) {
                        in.skipValue();
                    } else {
                        values[index] = adapters[index].read(in);
                    }
                }
                in.endObject();
                for (int i = 0; i < values.length; i++) {
                    if (values[i] == null && types[i].isPrimitive()) {
                        values[i] = Array.get(Array.newInstance(types[i], 1), 0);
                    }
                }
                try {
                    return (T) constructor.newInstance(values);
                } catch (ReflectiveOperationException e) {
                    throw new JsonIOException("Could not create " + raw.getName(), e);
                }
            }
        };
    }
}
