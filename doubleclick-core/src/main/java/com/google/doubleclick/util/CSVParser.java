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

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * CSV (comma-separated) and TSV (tab-separated) parser for internal use only.
 * Remove this if we find some alternative that's small, bug-free / well-maintained,
 * and has all required features (including some extensions we need).
 * <p>
 * This parser is "record-oriented", it doesn't try to split a stream into records so this
 * will be done by the caller before invoking the parser. Unfortunately RFC-4180 supports
 * unescaped line breaks inside quoted fields, so a naive caller that just splits the stream
 * into records by looking at line breaks will fail to preserve the "internal line breaks".
 * In principle the caller can have the intelligence to split records correctly, but this
 * would ideally be implemented as part of the parser with a stream-oriented API.
 */
class CSVParser {
  static final char EOT = (char) 0x03;
  static final char NUL = (char) 0x00;
  private static final CSVParser TSV_PARSER = new CSVParser('\t', NUL, NUL, "", false);
  private static final CSVParser CSV_PARSER = new CSVParser(',', '"', NUL, "", false);

  final char separator;
  final char quote;
  final char escape;
  final String empty;
  final boolean trim;

  /**
   * Creates a CSV parser (or TSV, but let's not get picky about naming).
   *
   * @param separator Separator. Normally comma (',' / 0x2C) for CSV, or tab ('\t' / 0x09) for TSV.
   * @param quote Quote. Normally the double-quote ('"', 0x22).
   * @param escape Escape. Non-RFC extension, allows escaping individual characters inside quoted
   * or unquoted fields. Defaults to NUL (no support for escaping), a popular choice would be '\'.
   * @param empty Empty value. Any absent field will be replaced by this value. Only a zero-char
   * field is considered absent; a quoted empty field ("") is not, so you can differentiate
   * between "no value at all" and "empty string value". The normal value for RFC-compliant CSV
   * or TSP parsing is the empty string, which causes no distinction between empty and zero-length.
   * @param trim If {@code true}, trims whitespaces in the start or end of all fields.
   */
  public CSVParser(char separator, char quote, char escape, @Nullable String empty, boolean trim) {
    this.separator = separator;
    this.quote = quote;
    this.escape = escape;
    this.empty = empty;
    this.trim = trim;
  }

  /**
   * Returns a RFC 4180-compliant CSV parser.
   */
  public static CSVParser csvParser() {
    return CSV_PARSER;
  }

  /**
   * Returns an IANA-standard TSV parser.
   */
  public static CSVParser tsvParser() {
    return TSV_PARSER;
  }

  public boolean parse(InputStream is, String regex, Function<List<String>, Boolean> sink)
      throws IOException {
    BufferedReader rd = new BufferedReader(new InputStreamReader(is));
    Pattern pattern = Pattern.compile(regex);
    String record;
    while ((record = rd.readLine()) != null) {
      if (pattern.matcher(record).matches()) {
        try {
          if (!sink.apply(parse(record))) {
            return false;
          }
        } catch (ParseException e) {
          //logger.trace("Bad record: [{}]: {}", record, e.toString());
        }
      }
    }
    return true;
  }

  /**
   * Parses one line / record.
   */
  public List<String> parse(String line) throws ParseException {
    List<String> cols = new ArrayList<>();
    boolean afterQuote = false;
    boolean afterEscape = false;
    boolean afterSeparator = false;
    boolean outerQuote = false;
    boolean afterEndField = false;
    StringBuilder sb = new StringBuilder();

    for (int i = 0; ; ++i) {
      char c = (i == line.length()) ? EOT : line.charAt(i);
      if (afterEndField && c != separator && c != EOT) {
        if (!trim || c != ' ') {
          throw new ParseException("Extraneous character after end of quoted field", i);
        }
      } else if (afterEscape) {
        if (c == EOT) {
          // [abc\^]
          throw new ParseException("Escape not followed by a character", i);
        } else {
          // [abc\x...] => abcx...
          afterEscape = false;
          sb.append(c);
        }
      } else if (c == separator) {
        if (outerQuote && !afterQuote) {
          // ["abc,...] => abc,...
          sb.append(c);
          afterSeparator = false;
        } else {
          // [abc,...] => {abc, ...}
          endCol(cols, sb, i, outerQuote, afterQuote);
          afterQuote = afterEscape = afterEndField = outerQuote = false;
          afterSeparator = true;
        }
      } else if (c == EOT) {
        if (sb.length() != 0 || afterSeparator || outerQuote) {
          // [...,abc^] => {..., abc}
          // [...,^] => {..., ""}
          endCol(cols, sb, i, outerQuote, afterQuote);
        }
        return cols;
      } else if (c == escape) {
        // [...\...]
        afterEscape = true;
        afterSeparator = false;
      } else if (c == quote) {
        if (afterQuote && outerQuote) {
          // Two consecutive quotes inside quoted string, so the pair has to be internal
          // (the second quote cannot be terminating the field).
          sb.append(quote);
          afterQuote = false;
        } else if (sb.length() == 0 && !outerQuote) {
          outerQuote = true;
        } else if (sb.length() != 0 && !outerQuote) {
          // Fields that are not quote-delimited cannot have any internal quotes,
          // unless they are escaped which was already handled.
          throw new ParseException(escape == NUL
              ? "Unescaped quote inside non-quote-delimited field"
              : "Quote inside non-quote-delimited field",
              i);
        } else {
          afterQuote = true;
        }
        afterSeparator = false;
      } else if (c == ' ' && trim
          && ((!outerQuote && sb.length() == 0) // Trimmed space before field
              || afterEndField)) {              // Trimmed space after field
      } else if (c == ' ' && trim && afterQuote) {
        afterEndField = true;
      } else if (outerQuote && afterQuote) {
        // ["abc"x]
        throw new ParseException("Extraneous character after end of quoted field", i);
      } else {
        // Common character.
        sb.append(c);
        afterSeparator = false;
      }
    }
  }

  protected void endCol(
      List<String> cols, StringBuilder sb, int i, boolean outerQuote, boolean afterQuote)
      throws ParseException {
    if (outerQuote && !afterQuote) {
      throw new ParseException("Field starts with quote but ends unquoted", i);
    }
    if (trim && !outerQuote) {
      while (sb.length() != 0 && sb.charAt(sb.length() - 1) == ' ') {
        sb.setLength(sb.length() - 1);
      }
    }
    // Drop trailing whitespace, if any; like: ["xyz"   ,] or [xyz   ,]
    cols.add(!outerQuote && sb.length() == 0 ? empty : sb.toString());
    sb.setLength(0);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).omitNullValues()
        .add("separator", separator == NUL ? null : "0x" + Integer.toHexString(separator))
        .add("quote", quote == NUL ? null : "0x" + Integer.toHexString(quote))
        .add("escape", escape == NUL ? null : "0x" + Integer.toHexString(escape))
        .add("empty", String.valueOf(empty))
        .add("trim", trim)
        .toString();
  }
}
