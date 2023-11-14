package com.newrelic.agent.android;


import com.newrelic.agent.android.util.Constants;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class HttpHeaders {

    protected static final AtomicReference<HttpHeaders> instance = new AtomicReference<>(new HttpHeaders());
    private final Set<String> httpHeaders;

    public static final String OPERATION_NAME = "operationName";
    public static final String OPERATION_TYPE = "operationType";
    public static final String OPERATION_ID = "operationId";

    private HttpHeaders() {
        httpHeaders = new HashSet<>();
        httpHeaders.add(Constants.ApolloGraphQLHeader.OPERATION_NAME);
        httpHeaders.add(Constants.ApolloGraphQLHeader.OPERATION_ID);
        httpHeaders.add(Constants.ApolloGraphQLHeader.OPERATION_TYPE);
    }

    public static HttpHeaders getInstance() {
        return instance.get();
    }

    public void addHttpHeaderAsAttribute(String httpHeader) {
        httpHeaders.add(httpHeader);
    }

    public void removeHttpHeaderAsAttribute(String httpHeader) {
        if (!(Constants.ApolloGraphQLHeader.OPERATION_ID.equalsIgnoreCase(httpHeader) || Constants.ApolloGraphQLHeader.OPERATION_NAME.equalsIgnoreCase(httpHeader) || Constants.ApolloGraphQLHeader.OPERATION_TYPE.equalsIgnoreCase(httpHeader)))
            httpHeaders.remove(httpHeader);
    }

    public boolean addHttpHeadersAsAttributes(List<String> httpHeaders) {
        return this.httpHeaders.addAll(httpHeaders);
    }

    public Set<String> getHttpHeaders() {
        return httpHeaders;
    }


    public static String translateApolloHeader(String s) {

        switch (s) {
            case Constants.ApolloGraphQLHeader.OPERATION_NAME:
                return OPERATION_NAME;
            case Constants.ApolloGraphQLHeader.OPERATION_ID:
                return OPERATION_ID;
            case Constants.ApolloGraphQLHeader.OPERATION_TYPE:
                return OPERATION_TYPE;
            default:
                return s;
        }
    }
}
