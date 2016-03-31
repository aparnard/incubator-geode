/*=========================================================================
 * Copyright (c) 2002-2014 Pivotal Software, Inc. All Rights Reserved.
 * This product is protected by U.S. and international copyright
 * and intellectual property laws. Pivotal products are covered by
 * more patents listed at http://www.pivotal.io/patents.
 *=========================================================================
 */

package com.gemstone.gemfire.internal.cache;

import java.io.IOException;

import com.gemstone.gemfire.DataSerializer;
import com.gemstone.gemfire.cache.util.ObjectSizer;
import com.gemstone.gemfire.internal.DSCODE;
import com.gemstone.gemfire.internal.HeapDataOutputStream;
import com.gemstone.gemfire.internal.NullDataOutputStream;
import com.gemstone.gemfire.internal.cache.lru.Sizeable;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;
import com.gemstone.gemfire.pdx.PdxInstance;

/**
 * Produces instances that implement CachedDeserializable.
 * @author Darrel
 * @since 5.0.2
 *
 */
public class CachedDeserializableFactory {
  private static final boolean PREFER_DESERIALIZED = ! Boolean.getBoolean("gemfire.PREFER_SERIALIZED");
  private static final boolean STORE_ALL_VALUE_FORMS = Boolean.getBoolean("gemfire.STORE_ALL_VALUE_FORMS");

  /**
   * Currently GFE always wants a CachedDeserializable wrapper.
   */
  public static final boolean preferObject() {
    return false;
  }
  
  /**
   * Creates and returns an instance of CachedDeserializable that contains the
   * specified byte array.
   */
  public static CachedDeserializable create(byte[] v) {
    if (STORE_ALL_VALUE_FORMS) {
      return new StoreAllCachedDeserializable(v);
    }
    else if (PREFER_DESERIALIZED) {
      if (isPdxEncoded(v) && cachePrefersPdx()) {
        return new PreferBytesCachedDeserializable(v);
      } else {
        return new VMCachedDeserializable(v);
      }
    } else {
      return new PreferBytesCachedDeserializable(v);
    }
  }
  
  private static boolean isPdxEncoded(byte[] v) {
    // assert v != null;
    if (v.length > 0) {
      return v[0] == DSCODE.PDX;
    }
    return false;
  }

  /**
   * Creates and returns an instance of CachedDeserializable that contains the
   * specified object (that is not a byte[]).
   */
  public static CachedDeserializable create(Object object, int serializedSize) {
    if (STORE_ALL_VALUE_FORMS) {
      return new StoreAllCachedDeserializable(object);
    }
    else if (PREFER_DESERIALIZED) {
      if (object instanceof PdxInstance && cachePrefersPdx()) {
        return new PreferBytesCachedDeserializable(object);

      } else {
        return new VMCachedDeserializable(object, serializedSize);
      }
    } else {
      return new PreferBytesCachedDeserializable(object);
    }
  }

  private static boolean cachePrefersPdx() {
    GemFireCacheImpl gfc = GemFireCacheImpl.getInstance();
    if (gfc != null) {
      return gfc.getPdxReadSerialized();
    }
    return false;
  }

  /**
   * Wrap cd in a new CachedDeserializable.
   */
  public static CachedDeserializable create(CachedDeserializable cd) {
    if (STORE_ALL_VALUE_FORMS) {
      // storeAll cds are immutable just return it w/o wrapping
      return cd;
    }
    else if (PREFER_DESERIALIZED) {
      if (cd instanceof PreferBytesCachedDeserializable) {
        return cd;
      } else {
        return new VMCachedDeserializable((VMCachedDeserializable) cd);
      }
    } else {
      // preferBytes cds are immutable so just return it w/o wrapping
      return cd;
    }
  }

  /**
   * Return the heap overhead in bytes for each CachedDeserializable instance.
   */
  public static int overhead() {
    // TODO: revisit this code. If we move to per-region cds then this can no longer be static.
    // TODO: This method also does not work well with the way off heap is determined using the cache.
    
    if (STORE_ALL_VALUE_FORMS) {
      return StoreAllCachedDeserializable.MEM_OVERHEAD;
    }
    else if (PREFER_DESERIALIZED) {
      // PDX: this may instead be PreferBytesCachedDeserializable.MEM_OVERHEAD
      return VMCachedDeserializable.MEM_OVERHEAD;
    } else {
      return PreferBytesCachedDeserializable.MEM_OVERHEAD;
    }
    
  }
  /**
   * Return the number of bytes the specified byte array will consume
   * of heap memory.
   */
  public static int getByteSize(byte[] serializedValue) {
    // add 4 for the length field of the byte[]
    return serializedValue.length + Sizeable.PER_OBJECT_OVERHEAD + 4;
  }

  public static int getArrayOfBytesSize(final byte[][] value,
      final boolean addObjectOverhead) {
    int result = 4 * (value.length + 1);
    if (addObjectOverhead) {
      result += Sizeable.PER_OBJECT_OVERHEAD * (value.length + 1);
    }
    for (byte[] bytes : value) {
      if (bytes != null) {
        result += bytes.length;
      }
    }
    return result;
  }

  /**
   * Return an estimate of the amount of heap memory used for the object.
   * If it is not a byte[] then account for CachedDeserializable overhead.
   * when it is wrapped by a CachedDeserializable.
   */
  public static int calcMemSize(Object o) {
    return calcMemSize(o, null, true);
  }
  public static int calcMemSize(Object o, ObjectSizer os, boolean addOverhead) {
    return calcMemSize(o, os, addOverhead, true);
  }
  /**
   * If not calcSerializedSize then return -1 if we can't figure out the mem size.
   */
  public static int calcMemSize(Object o, ObjectSizer os, boolean addOverhead, boolean calcSerializedSize) {
    int result;
    if (o instanceof byte[]) {
      // does not need to be wrapped so overhead never added
      result = getByteSize((byte[])o);
      addOverhead = false;
    } else if (o == null) {
      // does not need to be wrapped so overhead never added
      result = 0;
      addOverhead = false;
    } else if (o instanceof String) {
      result = (((String)o).length() * 2)
        + 4 // for the length of the char[]
        + (Sizeable.PER_OBJECT_OVERHEAD * 2) // for String obj and Char[] obj
        + 4 // for obj ref to char[] on String; note should be 8 on 64-bit vm
        + 4 // for offset int field on String
        + 4 // for count int field on String
        + 4 // for hash int field on String
        ;
    } else if (o instanceof byte[][]) {
      result = getArrayOfBytesSize((byte[][])o, true);
      addOverhead = false;
    } else if (o instanceof CachedDeserializable) {
      // overhead never added
      result = ((CachedDeserializable)o).getSizeInBytes();
      addOverhead = false;
    } else if (o instanceof Sizeable) {
      result = ((Sizeable)o).getSizeInBytes();
    } else if (os != null) {
      result = os.sizeof(o);
    } else if (calcSerializedSize) {
      result = Sizeable.PER_OBJECT_OVERHEAD + 4;
      NullDataOutputStream dos = new NullDataOutputStream();
      try {
        DataSerializer.writeObject(o, dos);
        result += dos.size();
      } catch (IOException ex) {
        RuntimeException ex2 = new IllegalArgumentException(LocalizedStrings.CachedDeserializableFactory_COULD_NOT_CALCULATE_SIZE_OF_OBJECT.toLocalizedString());
        ex2.initCause(ex);
        throw ex2;
      }
    } else {
      // return -1 to signal the caller that we did not compute the size
      result = -1;
      addOverhead = false;
    }
    if (addOverhead) {
      result += overhead();
    }
//     GemFireCache.getInstance().getLogger().info("DEBUG calcMemSize: o=<" + o + "> o.class=" + (o != null ? o.getClass() : "<null>") + " os=" + os + " result=" + result, new RuntimeException("STACK"));
    return result;
  }
  /**
   * Return an estimate of the number of bytes this object will consume
   * when serialized. This is the number of bytes that will be written
   * on the wire including the 4 bytes needed to encode the length.
   */
  public static int calcSerializedSize(Object o) {
    int result;
    if (o instanceof byte[]) {
      result = getByteSize((byte[])o) - Sizeable.PER_OBJECT_OVERHEAD;
    } else if (o instanceof byte[][]) {
      result = getArrayOfBytesSize((byte[][])o, false);
    } else if (o instanceof CachedDeserializable) {
      result = ((CachedDeserializable)o).getSizeInBytes() + 4 - overhead();
    } else if (o instanceof Sizeable) {
      result = ((Sizeable)o).getSizeInBytes() + 4;
    } else if (o instanceof HeapDataOutputStream) {
      result = ((HeapDataOutputStream)o).size() + 4;
    } else {
      result = 4;
      NullDataOutputStream dos = new NullDataOutputStream();
      try {
        DataSerializer.writeObject(o, dos);
        result += dos.size();
      } catch (IOException ex) {
        RuntimeException ex2 = new IllegalArgumentException(LocalizedStrings.CachedDeserializableFactory_COULD_NOT_CALCULATE_SIZE_OF_OBJECT.toLocalizedString());
        ex2.initCause(ex);
        throw ex2;
      }
    }
//     GemFireCache.getInstance().getLogger().info("DEBUG calcSerializedSize: o=<" + o + "> o.class=" + (o != null ? o.getClass() : "<null>") + " result=" + result, new RuntimeException("STACK"));
    return result;
  }
  /**
   * Return how much memory this object will consume
   * if it is in serialized form
   * @since 6.1.2.9
   */
  public static int calcSerializedMemSize(Object o) {
    int result = calcSerializedSize(o);
    result += Sizeable.PER_OBJECT_OVERHEAD;
    if (!(o instanceof byte[])) {
      result += overhead();
    }
    return result;
  }
}