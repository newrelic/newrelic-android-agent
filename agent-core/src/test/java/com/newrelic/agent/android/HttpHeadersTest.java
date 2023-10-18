package com.newrelic.agent.android;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class HttpHeadersTest {
    private HttpHeaders httpHeaders;

    @Before
    public void setUp() {
        httpHeaders = HttpHeaders.getInstance();

    }

    @Test
    public void testGetInstance() {
        HttpHeaders newInstance = HttpHeaders.getInstance();
        assertNotNull(newInstance);
        assertEquals(httpHeaders, newInstance);
    }

    @Test
    public void testAddHttpHeaderAsAttribute() {
        httpHeaders.addHttpHeaderAsAttribute("X-Custom-Header");
        assertTrue(httpHeaders.getHttpHeaders().contains("X-Custom-Header"));
    }

    @Test
    public void testDefaultHttpHeadersAsAttributes() {
        List<String> headers = Arrays.asList("X-Custom-Header", "X-APOLLO-OPERATION-NAME", "X-APOLLO-OPERATION-TYPE", "X-APOLLO-OPERATION-ID", "X-Custom-Header-1", "X-Custom-Header-2");
        HashSet<String> headersSet = new HashSet<>(headers);
        assertEquals(headersSet, httpHeaders.getHttpHeaders());
    }


    @Test
    public void testAddHttpHeadersAsAttributes() {
        List<String> headersToAdd = Arrays.asList("X-Custom-Header-1", "X-Custom-Header-2");
        assertTrue(httpHeaders.addHttpHeadersAsAttributes(headersToAdd));
        assertEquals(5, httpHeaders.getHttpHeaders().size());
    }

    @Test
    public void testRemoveHttpHeaderAsAttribute() {
        httpHeaders.removeHttpHeaderAsAttribute("X-Custom-Header");
        assertFalse(httpHeaders.getHttpHeaders().contains("X-Custom-Header"));
    }


}