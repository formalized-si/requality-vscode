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

import org.w3c.dom.Node;

/**
 * Class representing a pair of node and corresponding location. Comparison is performed by
 * locations
 */
public class NodeLocation implements Comparable<NodeLocation> {

  protected SpanStartEnd locationPosition;
  protected Node node;

  public NodeLocation(final SpanStartEnd locationPosition, final Node node) {
    this.locationPosition = locationPosition;
    this.node = node;
  }

  public SpanStartEnd getLocation() {
    return locationPosition;
  }

  @Override
  public int compareTo(final NodeLocation other) {
    return locationPosition.compareTo(other.locationPosition);
  }

  @Override
  public String toString() {
    String nodeValueOrDash;
    if (node.getNodeValue() == null) {
      nodeValueOrDash = "-";
    } else {
      nodeValueOrDash = node.getNodeValue();
    }
    return locationPosition.toString()
        + " tag:"
        + node.getNodeName()
        + " NodeType:"
        + node.getNodeType()
        + " LocalName:"
        + node.getLocalName()
        + " value:<"
        + nodeValueOrDash.replaceAll("\\n", "@").replaceAll("\\r", "~")
        + ">";
  }

  public Node getNode() {
    return node;
  }
}
