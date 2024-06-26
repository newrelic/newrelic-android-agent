// automatically generated by the FlatBuffers compiler, do not modify

package com.newrelic.mobile.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class HexAgentDataBundle extends Table {
  public static HexAgentDataBundle getRootAsHexAgentDataBundle(ByteBuffer _bb) { return getRootAsHexAgentDataBundle(_bb, new HexAgentDataBundle()); }
  public static HexAgentDataBundle getRootAsHexAgentDataBundle(ByteBuffer _bb, HexAgentDataBundle obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; vtable_start = bb_pos - bb.getInt(bb_pos); vtable_size = bb.getShort(vtable_start); }
  public HexAgentDataBundle __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public HexAgentData hexAgentData(int j) { return hexAgentData(new HexAgentData(), j); }
  public HexAgentData hexAgentData(HexAgentData obj, int j) { int o = __offset(4); return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null; }
  public int hexAgentDataLength() { int o = __offset(4); return o != 0 ? __vector_len(o) : 0; }

  public static int createHexAgentDataBundle(FlatBufferBuilder builder,
      int hexAgentDataOffset) {
    builder.startObject(1);
    HexAgentDataBundle.addHexAgentData(builder, hexAgentDataOffset);
    return HexAgentDataBundle.endHexAgentDataBundle(builder);
  }

  public static void startHexAgentDataBundle(FlatBufferBuilder builder) { builder.startObject(1); }
  public static void addHexAgentData(FlatBufferBuilder builder, int hexAgentDataOffset) { builder.addOffset(0, hexAgentDataOffset, 0); }
  public static int createHexAgentDataVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startHexAgentDataVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static int endHexAgentDataBundle(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishHexAgentDataBundleBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }
  public static void finishSizePrefixedHexAgentDataBundleBuffer(FlatBufferBuilder builder, int offset) { builder.finishSizePrefixed(offset); }
}

