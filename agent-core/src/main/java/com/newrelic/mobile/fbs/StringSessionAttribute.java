// automatically generated by the FlatBuffers compiler, do not modify

package com.newrelic.mobile.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class StringSessionAttribute extends Table {
  public static StringSessionAttribute getRootAsStringSessionAttribute(ByteBuffer _bb) { return getRootAsStringSessionAttribute(_bb, new StringSessionAttribute()); }
  public static StringSessionAttribute getRootAsStringSessionAttribute(ByteBuffer _bb, StringSessionAttribute obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; vtable_start = bb_pos - bb.getInt(bb_pos); vtable_size = bb.getShort(vtable_start); }
  public StringSessionAttribute __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public String name() { int o = __offset(4); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer nameAsByteBuffer() { return __vector_as_bytebuffer(4, 1); }
  public ByteBuffer nameInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 4, 1); }
  public String value() { int o = __offset(6); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer valueAsByteBuffer() { return __vector_as_bytebuffer(6, 1); }
  public ByteBuffer valueInByteBuffer(ByteBuffer _bb) { return __vector_in_bytebuffer(_bb, 6, 1); }

  public static int createStringSessionAttribute(FlatBufferBuilder builder,
      int nameOffset,
      int valueOffset) {
    builder.startObject(2);
    StringSessionAttribute.addValue(builder, valueOffset);
    StringSessionAttribute.addName(builder, nameOffset);
    return StringSessionAttribute.endStringSessionAttribute(builder);
  }

  public static void startStringSessionAttribute(FlatBufferBuilder builder) { builder.startObject(2); }
  public static void addName(FlatBufferBuilder builder, int nameOffset) { builder.addOffset(0, nameOffset, 0); }
  public static void addValue(FlatBufferBuilder builder, int valueOffset) { builder.addOffset(1, valueOffset, 0); }
  public static int endStringSessionAttribute(FlatBufferBuilder builder) {
    int o = builder.endObject();
    builder.required(o, 4);  // name
    return o;
  }

  @Override
  protected int keysCompare(Integer o1, Integer o2, ByteBuffer _bb) { return compareStrings(__offset(4, o1, _bb), __offset(4, o2, _bb), _bb); }

  public static StringSessionAttribute __lookup_by_key(StringSessionAttribute obj, int vectorLocation, String key, ByteBuffer bb) {
    byte[] byteKey = key.getBytes(Table.UTF8_CHARSET.get());
    int span = bb.getInt(vectorLocation - 4);
    int start = 0;
    while (span != 0) {
      int middle = span / 2;
      int tableOffset = __indirect(vectorLocation + 4 * (start + middle), bb);
      int comp = compareStrings(__offset(4, bb.capacity() - tableOffset, bb), byteKey, bb);
      if (comp > 0) {
        span = middle;
      } else if (comp < 0) {
        middle++;
        start += middle;
        span -= middle;
      } else {
        return (obj == null ? new StringSessionAttribute() : obj).__assign(tableOffset, bb);
      }
    }
    return null;
  }
}

