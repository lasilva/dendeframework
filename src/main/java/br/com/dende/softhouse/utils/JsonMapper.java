package br.com.dende.softhouse.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;

public final class JsonMapper {

    private static final ObjectMapper MAPPER = createMapper();

    private JsonMapper() {}

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Java Time (LocalDate, LocalDateTime, etc)
        mapper.registerModule(new JavaTimeModule());
        // Suporte a Optional
        mapper.registerModule(new Jdk8Module());

        // ISO-8601 ao invés de timestamp
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Ignora campos desconhecidos no request
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return mapper;
    }

    public static <T> T fromJson(InputStream body, Class<T> type) {
        try {
            return MAPPER.readValue(body, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON body", e);
        }
    }

    public static byte[] toJson(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialization error", e);
        }
    }
}

