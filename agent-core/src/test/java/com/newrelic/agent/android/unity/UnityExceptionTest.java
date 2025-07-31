package com.newrelic.agent.android.unity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UnityExceptionTest {

    @Test
    public void testDefaultConstructor() {
        UnityException ue = new UnityException();
        assertNull(ue.getMessage());
        assertTrue(ue.getStackTrace().length ==0);
    }

    @Test
    public void testWithDetailMessage() {
        UnityException ue = new UnityException("Test Message");
        assertEquals("Test Message", ue.getMessage());
    }

    @Test
    public void testWithSourceExceptionTypeAndDetailMessage() {
        UnityException ue = new UnityException("java.lang.RuntimeException", "Test Message");
        assertEquals("Test Message", ue.getMessage());
        assertEquals("java.lang.RuntimeException", ue.toString());
    }

    @Test
    public void testWithDetailMessageAndStackTraceElements() {
        StackTraceElement[] elements = new StackTraceElement[1];
        elements[0] = new StackTraceElement("TestClass", "testMethod", "TestClass.java", 1);
        UnityException ue = new UnityException("Test Message", elements);
        assertEquals("Test Message", ue.getMessage());
        assertEquals(1, ue.getStackTrace().length);
    }

    @Test
    public void testAppendStackFrame() {
        UnityException ue = new UnityException("Test Message");
        ue.appendStackFrame("TestClass", "testMethod", "TestClass.java", 1);
        StackTraceElement stackFrame = ue.getStackTrace()[ue.getStackTrace().length - 1];
        assertEquals("TestClass", stackFrame.getClassName());
        assertEquals("testMethod", stackFrame.getMethodName());
        assertEquals("TestClass.java", stackFrame.getFileName());
        assertEquals(1, stackFrame.getLineNumber());
    }

    @Test
    public void testSetSourceExceptionType() {
        UnityException ue = new UnityException("Test Message");
        ue.setSourceExceptionType("java.lang.RuntimeException");
        assertEquals("java.lang.RuntimeException", ue.toString());
    }
}
