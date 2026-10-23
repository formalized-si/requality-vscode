/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
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
 * That class keeps location (start, end) of the span in the string representation of the document.
 */
public class SpanInfo {

  protected SpanStartEnd position;
  protected String spanId;

  public SpanInfo(final SpanStartEnd position, final String spanId) {
    this.position = position;
    this.spanId = spanId;
  }

  public String toString() {
    return position.toString() + " spanId:<" + spanId + "> spanPos:<:" + position.start+", "+position.end+ ">";
  }

  public String getSpanId() {
    return spanId;
  }

  public SpanStartEnd getSpanPosition() {
    return position;
  }

  public String getText(String bigText) {
    return bigText.substring(position.start, position.end);
  }

}
