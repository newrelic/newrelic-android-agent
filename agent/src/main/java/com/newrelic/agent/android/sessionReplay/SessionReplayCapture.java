package com.newrelic.agent.android.sessionReplay;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.TextView;

import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.ChildNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SessionReplayCapture {
    private SessionReplayThingyRecorder recorder;

    public SessionReplayThingy capture(View rootView) {
        recorder = new SessionReplayThingyRecorder(rootView.getResources().getDisplayMetrics().density);
        return recursivelyCapture(rootView);
    }

    private SessionReplayThingy recursivelyCapture(View rootView) {
        SessionReplayThingy viewThingy = recorder.recordView(rootView);

        return viewThingy;
    }
}


//private void logChildViews(ViewGroup viewGroup, ChildNode htmlChildNode, ChildNode styleNode) {
//    for (int i = 0; i < viewGroup.getChildCount(); i++) {
//        View child = viewGroup.getChildAt(i);
//        if(shouldPrintView(child)) {
//
//            int id = NewRelicIdGenerator.generateId();
//            String attributeId =   child.getClass().getSimpleName()+"-"+id;
//
//            int[] location = new int[2];
//            child.getLocationOnScreen(location);
//            Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: getLocationOnScreen " + "X: " + location[0] + " Y: " + location[1]);
//
//            String styleContent ="#"+ attributeId + "{position: absolute; top: " + getPixel(location[1]) + "px; left: " + getPixel(location[0])+ "px; width: " + getPixel(child.getWidth()) + "px; height: " + getPixel(child.getHeight()) + "px;";
//            ChildNode childViewNode = new ChildNode(new ArrayList<>(),id,"div",2,null,null,false);
//
//            Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + child.getClass().getSimpleName());
//            Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "X: " + child.getPivotX() + " Y: " + child.getPivotY());
//            Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "Width: " + child.getWidth() + " Height: " + child.getHeight());
//            Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "Visibility: " + child.getVisibility());
//            Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "Alpha: " + child.getAlpha());
//
//
//
//
//            int[] locationOnWindows = new int[2];
//            child.getLocationInWindow(locationOnWindows);
//            Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: getLocationInWindow" + "X: " + locationOnWindows[0] + " Y: " + locationOnWindows[1]);
//
//            if(child.getBackground() != null){
//                Drawable background = child.getBackground();
//
//
////                        if(background instanceof ColorDrawable || background instanceof RippleDrawable || background instanceof InsetDrawable || background instanceof BitmapDrawable){
////                            styleContent += "background-color: #" + toRGBColor(background) + ";";
////                        }
//
//                if(background instanceof GradientDrawable){
//                    GradientDrawable gradientDrawable = (GradientDrawable) background;
//                    styleContent += convertToCSS(gradientDrawable);
//                }
//
//
//            }
//
//            if(child.getBackgroundTintList() != null){
//                styleContent += "background-color: #" + Integer.toHexString(child.getBackgroundTintList().getColorForState(new int[] {android.R.attr.state_enabled},0)).substring(2) + ";";
//            }
//
//            if(child instanceof DatePicker) {
//                ChildNode datePickerElement = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"input",2,null,null,false);
//                Attributes attributes = new Attributes(String.valueOf(NewRelicIdGenerator.generateId()));
//                attributes.setType("date");
//                datePickerElement.setAttributes(attributes);
//                childViewNode.getChildNodes().add(datePickerElement);
//            }
//
//            if(child instanceof NumberPicker) {
//                ChildNode numberPickerElement = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"input",2,null,null,false);
//                Attributes attributes = new Attributes(String.valueOf(NewRelicIdGenerator.generateId()));
//                attributes.setType("number");
//                numberPickerElement.setAttributes(attributes);
//                childViewNode.getChildNodes().add(numberPickerElement);
//            }
//
//            if(child instanceof CheckBox){
//                ChildNode checkBoxElement = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"input",2,null,null,false);
//                Attributes attributes = new Attributes(String.valueOf(NewRelicIdGenerator.generateId()));
//                attributes.setType("checkbox");
//                checkBoxElement.setAttributes(attributes);
//                childViewNode.getChildNodes().add(checkBoxElement);
//
//            }
//
//
//            if (child instanceof CheckedTextView) {
//                ChildNode checkBoxElement = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"input",2,null,null,false);
//                Attributes attributes = new Attributes(String.valueOf(NewRelicIdGenerator.generateId()));
//                attributes.setType("checkbox");
//                checkBoxElement.setAttributes(attributes);
//                childViewNode.getChildNodes().add(checkBoxElement);
//            }
//
//            if(child instanceof RadioButton){
//                ChildNode radioButtonElement = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"input",2,null,null,false);
//                Attributes attributes = new Attributes(String.valueOf(NewRelicIdGenerator.generateId()));
//                attributes.setType("radio");
//                //TODO: Add checked attribute
//                Map<String, String> metadata = new HashMap<>();
//                metadata.put("checked", String.valueOf(((RadioButton) child).isChecked()));
//                attributes.setMetadata(metadata);
//                radioButtonElement.setAttributes(attributes);
//                childViewNode.getChildNodes().add(radioButtonElement);
//            }
//
//            if (child instanceof TextView) {
//                ChildNode textElement = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"p",2,null,null,false);
//                ChildNode textNode = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"",3,null,null,false);
//                textNode.setType(3);
//                textNode.setTextContent(((TextView) child).getText().toString());
//                Typeface typeface = ((TextView) child).getTypeface();
//                styleContent += "font-size: " + getPixel(((TextView) child).getTextSize()) + "px; color: #" + Integer.toHexString(((TextView) child).getCurrentTextColor()).substring(2) + ";"+"font-family: "+getFrontFamily(typeface)+";"+convertGravityToCSS(((TextView) child).getGravity());
//                Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "Text: " + ((TextView) child).getText());
//                Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "Size: " + ((TextView) child).getTextSize());
//                Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "Size: " + ((TextView) child).getTypeface());
//                Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "Text: #" + Integer.toHexString(((TextView) child).getCurrentTextColor()).substring(2));
//                childViewNode.getChildNodes().add(textNode);
//            }
//
//            if (child instanceof ImageView) {
//                styleContent += "background-color: #FF474C;";
//                Log.d(TAG, "SessionReplayActivityLifecycleCallbacks: " + "BackGround: " + ((ImageView) child).getDrawable());
//            }
//
//            if (child instanceof EditText) {
//                ChildNode textNode = new ChildNode(new ArrayList<>(),NewRelicIdGenerator.generateId(),"",3,null,null,false);
//                textNode.setType(3);
//                if(((EditText) child).getText().toString().isEmpty()) {
//                    if(((EditText) child).getHint() != null) {
//                        textNode.setTextContent(((EditText) child).getHint().toString());
//                    }
//                }
//                Typeface typeface = ((EditText) child).getTypeface();
//                styleContent += "font-size: " + getPixel(((EditText) child).getTextSize()) + "px; color: #" + Integer.toHexString(((EditText) child).getCurrentTextColor()).substring(2) + ";"+"font-family: "+getFrontFamily(typeface)+";";
//            }
//
//
//
//            childViewNode.setAttributes(new Attributes(attributeId));
//
//            styleContent += "}";
//
//            styleNode.textContent += styleContent;
//
//            htmlChildNode.getChildNodes().add(childViewNode);
//
//
//        }
//        if (child instanceof ViewGroup) {
//            logChildViews((ViewGroup) child,htmlChildNode,styleNode);
//        }
//    }
//}