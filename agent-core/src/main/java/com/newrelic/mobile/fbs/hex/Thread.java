// automatically generated by the FlatBuffers compiler, do not modify

package com.newrelic.mobile.fbs.hex;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class Thread extends Table {
  public static Thread getRootAsThread(ByteBuffer _bb) { return getRootAsThread(_bb, new Thread()); }
  public static Thread getRootAsThread(ByteBuffer _bb, Thread obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; vtable_start = bb_pos - bb.getInt(bb_pos); vtable_size = bb.getShort(vtable_start); }
  public Thread __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public Frame frames(int j) { return frames(new Frame(), j); }
  public Frame frames(Frame obj, int j) { int o = __offset(4); return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null; }
  public int framesLength() { int o = __offset(4); return o != 0 ? __vector_len(o) : 0; }

  public static int createThread(FlatBufferBuilder builder,
      int framesOffset) {
    builder.startObject(1);
    Thread.addFrames(builder, framesOffset);
    return Thread.endThread(builder);
  }

  public static void startThread(FlatBufferBuilder builder) { builder.startObject(1); }
  public static void addFrames(FlatBufferBuilder builder, int framesOffset) { builder.addOffset(0, framesOffset, 0); }
  public static int createFramesVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startFramesVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static int endThread(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

