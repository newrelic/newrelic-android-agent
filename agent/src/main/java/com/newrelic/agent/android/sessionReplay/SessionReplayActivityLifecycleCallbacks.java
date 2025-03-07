package com.newrelic.agent.android.sessionReplay;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.sessionReplay.internal.Curtains;
import com.newrelic.agent.android.sessionReplay.internal.OnRootViewsChangedListener;
import com.newrelic.agent.android.sessionReplay.internal.OnTouchEventListener;
import com.newrelic.agent.android.sessionReplay.internal.WindowCallbackWrapper;
import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.ChildNode;
import com.newrelic.agent.android.sessionReplay.models.Data;
import com.newrelic.agent.android.sessionReplay.models.InitialOffset;
import com.newrelic.agent.android.sessionReplay.models.Node;
import com.newrelic.agent.android.sessionReplay.models.RRWebTouch;
import com.newrelic.agent.android.sessionReplay.models.RecordedTouchData;
import com.newrelic.agent.android.sessionReplay.models.SessionReplayRoot;
import com.newrelic.agent.android.sessionReplay.models.RRWebTouch;
import com.newrelic.agent.android.stores.SharedPrefsSessionReplayStore;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class SessionReplayActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
    ArrayList<SessionReplayRoot> sessionReplayRoots = new ArrayList<>();

    ArrayList<RRWebTouch> RRWebTouches = new ArrayList<>();
    ArrayList<Touch> touches = new ArrayList<>();
    private static AgentConfiguration agentConfiguration = new AgentConfiguration();
    WeakReference mrootView;
    private static final String TAG = "SessionReplayActivityLifecycleCallbacks";
    int i  = 0 ;
    private float density;
    private long firstTimestamp = 0;
    private int currentTouchId = -1;
    private ArrayList<TouchTracker> touchTrackers = new ArrayList<>();
    private TouchTracker currentTouchTracker = null;

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {

    }

    @Override
    public void onActivityPrePaused(@NonNull Activity activity) {

        Gson gson = new Gson();
        String json = gson.toJson(sessionReplayRoots);
        Log.d(TAG, "onActivityPrePaused: " + json);
        Application.ActivityLifecycleCallbacks.super.onActivityPrePaused(activity);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        Log.d(TAG, "onActivityResumed: " + activity.getClass().getSimpleName());
        firstTimestamp = System.currentTimeMillis();

        Curtains.getOnRootViewsChangedListeners().add(new OnRootViewsChangedListener() {
            @Override
            public void onRootViewsChanged(View view, boolean added) {
                Log.d(TAG, "Root View Changed in Listener");
                Window window = Windows.getPhoneWindowForView(view);
                WindowCallbackWrapper.getListeners(window).getTouchEventInterceptors().add(new OnTouchEventListener() {
                       @Override
                       public void onTouchEvent(MotionEvent motionEvent) {
//                           Log.d(TAG, "Received Motion Event: " + motionEvent.toString());
                           long timestamp = System.currentTimeMillis();
                           MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
                           motionEvent.getPointerCoords(0, pointerCoords);

                           if(currentTouchId == -1) {
//                               currentTouchId = NewRelicIdGenerator.generateId();
                               View containingView = findViewAtCoords(view, (int)pointerCoords.x, (int)pointerCoords.y);
                               if(containingView != null) {
                                   currentTouchId = getStableId(containingView);
                                   Log.d(TAG, "Found originating View. Id is " + currentTouchId);
                               } else {
                                   Log.d(TAG, "Unable to find originating View. Generating ID");
                                   currentTouchId = NewRelicIdGenerator.generateId();
                               }
                           }

                           RecordedTouchData moveTouch;
                           if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                               if (currentTouchTracker == null) {
                                   Log.d(TAG, "Adding Start Event");
                                   moveTouch = new RecordedTouchData(0, currentTouchId, getPixel(pointerCoords.x), getPixel(pointerCoords.y), timestamp);
                                   currentTouchTracker = new TouchTracker(moveTouch);
                               }
                           } else if (motionEvent.getActionMasked() == MotionEvent.ACTION_MOVE) {
                               if (SessionReplayActivityLifecycleCallbacks.this.currentTouchTracker != null) {
                                   Log.d(TAG, "Adding Move Event");
                                   moveTouch = new RecordedTouchData(2, currentTouchId, getPixel(pointerCoords.x), SessionReplayActivityLifecycleCallbacks.this.getPixel(pointerCoords.y), timestamp);
                                   SessionReplayActivityLifecycleCallbacks.this.currentTouchTracker.addMoveTouch(moveTouch);
                               }
                           } else if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP && currentTouchTracker != null) {
                               Log.d(TAG, "Adding End Event");
                               moveTouch = new RecordedTouchData(1, currentTouchId, getPixel(pointerCoords.x), getPixel(pointerCoords.y), timestamp);
                               currentTouchTracker.addEndTouch(moveTouch);
                               touchTrackers.add(SessionReplayActivityLifecycleCallbacks.this.currentTouchTracker);
                               currentTouchTracker = null;
                               currentTouchId = -1;
                           }

                       }
                   }
                );

            }
        });

        View rootView = activity.getWindow().getDecorView().getRootView();
        mrootView = new WeakReference(rootView);
//        if (rootView instanceof ViewGroup) {
//            logChildViews((ViewGroup) rootView);
//        }

//        density = activity.getResources().getDisplayMetrics().density;

//        int heightPixels = activity.getResources().getDisplayMetrics().heightPixels;
//
//        int widthPixels = activity.getResources().getDisplayMetrics().widthPixels;
//
//        Log.d(TAG, "onActivityResumed: " + "Height: " + heightPixels + " Width: " + widthPixels + " Density: " + density);
//
//
//        Log.d(TAG, "OnDrawListener: " + rootView.getClass().getSimpleName());
//        rootView.getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
//            @Override
//            public void onDraw() {
//
//
//                    SessionReplayRoot sessionReplayRoot = new SessionReplayRoot(2,null,System.currentTimeMillis());
//
//                    Node node = new Node(0,NewRelicIdGenerator.generateId(),new ArrayList<>());
//
//                    InitialOffset initialOffset = new InitialOffset(0,0);
//
//                    ChildNode htmlChildNode = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"html",2,null,null,false);
//
//                    ChildNode bodyHeaderNode = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"head",2,null,null,false);
//
//                    ChildNode styleNode = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"style",2,null,null,false);
//
//                    ChildNode bodyNode = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"body",2,null,null,false);
//
//                    bodyHeaderNode.getChildNodes().add(styleNode);
//
//                    bodyNode.getChildNodes().add(bodyHeaderNode);
//
//                    htmlChildNode.getChildNodes().add(bodyNode);
//
//                    node.getChildNodes().add(htmlChildNode);
//
//                    Data data = new Data(initialOffset,node);
//
//                    sessionReplayRoot.setData(data);
//
//                    ChildNode styleChildNode = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"",3,null,"",true);
//
//
//
//
//                    i++;
//                    Log.d(TAG, "OnDrawListener: " + "onDraw");
////                    sessionReplayRoots.add(sessionReplayRoot);
//                    if (rootView instanceof ViewGroup) {
//                        logChildViews((ViewGroup) rootView,htmlChildNode,styleChildNode);
//
//
//                        styleNode.getChildNodes().add(styleChildNode);
//
//
//                        sessionReplayRoots.add(sessionReplayRoot);
//
//                        String json = new Gson().toJson(sessionReplayRoots);
//
//                        ArrayList<RRWebTouch> totalTouches = new ArrayList<>();
//                        for(TouchTracker touchTracker : touchTrackers) {
//                            totalTouches.addAll(touchTracker.processTouchData());
//                        }
//                        String touchJson = new Gson().toJson(totalTouches);
//
//                        Log.d(TAG, "first timestamp: " + firstTimestamp);
//                        Log.d(TAG, "jsonPayloadForRRWEB: " + json);
//                        Log.d(TAG, "touchJsonPayloadForRRWEB: " + touchJson);
//
//                        SharedPreferences sharedPreferences = activity.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
//                        SharedPreferences.Editor editor = sharedPreferences.edit();
//                        editor.putString("SessionReplayFrame", json);
//                        editor.apply();
//                    }
//                }
//        });
    }

    private void logChildViews(ViewGroup viewGroup,ChildNode htmlChildNode,ChildNode styleNode) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
                if(shouldPrintView(child)) {

                    int id = getStableId(child);

                    String attributeId =   child.getClass().getSimpleName()+"-"+id;

                    int[] location = new int[2];
                    child.getLocationOnScreen(location);
                    Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: getLocationOnScreen " + "X: " + location[0] + " Y: " + location[1]);

                    String styleContent ="#"+ attributeId + "{position: absolute; top: " + getPixel(location[1]) + "px; left: " + getPixel(location[0])+ "px; width: " + getPixel(child.getWidth()) + "px; height: " + getPixel(child.getHeight()) + "px;";
                    ChildNode childViewNode = new ChildNode(new ArrayList<>(),id,"div",2,null,null,false);

                    Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + child.getClass().getSimpleName());
                    Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "X: " + child.getPivotX() + " Y: " + child.getPivotY());
                    Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "Width: " + child.getWidth() + " Height: " + child.getHeight());
                    Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "Visibility: " + child.getVisibility());
                    Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "Alpha: " + child.getAlpha());




                    int[] locationOnWindows = new int[2];
                    child.getLocationInWindow(locationOnWindows);
                    Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: getLocationInWindow" + "X: " + locationOnWindows[0] + " Y: " + locationOnWindows[1]);

                    if(child.getBackground() != null){
                        Drawable background = child.getBackground();


//                        if(background instanceof ColorDrawable || background instanceof RippleDrawable || background instanceof InsetDrawable || background instanceof BitmapDrawable){
//                            styleContent += "background-color: #" + toRGBColor(background) + ";";
//                        }

                        if(background instanceof GradientDrawable){
                            GradientDrawable gradientDrawable = (GradientDrawable) background;
                            styleContent += convertToCSS(gradientDrawable);
                        }


                    }

                    if(child.getBackgroundTintList() != null){
                        styleContent += "background-color: #" + Integer.toHexString(child.getBackgroundTintList().getColorForState(new int[] {android.R.attr.state_enabled},0)).substring(2) + ";";
                    }

                    if(child instanceof DatePicker) {
                        ChildNode datePickerElement = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"input",2,null,null,false);
                        Attributes attributes = new Attributes(String.valueOf(NewRelicIdGenerator.generateId()));
                        attributes.setType("date");
                        datePickerElement.setAttributes(attributes);
                        childViewNode.getChildNodes().add(datePickerElement);
                    }

                    if(child instanceof NumberPicker) {
                        ChildNode numberPickerElement = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"input",2,null,null,false);
                        Attributes attributes = new Attributes(String.valueOf(NewRelicIdGenerator.generateId()));
                        attributes.setType("number");
                        numberPickerElement.setAttributes(attributes);
                        childViewNode.getChildNodes().add(numberPickerElement);
                    }

                    if(child instanceof CheckBox){
                       ChildNode checkBoxElement = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"input",2,null,null,false);
                       Attributes attributes = new Attributes(String.valueOf(NewRelicIdGenerator.generateId()));
                       attributes.setType("checkbox");
                       checkBoxElement.setAttributes(attributes);
                       childViewNode.getChildNodes().add(checkBoxElement);

                    }


                    if (child instanceof CheckedTextView) {
                        ChildNode checkBoxElement = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"input",2,null,null,false);
                        Attributes attributes = new Attributes(String.valueOf(NewRelicIdGenerator.generateId()));
                        attributes.setType("checkbox");
                        checkBoxElement.setAttributes(attributes);
                        childViewNode.getChildNodes().add(checkBoxElement);
                    }

                    if(child instanceof RadioButton){
                        ChildNode radioButtonElement = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"input",2,null,null,false);
                        Attributes attributes = new Attributes(String.valueOf(NewRelicIdGenerator.generateId()));
                        attributes.setType("radio");
                        //TODO: Add checked attribute
                        Map<String, String> metadata = new HashMap<>();
                        metadata.put("checked", String.valueOf(((RadioButton) child).isChecked()));
                        attributes.setMetadata(metadata);
                        radioButtonElement.setAttributes(attributes);
                        childViewNode.getChildNodes().add(radioButtonElement);
                    }

                    if (child instanceof TextView) {
                        ChildNode textElement = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"p",2,null,null,false);
                        ChildNode textNode = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"",3,null,null,false);
                        textNode.setType(3);
                        textNode.setTextContent(((TextView) child).getText().toString());
                        Typeface typeface = ((TextView) child).getTypeface();
                        styleContent += "font-size: " + getPixel(((TextView) child).getTextSize()) + "px; color: #" + Integer.toHexString(((TextView) child).getCurrentTextColor()).substring(2) + ";"+"font-family: "+getFrontFamily(typeface)+";"+convertGravityToCSS(((TextView) child).getGravity());
                        Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "Text: " + ((TextView) child).getText());
                        Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "Size: " + ((TextView) child).getTextSize());
                        Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "Size: " + ((TextView) child).getTypeface());
                        Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "Text: #" + Integer.toHexString(((TextView) child).getCurrentTextColor()).substring(2));
                        childViewNode.getChildNodes().add(textNode);
                    }

                    if (child instanceof ImageView) {
                        styleContent += "background-color: #FF474C;";
                        Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "BackGround: " + ((ImageView) child).getDrawable());
                    }

                    if (child instanceof EditText) {
                        ChildNode textNode = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"",3,null,null,false);
                        textNode.setType(3);
                        if(((EditText) child).getText().toString().isEmpty()) {
                            if(((EditText) child).getHint() != null) {
                                textNode.setTextContent(((EditText) child).getHint().toString());
                            }
                        }
                        Typeface typeface = ((EditText) child).getTypeface();
                        styleContent += "font-size: " + getPixel(((EditText) child).getTextSize()) + "px; color: #" + Integer.toHexString(((EditText) child).getCurrentTextColor()).substring(2) + ";"+"font-family: "+getFrontFamily(typeface)+";";
                    }



                    childViewNode.setAttributes(new Attributes(attributeId));

                    styleContent += "}";

                    styleNode.textContent += styleContent;

                    htmlChildNode.getChildNodes().add(childViewNode);


                }
            if (child instanceof ViewGroup) {
                logChildViews((ViewGroup) child,htmlChildNode,styleNode);
            }
        }
    }

    private int getStableId(View child) {
        int keyCode = "NewRelicSessionReplay".hashCode();
        Integer idValue = null;
        idValue = (Integer) child.getTag(keyCode);
        if(idValue == null) {
            idValue = NewRelicIdGenerator.generateId();
            child.setTag(keyCode, idValue);
        }
        int id = idValue;
        return id;
    }

    private View findViewAtCoords(View parent, int x, int y) {
        Rect hitRect = new Rect();
        parent.getHitRect(hitRect);

        if(!hitRect.contains(x, y)) {
            return null;
        } else if (parent instanceof ViewGroup) {
            for(int i = 0; i < ((ViewGroup) parent).getChildCount(); i++) {
                View childView = ((ViewGroup) parent).getChildAt(i);
                Rect bounds = new Rect();
                childView.getHitRect(bounds);
                if(bounds.contains(x, y)) {
                    if(childView instanceof ViewGroup) {
                        View foundView = findViewAtCoords(childView, x, y);
                        if(foundView != null && foundView.isShown()) {
                            return foundView;
                        }
                    } else {
                        return childView;
                    }
                }
            }
        }

        return null;
    }

    private float getPixel(float pixel){
        return  (pixel /density);
    }

    private String getFrontFamily(Typeface typeface) {

        if(typeface.equals(Typeface.DEFAULT)){
            return "roboto, sans-serif";
        }
        if(typeface.equals(Typeface.DEFAULT_BOLD)){
            return "sans-serif-bold";
        }
        if(typeface.equals(Typeface.MONOSPACE)){
            return "monospace";
        }
        if(typeface.equals(Typeface.SANS_SERIF)){
            return "sans-serif";
        }
        if(typeface.equals(Typeface.SERIF)){
            return "serif";
        }
        return "roboto, sans-serif";
    }

    public String convertGravityToCSS(int gravity) {
        switch (gravity) {
            case Gravity.TOP:
            case Gravity.BOTTOM:
            case Gravity.CENTER_VERTICAL:
                return "vertical-align: middle;"; // CSS does not have direct equivalents for these
            case Gravity.LEFT:
            case Gravity.START:
                return "text-align: left;";
            case Gravity.RIGHT:
            case Gravity.END:
                return "text-align: right;";
            case Gravity.CENTER:
            case Gravity.CENTER_HORIZONTAL:
                return "text-align: center;";
            default:
                return "text-align: left;"; // Default to left alignment
        }
    }

    public String convertToCSS(GradientDrawable gradientDrawable) {
        StringBuilder css = new StringBuilder();

        Paint fillPaint = getFillPaint(gradientDrawable);
        if (fillPaint != null) {
            css.append("background-color: #").append(Integer.toHexString(fillPaint.getColor()).substring(2)).append(";");
        }

        // Extract corner radius
        float[] cornerRadii = gradientDrawable.getCornerRadii();
        if (cornerRadii != null) {
            css.append(" border-radius: ").append(getPixel(cornerRadii[0])).append("px;");
        } else {
            float cornerRadius = getPixel(gradientDrawable.getCornerRadius());
            if (cornerRadius > 0) {
                css.append(" border-radius: ").append(cornerRadius).append("px;");
            }
        }


        Paint strokePaint = getStrokePaint(gradientDrawable);
        if (strokePaint != null) {
            css.append(" border:").append(getPixel(strokePaint.getStrokeWidth())).append("px").append(" solid #").append(Integer.toHexString(strokePaint.getColor()).substring(2)).append(";");
        }
        return css.toString();
    }



    private boolean shouldPrintView(View child) {

        Rect rootView = new Rect();
        Point point = new Point();

        return child.getGlobalVisibleRect(rootView, point) && child.getVisibility() == View.VISIBLE && child.getAlpha() > 0;
    }


    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {

    }

    public static Paint getFillPaint(GradientDrawable gradientDrawable) {
        try {
            Field mFillPaintField = GradientDrawable.class.getDeclaredField("mFillPaint");
            mFillPaintField.setAccessible(true);
            return (Paint)mFillPaintField.get(gradientDrawable);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Paint getStrokePaint(GradientDrawable gradientDrawable) {
        try {
            Field mStrokePaintPaintField = GradientDrawable.class.getDeclaredField("mStrokePaint");
            mStrokePaintPaintField.setAccessible(true);
            return (Paint)mStrokePaintPaintField.get(gradientDrawable);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String toRGBColor(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return String.format("#%02x%02x%02x", red, green, blue);
    }

    public static String toRGBColor(Drawable drawable) {
        if (drawable instanceof ColorDrawable) {
            return toRGBColor(((ColorDrawable) drawable).getColor());
        } else if (drawable instanceof RippleDrawable) {
            try {
                return toRGBColor(((RippleDrawable) drawable).getDrawable(1));
            } catch (Throwable e) {
                return null; // ignore
            }
        } else if (drawable instanceof InsetDrawable) {
            return toRGBColor(((InsetDrawable) drawable).getDrawable());
        } else if (drawable instanceof GradientDrawable) {
            GradientDrawable gradientDrawable = (GradientDrawable) drawable;
            int[] colors = gradientDrawable.getColors();
            if (colors != null && colors.length > 0) {
                return toRGBColor(colors[0]);
            }
            if (gradientDrawable.getColor() != null && gradientDrawable.getColor().getDefaultColor() != -1) {
                return toRGBColor(gradientDrawable.getColor().getDefaultColor());
            }
        }
        return null;
    }

}

