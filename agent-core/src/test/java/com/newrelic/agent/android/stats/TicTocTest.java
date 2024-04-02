package com.newrelic.agent.android.stats;

import org.junit.Assert;
import org.junit.Test;

public class TicTocTest {

    @Test
    public void validateValues(){
        TicToc timer = new TicToc();

        timer.tic();
        long startTime = timer.getStartTime();
        Assert.assertEquals(timer.getState(), TicToc.State.STARTED);

        long calculatedTime = timer.toc();
        long endTime = timer.getEndTime();
        Assert.assertEquals(timer.getState(), TicToc.State.STOPPED);

        Assert.assertEquals(calculatedTime, endTime-startTime);
    }
}
