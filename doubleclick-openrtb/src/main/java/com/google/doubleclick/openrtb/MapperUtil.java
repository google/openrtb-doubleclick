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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import com.google.protobuf.ByteString;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.Collections;

/**
 * Utilities for Mappers.
 */
public class MapperUtil {
  private static final char[] HEX = new char[] {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
  };

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

  public static <T> ImmutableCollection<T> get(ImmutableSet<T>[] sets, int i) {
    return i >= 0 && i < sets.length && sets[i] != null
        ? sets[i]
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

  public static <E extends Enum<E>, T> ImmutableCollection<T> get(ImmutableSet<T>[] sets, E e) {
    return e != null && e.ordinal() < sets.length && sets[e.ordinal()] != null
        ? sets[e.ordinal()]
        : ImmutableSet.<T>of();
  }

  public static String toHexString(ByteString bytes) {
    StringBuilder sb = new StringBuilder(bytes.size() * 2);
    toHexString(bytes, 0, bytes.size(), sb);
    return sb.toString();
  }

  public static void toHexString(ByteString bytes, int from, int to, StringBuilder sb) {
    for (int i = from; i < to; ++i) {
      toHexString(bytes.byteAt(i), sb);
    }
  }

  protected static void toHexString(int b, StringBuilder sb) {
    sb.append(HEX[(b >> 4) & 0x0F]);
    sb.append(HEX[b & 0x0F]);
  }

  public static ByteString toByteString(String str) {
    ByteString.Output bs = ByteString.newOutput(str.length() / 2);

    for (int i = 0; i < str.length(); ) {
      char c1 = str.charAt(i++);
      char c2 = str.charAt(i++);
      int b = (parseHexDigit(c1) << 4) | parseHexDigit(c2);
      bs.write(b);
    }

    return bs.toByteString();
  }

  private static int parseHexDigit(char c) {
    return c <= '9' ? c - '0' : c - 'A' + 10;
  }

  public static String toIpv4String(ByteString bytes) {
    StringBuilder sb = new StringBuilder(bytes.size() * 4);

    for (int i = 0; i < bytes.size(); ++i) {
      if (i != 0) {
        sb.append('.');
      }
      sb.append(bytes.byteAt(i) & 0xFF);
    }

    if (bytes.size() == 3) {
      sb.append(".0");
    }

    return sb.toString();
  }

  public static String toIpv6String(ByteString bytes) {
    try {
      byte[] ipv6 = new byte[16];
      bytes.copyTo(ipv6, 0);
      return InetAddresses.toAddrString(InetAddress.getByAddress(ipv6));
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("ip=" + toHexString(bytes));
    }
  }

  protected static String decodeUri(String uri) {
    try {
      return URLDecoder.decode(uri, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new MapperException(e.getMessage());
    }
  }
}
