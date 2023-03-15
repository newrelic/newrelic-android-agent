/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.io;

import com.newrelic.agent.android.util.TestUtil;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CountingInputStreamTest {

    @Test
    public void readBufferBytesExceptionTest() throws Exception {
        TestInputStream tis = new TestInputStream();

        int maxBuffer = 1024;
        CountingInputStream cis = new CountingInputStream(tis, true, maxBuffer);

        final BufferReader reader = new BufferReader(cis, maxBuffer);
        final BufferSkipper skipper = new BufferSkipper(cis, maxBuffer);

        Executor executor = Executors.newFixedThreadPool(2);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                while (true)
                    reader.run();
            }
        });

        executor.execute(new Runnable() {
            @Override
            public void run() {
                while (true)
                    skipper.run();
            }
        });

        Thread.sleep(1000);
    }

    @Test
    public void testNullErrorStream() throws Exception {
        CountingInputStream cis = null;
        try {
            cis = new CountingInputStream(null);
            Assert.fail("Counting stream should throw exception");
        } catch (Exception e) {
            Assert.assertTrue("Should contain exception message", e.getMessage().contains("input stream cannot be null"));
            Assert.assertNull("Counting stream should be null", cis);
        }

        try {
            cis = new CountingInputStream(null, true);
            Assert.fail("Counting stream should throw exception");
        } catch (Exception e) {
            Assert.assertNull("Counting stream should be null", cis);
        }
    }

    @Test
    public void getBufferAsString() throws Exception {
        CountingInputStream cisBuffered = new CountingInputStream(new ByteArrayInputStream("hellohello".getBytes()), true);
        Assert.assertTrue(cisBuffered.getBufferAsString().equals("hellohello"));
        Assert.assertTrue(cisBuffered.read() != -1);

        CountingInputStream cisUnbuffered = new CountingInputStream(new ByteArrayInputStream("hellohello".getBytes()), false);
        Assert.assertTrue(cisUnbuffered.getBufferAsString().equals(""));
        Assert.assertTrue(cisUnbuffered.read() != -1);
    }

    @Test
    public void testFillBuffer() throws Exception {
        String data = new String("winken\nblinken\n\nand\n\n\nnod");

        CountingInputStream cis = new CountingInputStream(new ByteArrayInputStream(data.getBytes()), true);
        Assert.assertEquals(data, cis.getBufferAsString());

        // test with tiny buffer
        int bufferSize = 4;
        cis = new CountingInputStream(new ByteArrayInputStream(data.getBytes()), true, bufferSize);
        Assert.assertEquals(cis.getBufferAsString(), data.substring(0, bufferSize));
        Assert.assertEquals(cis.available(), data.length());
        Assert.assertEquals(data, TestUtil.slurp(cis));
    }

    @Test
    public void closeWithBuggyCompletionHandler() throws IOException {
        InputStream isSpy = Mockito.spy(new ByteArrayInputStream("dowhatchalike".getBytes()));
        CountingInputStream cis = Mockito.spy(new CountingInputStream(isSpy));

        cis.addStreamCompleteListener(new StreamCompleteListener() {
            @Override
            public void streamError(StreamCompleteEvent e) {
                throw new RuntimeException("RuntimeException thrown from streamComplete handler");
            }

            @Override
            public void streamComplete(StreamCompleteEvent e) {
                throw new IllegalStateException("IllegalStateException thrown from streamComplete handler");
            }
        });

        try {
            cis.close();
        } catch (Exception e) {
            Assert.fail(e.getLocalizedMessage());
        }
    }

    @Test
    public void closeWithError() throws IOException {
        InputStream isSpy = Mockito.spy(new ByteArrayInputStream("dowhatchalike".getBytes()));
        CountingInputStream cis = Mockito.spy(new CountingInputStream(isSpy));

        Mockito.doThrow(new IllegalStateException()).when(isSpy).close();
        cis.addStreamCompleteListener(new StreamCompleteListener() {
            @Override
            public void streamError(StreamCompleteEvent e) {
                String cause = null;
                throw new RuntimeException("RuntimeException thrown from streamComplete handler:" + cause.toLowerCase());
            }

            @Override
            public void streamComplete(StreamCompleteEvent e) {
                Assert.fail(e.getException().getLocalizedMessage());
            }
        });

        try {
            cis.close();
        } catch (Exception e) {
            Assert.fail(e.getLocalizedMessage());
        }
    }

    @Test
    public void bufferVsByteBuffer() {
        String data = new String("winken\nblinken\n\nand\n\n\nnod");
        CountingInputStream cis;

        try {
            cis = new CountingInputStream(new ByteArrayInputStream(data.getBytes()), true);
            Assert.assertEquals(data, cis.getBufferAsString());

            // test with tiny buffer
            int bufferSize = 4;
            ByteBuffer byteBuffer = Mockito.spy(ByteBuffer.allocate(bufferSize));
            Mockito.when(byteBuffer.limit(Mockito.anyInt())).thenThrow(new NoSuchMethodError());

            cis = new CountingInputStream(new ByteArrayInputStream(data.getBytes()), byteBuffer);
            Assert.assertEquals(cis.getBufferAsString(), data.substring(0, bufferSize));
            Assert.assertEquals(cis.available(), data.length());
            Assert.assertEquals(data, TestUtil.slurp(cis));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private class TestInputStream extends InputStream {

        @Override
        public int read() throws IOException {
            return 0x41;
        }
    }

    private class BufferReader implements Runnable {
        CountingInputStream inputStream;
        int maxLength;
        Random random = new Random();

        public BufferReader(CountingInputStream inputStream, int maxLength) {
            this.inputStream = inputStream;
            this.maxLength = maxLength;
        }

        @Override
        public void run() {
            int offset = random.nextInt(maxLength);
            int readSize = maxLength - offset;

            byte[] b = new byte[maxLength];
            try {
                inputStream.read(b, offset, readSize);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class BufferSkipper implements Runnable {
        CountingInputStream inputStream;
        Random random = new Random();
        int maxLength;

        public BufferSkipper(CountingInputStream inputStream, int maxLength) {
            this.inputStream = inputStream;
            this.maxLength = maxLength;
        }

        @Override
        public void run() {
            try {
                if (random.nextBoolean())
                    inputStream.skip(maxLength);
                else
                    inputStream.skip(0);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
