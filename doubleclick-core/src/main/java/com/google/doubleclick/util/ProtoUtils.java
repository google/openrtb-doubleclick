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

package com.google.doubleclick.util;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.MessageLiteOrBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Partial, private copy of the ProtoUtils class in openrtb-core,
 * so we don't need to add a dependency from openrtb-core just for this.
 */
final class ProtoUtils {

  /**
   * Runs a filter through a sequence of objects.
   *
   * @param objs Message-or-builder objects
   * @param filter Function that returns {@code true} to retain an object, {@code false} to discard
   * @return Retained objects. If some elements are retained and others are discarded,
   * this will be a new, mutable {@link List} that contains only the retained elements.
   * If all elements are retained, returns the same, unmodified input sequence.
   * If all elements are discarded, returns an immutable, empty sequence
   */
  public static <M extends MessageLiteOrBuilder>
  Iterable<M> filter(Iterable<M> objs, Predicate<M> filter) {

    int i = 0;
    for (M obj : objs) {
      if (!filter.apply(obj)) {
        // At least one discarded object, go to slow-path.
        return filterFrom(objs, filter, i);
      }
      ++i;
    }

    // Optimized common case: all items filtered, return the input sequence.
    return objs;
  }

  private static <M extends MessageLiteOrBuilder> List<M> filterFrom(
      Iterable<M> objs, Predicate<M> filter, int firstDiscarded) {
    int initialCapacity = (objs instanceof Collection<?>) ? ((Collection<?>) objs).size() - 1 : 10;
    List<M> filtered = (firstDiscarded == 0) ? null : new ArrayList<M>(initialCapacity);

    Iterator<M> iter = objs.iterator();
    for (int i = 0; i < firstDiscarded; ++i) {
      filtered.add(iter.next());
    }

    iter.next(); // Ignore object at firstDiscarded position

    while (iter.hasNext()) {
      M obj = iter.next();

      if (filter.apply(obj)) {
        filtered = (filtered == null) ? new ArrayList<M>(initialCapacity) : filtered;
        filtered.add(obj);
      }
    }

    return filtered == null ? ImmutableList.<M>of() : filtered;
  }
}
