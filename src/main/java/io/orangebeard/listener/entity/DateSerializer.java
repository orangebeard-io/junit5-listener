package io.orangebeard.listener.entity;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

class DateSerializer extends JsonSerializer<LocalDateTime> {

    @Override
    public void serialize(LocalDateTime value, JsonGenerator jgen, SerializerProvider serializers) throws IOException {
        jgen.writeRawValue(String.valueOf(value.atZone(ZoneId.systemDefault()).toEpochSecond() * 1000));
    }
}
