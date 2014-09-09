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

import com.google.common.base.MoreObjects;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Simple CSV parser for internal use only. Remove this again if we find some alternative
 * that is both very small and bug-free / well-maintained.
 * <p>
 * This parser supports: configurable separator and quote characters and empty value;
 * ignored internal double-quotes; ignored non-quoted whitespace; unescaped internal quotes.
 */
class CSVParser {
  static final char NONE = (char) -1;
  final char separator;
  final char quote;
  final char escape;
  final String empty;

  CSVParser(char separator) {
    this(separator, '\"', NONE, "");
  }

  CSVParser(char separator, char quote, char escape, @Nullable String empty) {
    this.separator = separator;
    this.quote = quote;
    this.escape = escape;
    this.empty = empty;
  }

  List<String> parseCsv(String line) throws IOException {
    List<String> cols = new ArrayList<>();
    boolean reading = false;
    int quoteCount = 0;
    boolean escaping = false;
    boolean afterSeparator = false;
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < line.length(); ++i) {
      char c = line.charAt(i);
      if (!reading) {
        if (isWhitespace(c)) {
          continue;
        } else if (c == separator) {
          cols.add(empty);
          afterSeparator = true;
          continue;
        } else {
          reading = true;
          if (c == quote) {
            quoteCount = 1;
            continue;
          } // else fall-through to the "reading && !escaping" section
        }
      } else if (escaping) {
        escaping = false;
        sb.append(c);
        continue;
      }
      // reading && !escaping
      if (c == escape) {
        escaping = true;
      } else if (c == quote) {
        ++quoteCount;
        sb.append(c);
      } else if (quoteCount % 2 == 0 && c == separator) {
        endCol(cols, sb, quoteCount);
        reading = false;
        afterSeparator = c == separator;
        quoteCount = 0;
      } else {
        sb.append(c);
      }
    }

    if (reading || afterSeparator) {
      if (quoteCount % 2 == 0) {
        endCol(cols, sb, quoteCount);
      } else {
        throw new EOFException("End of line inside quotes");
      }
    }

    return cols;
  }

  protected boolean isWhitespace(char c) {
    return c != separator && c != quote && c != escape && Character.isWhitespace(c);
  }

  protected void endCol(List<String> cols, StringBuilder sb, int quoteCount) {
    // Drop trailing whitespace, if any; like: ["xyz"   ,] or [xyz   ,]
    while (sb.length() != 0 && isWhitespace(sb.charAt(sb.length() - 1))) {
      sb.setLength(sb.length() - 1);
    }
    // Drop closing quote if any, like: ["x "y" z",] => [x "y" z"] => [x "y" z]
    // Will fail if the last quote was escaped, like: [x "y" z\",] => [x "y" z"] => [x "y" z]
    // (ideally should be [x "y" z"]; but data that escapes internal quotes will typically
    // delimit the value by external quotes like ["x \"y\" z\"",] which we will parse OK).
    if (quoteCount != 0 && sb.charAt(sb.length() - 1) == quote) {
      sb.setLength(sb.length() - 1);
    }
    cols.add(sb.length() == 0 && quoteCount == 0 ? empty : sb.toString());
    sb.setLength(0);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).omitNullValues()
        .add("separator", separator == NONE ? null : "0x" + Integer.toHexString(separator))
        .add("quote", quote == NONE ? null : "0x" + Integer.toHexString(quote))
        .add("escape", escape == NONE ? null : "0x" + Integer.toHexString(escape))
        .add("empty", empty)
        .toString();
  }
}
