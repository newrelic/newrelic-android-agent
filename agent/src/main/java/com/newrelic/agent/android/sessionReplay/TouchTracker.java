package com.newrelic.agent.android.sessionReplay;

import com.newrelic.agent.android.sessionReplay.models.RRWebRRWebTouchUpDownData;
import com.newrelic.agent.android.sessionReplay.models.RRWebTouch;
import com.newrelic.agent.android.sessionReplay.models.RRWebTouchMoveData;
import com.newrelic.agent.android.sessionReplay.models.RecordedTouchData;

import java.util.ArrayList;

public class TouchTracker {
    private RecordedTouchData startTouch;
    private RecordedTouchData endTouch;
    private ArrayList<RecordedTouchData> moveTouches = new ArrayList<>();

    public TouchTracker(RecordedTouchData startTouch) {
        this.startTouch = startTouch;
    }

    public void addMoveTouch(RecordedTouchData touch) {
        moveTouches.add(touch);
    }

    public void addEndTouch(RecordedTouchData touch) {
        endTouch = touch;
    }

    public ArrayList<RRWebTouch> processTouchData() {
        ArrayList<RRWebTouch> touches = new ArrayList<>();

        RRWebRRWebTouchUpDownData downTouch = new RRWebRRWebTouchUpDownData(2, 7, startTouch.originatingViewId, startTouch.xCoordinate, startTouch.yCoordinate);
        touches.add(new RRWebTouch(startTouch.timestamp, 3, downTouch));

        if(!moveTouches.isEmpty()) {
            RecordedTouchData lastTouch = moveTouches.get(moveTouches.size()-1);
            long lastTimestamp = lastTouch.timestamp;
            ArrayList<RRWebTouchMoveData.Position> movePositions = new ArrayList<>();
            for(RecordedTouchData moveTouch : moveTouches) {
                RRWebTouchMoveData.Position position = new RRWebTouchMoveData.Position(moveTouch.originatingViewId, moveTouch.xCoordinate, moveTouch.yCoordinate, (moveTouch.timestamp - lastTimestamp));
                movePositions.add(position);
            }

            RRWebTouchMoveData moveData = new RRWebTouchMoveData(6, movePositions);
            touches.add(new RRWebTouch(lastTimestamp, 3, moveData));
        }

        RRWebRRWebTouchUpDownData upTouch = new RRWebRRWebTouchUpDownData(2, 9, endTouch.originatingViewId, endTouch.xCoordinate, endTouch.yCoordinate);
        touches.add(new RRWebTouch(endTouch.timestamp, 3, upTouch));

        return touches;
    }

}
