/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.doubleclick.openrtb;

import static java.lang.Math.min;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.BaseEncoding;
import com.google.common.net.InetAddresses;
import com.google.protobuf.ByteString;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;

/**
 * Utilities for Mappers.
 */
public class MapperUtil {

  @SuppressWarnings("unchecked")
  public static <T> ImmutableSet<T>[] multimapIntToSets(
      ImmutableMultimap<Integer, T> mmap) {
    int iMax = Collections.max(mmap.keySet());
    ImmutableSet<T>[] setArray = new ImmutableSet[iMax + 1];
    for (Integer key : mmap.keySet()) {
      setArray[key] = ImmutableSet.copyOf(mmap.get(key));
    }
    return setArray;
  }

  @SuppressWarnings("unchecked")
  public static <E extends Enum<E>> ImmutableSet<E>[] multimapIntToEnumSets(
      ImmutableMultimap<Integer, E> mmap) {
    int iMax = Collections.max(mmap.keySet());
    ImmutableSet<E>[] setArray = new ImmutableSet[iMax + 1];
    for (Integer key : mmap.keySet()) {
      setArray[key] = Sets.immutableEnumSet(mmap.get(key));
    }
    return setArray;
  }

  public static <T> ImmutableSet<T> get(ImmutableSet<T>[] sets, int i) {
    return i >= 0 && i < sets.length && sets[i] != null
        ? sets[i]
        : ImmutableSet.<T>of();
  }

  public static <E extends Enum<E>, T> ImmutableSet<T> get(ImmutableSet<T>[] sets, E e) {
    return e != null && e.ordinal() < sets.length && sets[e.ordinal()] != null
        ? sets[e.ordinal()]
        : ImmutableSet.<T>of();
  }

  @SuppressWarnings("unchecked")
  public static <E extends Enum<E>, T> ImmutableSet<T>[] multimapEnumToSets(
      ImmutableMultimap<E, T> mmap) {
    E iMax = Collections.max(mmap.keySet());
    ImmutableSet<T>[] setArray = new ImmutableSet[iMax.ordinal() + 1];
    for (E key : mmap.keySet()) {
      setArray[key.ordinal()] = ImmutableSet.copyOf(mmap.get(key));
    }
    return setArray;
  }

  public static String toIpv4String(ByteString bytes) {
    StringBuilder sb = new StringBuilder(15);
    int size = min(4, bytes.size());

    for (int i = 0; i < size; ++i) {
      if (i != 0) {
        sb.append('.');
      }
      sb.append(bytes.byteAt(i) & 0xFF);
    }

    while (size++ < 4) {
      sb.append(".0");
    }

    return sb.toString();
  }

  public static String toIpv6String(ByteString bytes) {
    try {
      byte[] ipv6 = new byte[16];
      bytes.copyTo(ipv6, 0, 0, min(ipv6.length, bytes.size()));
      return InetAddresses.toAddrString(InetAddress.getByAddress(ipv6));
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("ip=" + BaseEncoding.base16().encode(bytes.toByteArray()));
    }
  }

  public static String toBase64(ByteString bytes) {
    return Base64.getEncoder().withoutPadding().encodeToString(bytes.toByteArray());
  }

  public static String toUUID(ByteString bytes, boolean iOS) {
    if (bytes.size() == 16) {
      String uuid = new UUID(readRawBigEndian64(bytes, 0), readRawBigEndian64(bytes, 8)).toString();
      return iOS ? uuid.toUpperCase() : uuid;
    }
    return BaseEncoding.base16().encode(bytes.toByteArray());
  }

  private static long readRawBigEndian64(ByteString s, int pos) {
    return (((s.byteAt(pos + 7) & 0xffL))       |
            ((s.byteAt(pos + 6) & 0xffL) <<  8) |
            ((s.byteAt(pos + 5) & 0xffL) << 16) |
            ((s.byteAt(pos + 4) & 0xffL) << 24) |
            ((s.byteAt(pos + 3) & 0xffL) << 32) |
            ((s.byteAt(pos + 2) & 0xffL) << 40) |
            ((s.byteAt(pos + 1) & 0xffL) << 48) |
            ((s.byteAt(pos + 0) & 0xffL) << 56));
  }


  protected static String decodeUri(String uri) {
    try {
      return URLDecoder.decode(uri, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new MapperException(e.getMessage());
    }
  }
}
