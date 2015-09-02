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

package com.google.doubleclick.util.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assert.assertEquals;

import com.google.doubleclick.util.impl.CSVParser;

import org.junit.Test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link CSVParser}.
 */
public class CSVParserTest {

  @Test
  public void testRfcCSV() throws ParseException {
    CSVParser csvParser = CSVParser.csvParser();
    checkNotNull(csvParser.toString());
    checkParse(csvParser, "");
    checkParse(csvParser, "**", "");
    checkParse(csvParser, "****", "*");
    checkParse(csvParser, ",,", "", "", "");
    checkParse(csvParser, "x", "x");
    checkParse(csvParser, "x y,z", "x y", "z");
    checkParse(csvParser, "*x y,z*", "x y,z");
    checkParse(csvParser, "//", "/");
    checkParse(csvParser, "/*", "*");
    checkParse(csvParser, ",", null, null);
    checkParse(csvParser, "**,", "", null);
    checkParse(csvParser, ",**", null, "");
    checkParse(csvParser, "x,y,z", "x", "y", "z");
    checkParse(csvParser, "x,*y*,z", "x", "y", "z");
  }

  @Test
  public void testTrim() throws ParseException {
    CSVParser csvParser = new CSVParser(',', '\"', CSVParser.NUL, "", true);
    checkParse(csvParser, " , a , * b * , c ", "", "a", " b ", "c");
  }

  @Test
  public void testEmpty() throws ParseException {
    CSVParser csvParser = new CSVParser(',', '\"', CSVParser.NUL, "!", false);
    checkParse(csvParser, ",**,* *,", "!", "", " ", "!");
  }

  @Test
  public void testTrimEmpty() throws ParseException {
    CSVParser csvParser = new CSVParser(',', '\"', CSVParser.NUL, "!", true);
    checkParse(csvParser, " , ** , * * , ", "!", "", " ", "!");
  }

  @Test
  public void testIanaTSV() throws ParseException {
    CSVParser csvParser = CSVParser.tsvParser();
    checkParse(csvParser, "");
    checkParse(csvParser, ",,", "", "", "");
    checkParse(csvParser, "x", "x");
    checkParse(csvParser, "x y,z", "x y", "z");
    checkParse(csvParser, "//", "/");
    checkParse(csvParser, "/*", "*");
    checkParse(csvParser, ",", null, null);
    checkParse(csvParser, "x,y,z", "x", "y", "z");
  }

  @Test(expected = ParseException.class)
  public void testEolInsideQuotes1() throws ParseException {
    CSVParser csvParser = new CSVParser(',', '*', '/', null, false);
    csvParser.parse("*");
  }

  @Test(expected = ParseException.class)
  public void testEolInsideQuotes2() throws ParseException {
    CSVParser csvParser = new CSVParser(',', '*', '/', null, false);
    csvParser.parse("*x*y*z");
  }

  @Test(expected = ParseException.class)
  public void testEolInsideQuotes3() throws ParseException {
    CSVParser csvParser = new CSVParser(',', '*', '/', null, false);
    csvParser.parse("x*y*z*");
  }

  @Test(expected = ParseException.class)
  public void testEolAfterEscape() throws ParseException {
    CSVParser csvParser = new CSVParser(',', '*', '/', null, false);
    csvParser.parse("/");
  }

  @Test(expected = ParseException.class)
  public void testExtraneousCharAtEnd() throws ParseException {
    CSVParser csvParser = new CSVParser(',', '*', '/', null, false);
    csvParser.parse("*x*k");
  }

  @Test(expected = ParseException.class)
  public void testTrimExtraneousCharAtEnd() throws ParseException {
    CSVParser csvParser = new CSVParser(',', '*', '/', null, true);
    csvParser.parse("*x* k");
  }

  @Test(expected = ParseException.class)
  public void testExtraneousCharAtStart() throws ParseException {
    CSVParser csvParser = new CSVParser(',', '*', '/', null, false);
    csvParser.parse("k*x");
  }

  static void checkParse(CSVParser csvParser, String input, String... expected)
      throws ParseException {
    List<String> expectedList = new ArrayList<>(expected.length);
    for (String exp : expected) {
      expectedList.add(fix(csvParser, exp));
    }
    assertEquals(
        expectedList,
        csvParser.parse(fix(csvParser, input)));
  }

  static String fix(CSVParser csvParser, String input) {
    return input == null
        ? csvParser.empty
        : input
            .replace(',', csvParser.separator)
            .replace('*', csvParser.quote)
            .replace('/', csvParser.escape);
  }
}
