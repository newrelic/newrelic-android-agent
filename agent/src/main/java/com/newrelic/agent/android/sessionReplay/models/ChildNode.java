package com.newrelic.agent.android.sessionReplay.models;

import java.util.ArrayList;

public class ChildNode{
    public ArrayList<ChildNode> childNodes;
    public int id;
    public String tagName;
    public int type;

    public Attributes attributes;
    public String textContent;
    public boolean isStyle;

    public ChildNode(ArrayList<ChildNode> childNodes, int id, String tagName, int type, Attributes attributes, String textContent, boolean isStyle) {
        this.childNodes = childNodes;
        this.id = id;
        this.tagName = tagName;
        this.type = type;
        this.attributes = attributes;
        this.textContent = textContent;
        this.isStyle = isStyle;
    }

    public ArrayList<ChildNode> getChildNodes() {
        return childNodes;
    }

    public void setChildNodes(ArrayList<ChildNode> childNodes) {
        this.childNodes = childNodes;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes attributes) {
        this.attributes = attributes;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public boolean isStyle() {
        return isStyle;
    }

    public void setStyle(boolean isStyle) {
        this.isStyle = isStyle;
    }



}