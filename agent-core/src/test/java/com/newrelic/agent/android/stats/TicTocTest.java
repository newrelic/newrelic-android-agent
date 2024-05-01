package com.newrelic.agent.android.stats;

import org.junit.Assert;
import org.junit.Test;

public class TicTocTest {

    @Test
    public void validateValues() {
        TicToc timer = new TicToc();

        timer.tic();
        long startTime = timer.getStartTime();
        Assert.assertEquals(timer.getState(), TicToc.State.STARTED);

        long calculatedTime = timer.toc();
        long endTime = timer.getEndTime();
        Assert.assertEquals(timer.getState(), TicToc.State.STOPPED);

        Assert.assertEquals(calculatedTime, endTime - startTime);
    }

    @Test
    public void validateStartEndTime() throws InterruptedException {
        TicToc timer = new TicToc();
        timer.tic();
        long startTime = timer.getStartTime();
        Thread.sleep(3000);
        timer.toc();
        long endTime = timer.getEndTime();
        Assert.assertTrue(endTime > startTime);
    }

    @Test
    public void validatePeek() throws InterruptedException {
        TicToc timer = new TicToc();
        timer.tic();
        Thread.sleep(3000);
        Assert.assertTrue(timer.peek() > 0);
        timer.toc();
        Assert.assertTrue(timer.peek() == 0);
    }

    @Test
    public void validateTic() {
        TicToc timer = new TicToc();
        Assert.assertTrue(timer.tic() != null);
        Assert.assertEquals(timer.getState(), TicToc.State.STARTED);
    }

    @Test
    public void validateToc() throws InterruptedException {
        TicToc timer = new TicToc();
        Assert.assertEquals(timer.getState(), null);
        Assert.assertEquals(timer.toc(), -1);

        timer.tic();
        Assert.assertEquals(timer.getState(), TicToc.State.STARTED);
        Thread.sleep(3000);
        Assert.assertTrue(timer.toc() > 0);
        Assert.assertEquals(timer.getState(), TicToc.State.STOPPED);
    }

    @Test
    public void testAgainstMillis() throws InterruptedException {
        long mStart = System.currentTimeMillis();
        TicToc ticToc = new TicToc().tic();
        Thread.sleep(3000);
        ticToc.toc();
        long mEnd = System.currentTimeMillis();
        long nStart = ticToc.getStartTime();
        long nEnd = ticToc.getEndTime();
        Assert.assertTrue((mEnd - mStart) - (nEnd - nStart) < 5);
    }
}