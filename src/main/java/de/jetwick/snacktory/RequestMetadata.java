package de.jetwick.snacktory;

import java.util.Map;

public interface RequestMetadata
{
    void setResponseCode(int code);
    int getResponseCode();
    void addHeader(String name, String value);
    Map<String, String> getHeaders();
}
