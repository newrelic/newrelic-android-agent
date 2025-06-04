package com.newrelic.agent.android.sessionReplay.models;

import java.util.List;

public class RRWebElementNode implements RRWebNode {

    public static String TAG_TYPE_HTML = "html";
    public static String TAG_TYPE_HEAD = "head";
    public static String TAG_TYPE_STYLE = "style";
    public static String TAG_TYPE_BODY = "body";
    public static String TAG_TYPE_DIV = "div";

    public int type = RRWEB_NODE_TYPE_ELEMENT;
    public String tagName;
    public Attributes attributes  ;
    public List<RRWebNode> childNodes;
    public int id;

    public RRWebElementNode(Attributes attributes, String tagName, int id, List<RRWebNode> childNodes) {
        this.attributes = attributes;
        this.tagName = tagName;
        this.id = id;
        this.childNodes = childNodes;
    }
}
