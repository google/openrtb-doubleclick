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

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link CSVParser}.
 */
public class CSVParserTest {

  @Test
  public void testOk() throws IOException {
    for (char separator : asList(',', '\t')) {
      for (char quote : asList('"', '\n')) {
        for (char escape : asList('\\', '\r')) {
          for (String empty : asList((String) null))
          test(new CSVParser(separator, quote, escape, empty));
        }
      }
    }
  }

  static void test(CSVParser csvParser) throws IOException {
    checkParse(csvParser, "");
    checkParse(csvParser, " ");
    checkParse(csvParser, "**", "");
    checkParse(csvParser, "x", "x");
    checkParse(csvParser, "*x*y*z*", "x*y*z");
    checkParse(csvParser, "*x y,z*", "x y,z");
    checkParse(csvParser, "x*y*z", "x*y*z");
    checkParse(csvParser, " ** ", "");
    checkParse(csvParser, "//", "/");
    checkParse(csvParser, "/*", "*");
    checkParse(csvParser, ",", null, null);
    checkParse(csvParser, "**,", "", null);
    checkParse(csvParser, ",**", null, "");
    checkParse(csvParser, " ** , ", "", null);
    checkParse(csvParser, " , ** ", null, "");
    checkParse(csvParser, "x,y,z", "x", "y", "z");
    checkParse(csvParser, "x,*y*,z", "x", "y", "z");
  }

  static void checkParse(CSVParser csvParser, String input, String... expected) throws IOException {
    List<String> expectedList = new ArrayList<>(expected.length);
    for (String exp : expected) {
      expectedList.add(fix(csvParser, exp));
    }
    assertEquals(
        csvParser.toString(),
        expectedList,
        csvParser.parseCsv(fix(csvParser, input)));
  }

  static String fix(CSVParser csvParser, String input) {
    return input == null
        ? csvParser.empty
        : input
            .replace(',', csvParser.separator)
            .replace('*', csvParser.quote)
            .replace('/', csvParser.escape);
  }

  @Test(expected = EOFException.class)
  public void testEolInsideQuotes1() throws IOException {
    CSVParser csvParser = new CSVParser(',', '*', '/', null);
    csvParser.parseCsv("*");
  }

  @Test(expected = EOFException.class)
  public void testEolInsideQuotes2() throws IOException {
    CSVParser csvParser = new CSVParser(',', '*', '/', null);
    csvParser.parseCsv("*x*y*z");
  }

  @Test(expected = EOFException.class)
  public void testEolInsideQuotes3() throws IOException {
    CSVParser csvParser = new CSVParser(',', '*', '/', null);
    csvParser.parseCsv("x*y*z*");
  }
}
