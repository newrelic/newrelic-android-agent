package com.newrelic.agent.android.sessionReplay.models.IncrementalEvent;

import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public abstract class RRWebIncrementalData {
    public int source;
    
    // This class will be extended by specific data types
    // We'll need a custom serializer for Gson to handle this properly
    
    public static class RRWebIncrementalDataSerializer implements JsonSerializer<RRWebIncrementalData> {
        @Override
        public JsonElement serialize(RRWebIncrementalData src, Type typeOfSrc, JsonSerializationContext context) {
            // Let the specific subclass handle its own serialization
            return context.serialize(src);
        }
    }
}