package com.newrelic.agent.android.sessionReplay;

import android.view.View;

import java.util.List;

public class SessionReplayThingyRecorder {
    private static String SessionReplayKey = "NewRelicSessionReplayKey";
    private float density;

    public SessionReplayThingyRecorder(float density) {
        this.density = density;
    }

    public SessionReplayThingy recordView(View view, List<SessionReplayThingy> childThingies) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float x = location[0] / density;
        float y = location[1] / density;
        float width = view.getWidth() / density;
        float height = view.getHeight() / density;

        int keyCode = SessionReplayKey.hashCode();
        Integer id;
        id = (Integer) view.getTag(keyCode);
        if(id == null) {
            id = Integer.valueOf(NewRelicIdGenerator.generateId());
            view.setTag(keyCode, id);
        }

        String name = view.getClass().getSimpleName()+"-"+ id;

        SessionReplayThingy.Rect position = new SessionReplayThingy.Rect((int) x, (int) y, (int) width, (int) height);
        return new SessionReplayThingy(position, name, id, childThingies);
    }
}
