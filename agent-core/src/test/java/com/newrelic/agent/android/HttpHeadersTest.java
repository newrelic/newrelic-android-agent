package com.newrelic.agent.android;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.newrelic.agent.android.util.Constants;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    public void testConcurrentMutationDoesNotThrow() throws InterruptedException {
        // Reproduces NR-575842: ConcurrentModificationException when iterating
        // HttpHeaders while another thread mutates it. The "defensive copy"
        // pattern new HashSet<>(getHttpHeaders()) does not prevent CME because
        // HashSet's collection constructor calls iterator() on the source.
        final String prefix = "TestHeaderConcurrent_";
        final long deadline = System.currentTimeMillis() + 250;
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);

        Thread iterator = new Thread(() -> {
            ready.countDown();
            try {
                start.await();
            } catch (InterruptedException ignored) {
                return;
            }
            try {
                while (System.currentTimeMillis() < deadline) {
                    Set<String> snapshot = new HashSet<>(httpHeaders.getHttpHeaders());
                    snapshot.size();
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        }, "HttpHeaders-iterator");

        Thread mutator = new Thread(() -> {
            ready.countDown();
            try {
                start.await();
            } catch (InterruptedException ignored) {
                return;
            }
            try {
                int i = 0;
                while (System.currentTimeMillis() < deadline) {
                    String h = prefix + (i++ & 0x3FF);
                    httpHeaders.addHttpHeaderAsAttribute(h);
                    httpHeaders.removeHttpHeaderAsAttribute(h);
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        }, "HttpHeaders-mutator");

        try {
            iterator.start();
            mutator.start();
            ready.await();
            start.countDown();
            iterator.join();
            mutator.join();

            if (error.get() != null) {
                throw new AssertionError(
                        "Concurrent iteration + mutation must not throw: " + error.get(),
                        error.get());
            }
        } finally {
            // Restore singleton to pre-test state
            for (String h : new HashSet<>(httpHeaders.getHttpHeaders())) {
                if (h != null && h.startsWith(prefix)) {
                    httpHeaders.removeHttpHeaderAsAttribute(h);
                }
            }
        }
    }

    @Test
    public void testTranslateApolloHeader() {
        String otherHeader = "otherHeader";

        assertEquals(HttpHeaders.OPERATION_NAME,
                HttpHeaders.translateApolloHeader(Constants.ApolloGraphQLHeader.OPERATION_NAME));
        assertEquals(HttpHeaders.OPERATION_ID,
                HttpHeaders.translateApolloHeader(Constants.ApolloGraphQLHeader.OPERATION_ID));
        assertEquals(HttpHeaders.OPERATION_TYPE,
                HttpHeaders.translateApolloHeader(Constants.ApolloGraphQLHeader.OPERATION_TYPE));

        assertEquals(otherHeader,
                HttpHeaders.translateApolloHeader(otherHeader));
    }


}