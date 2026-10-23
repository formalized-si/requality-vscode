/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.unitesk.requality.tool;

import java.nio.charset.StandardCharsets;
 
public class URIParserUtil {
  //from apache cxf

  private static final String EXCLUDED_CHARS = "<>\"{}|\\^`";
  private static boolean isExcluded(char ch) {
      return ch <= 0x20 || ch >= 0x7F || EXCLUDED_CHARS.indexOf(ch) != -1;
  }
  public static String escapeChars(String s) {
      StringBuilder sb = new StringBuilder(s.length());

      for (int x = 0; x < s.length(); x++) {
          char ch = s.charAt(x);
          if (isExcluded(ch)) {
              byte[] bytes = Character.toString(ch).getBytes(StandardCharsets.UTF_8);
              for (byte b : bytes) {
                  sb.append('%');
                  StringUtils.byteToHex(b, sb);
              }
          } else {
              sb.append(ch);
          }
      }
      return sb.toString();
  }
}
