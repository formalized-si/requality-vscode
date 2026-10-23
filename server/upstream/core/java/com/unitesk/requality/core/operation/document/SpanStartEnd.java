/*
 * Copyright 2014-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
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

package com.unitesk.requality.core.operation.document;

/**
 * That class keeps location (start, end) of the any tag in the string representation of the
 * document.
 */
public class SpanStartEnd implements Comparable<SpanStartEnd> {

  protected int start, end;

  public SpanStartEnd(int start, int end) {
    this.start = start;
    this.end = end;
  }

  public String toString() {
    return "start:" + start + " end:" + end;
  }

  public int getStart() {
    return start;
  }

  public int getEnd() {
    return end;
  }

  public boolean isIn(final SpanStartEnd o) {
    return start >= o.start && end <= o.end;
  }

  @Override
  public int compareTo(final SpanStartEnd o) {
    if (this.start == o.start) {
      return (this.end - o.end);
    }
    return (this.start - o.start);
  }
}
