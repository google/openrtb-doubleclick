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

import com.google.common.collect.ImmutableList;

/**
 * Simple CSV parser for internal use only. Remove this again if we find some alternative
 * that is both very small and bug-free / well-maintained.
 */
class CSVParser {
  final char separator;
  final char quote;
  final char escape;

  CSVParser() {
    this(',', '\"', '\\');
  }

  CSVParser(char separator, char quote, char escape) {
    this.separator = separator;
    this.quote = quote;
    this.escape = escape;
  }

  ImmutableList<String> parseCsv(String line) {
    ImmutableList.Builder<String> cols = ImmutableList.builder();
    boolean reading = false;
    boolean quoted = false;
    boolean escaping = false;
    boolean ignoringSeparator = false;
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < line.length(); ++i) {
      char c = line.charAt(i);
      if (!reading) {
        if (Character.isWhitespace(c)) {
          continue;
        } else if (c == separator) {
          if (ignoringSeparator) {
            ignoringSeparator = false;
          } else {
            cols.add("");
          }
          continue;
        } else {
          reading = true;
          ignoringSeparator = false;
          if (c == quote) {
            quoted = true;
            continue;
          } // else fall-through to the "reading && !escaping" section
        }
      } else if (escaping) {
        if (c == '\n' || c == '\r') {
          return ImmutableList.of(); // error
        } else {
          sb.append(c);
          escaping = false;
          continue;
        }
      }
      // reading && !escaping
      if (c == escape) {
        escaping = true;
      } else if (c == (quoted ? quote : separator) || c == '\n' || c == '\r') {
        cols.add(sb.toString());
        sb.setLength(0);
        reading = false;
        if (c == quote && quoted) {
          ignoringSeparator = true;
          quoted = false;
        }
      } else {
        sb.append(c);
      }
    }

    return cols.build();
  }
}
