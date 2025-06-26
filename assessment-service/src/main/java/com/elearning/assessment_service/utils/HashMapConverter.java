package com.elearning.assessment_service.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Converter
public class HashMapConverter implements AttributeConverter<Map<String, String>, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, String> customerInfo) {

        String customerInfoJson = null;
        try {
            customerInfoJson = objectMapper.writeValueAsString(customerInfo);
        } catch (final JsonProcessingException e) {
            //log.error("JSON writing error", e);
        }

        return customerInfoJson;
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String customerInfoJSON) {

        Map<String, String> customerInfo = null;
        try {
            customerInfo = objectMapper.readValue(customerInfoJSON, HashMap.class);
        } catch (final IOException e) {
            //log.error("JSON reading error", e);
        }

        return customerInfo;
    }
} 