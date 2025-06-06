package com.newrelic.agent.android.sessionReplay.models;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Custom serializer for Attributes class that outputs the metadata map directly as key-value pairs
 * in the JSON without the "attributes" wrapper.
 */
public class AttributesSerializer implements JsonSerializer<Attributes> {
    
    @Override
    public JsonElement serialize(Attributes src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        
        // Add all metadata entries directly to the root JSON object
        for (Map.Entry<String, String> entry : src.getMetadata().entrySet()) {
            jsonObject.addProperty(entry.getKey(), entry.getValue());
        }
        // Add other fields of Attributes if needed
        if (src.getId() != null) {
            jsonObject.addProperty("id", src.getId());
        }
        if (src.getType() != null) {
            jsonObject.addProperty("type", src.getType());
        }
        if (src.getValue() != null) {
            jsonObject.addProperty("value", src.getValue());
        }
        
        return jsonObject;
    }
}