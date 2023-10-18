package com.newrelic.agent.android;

import static com.newrelic.agent.android.util.Constants.ApolloGraphQLHeader.OPERATION_ID;
import static com.newrelic.agent.android.util.Constants.ApolloGraphQLHeader.OPERATION_NAME;
import static com.newrelic.agent.android.util.Constants.ApolloGraphQLHeader.OPERATION_TYPE;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HttpHeaders {

    private static HttpHeaders instance = null;
    private final Set<String> httpHeaders;


    private HttpHeaders() {
        httpHeaders = new HashSet<>();
        httpHeaders.add(OPERATION_NAME);
        httpHeaders.add(OPERATION_ID);
        httpHeaders.add(OPERATION_TYPE);
    }

    public static HttpHeaders getInstance() {
        if (instance == null) {
            instance = new HttpHeaders();
        }
        return instance;
    }

    public void addHttpHeaderAsAttribute(String httpHeader) {
        httpHeaders.add(httpHeader);
    }

    public void removeHttpHeaderAsAttribute(String httpHeader) {
        if (!(OPERATION_ID.equalsIgnoreCase(httpHeader) || OPERATION_NAME.equalsIgnoreCase(httpHeader) || OPERATION_TYPE.equalsIgnoreCase(httpHeader)))
            httpHeaders.remove(httpHeader);
    }

    public boolean addHttpHeadersAsAttributes(List<String> httpHeaders) {
        return this.httpHeaders.addAll(httpHeaders);
    }

    public Set<String> getHttpHeaders() {
        return httpHeaders;
    }
}
