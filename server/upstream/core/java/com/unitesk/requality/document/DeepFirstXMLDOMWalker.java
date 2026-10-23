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
package com.unitesk.requality.document;

import java.util.Iterator;
import java.util.LinkedList;

import org.w3c.dom.Node;

public class DeepFirstXMLDOMWalker implements Iterable<Node> {
  private Node dom;

  public DeepFirstXMLDOMWalker(Node dom) {
    this.dom = dom;
  }

  public class IgroreableIterator implements Iterator<Node> {
    private LinkedList<Node> stack = new LinkedList<>();

    {
      stack.push(dom);
    }

    private Node lastNode;

    @Override
    public boolean hasNext() {
      return !stack.isEmpty();
    }

    public void ignoreChildren(Node n) {
      for (int i = lastNode.getChildNodes().getLength() - 1; i >= 0; i--) {
        stack.push(lastNode.getChildNodes().item(i));
      }
    }

    @Override
    public Node next() {
      lastNode = stack.pop();
      for (int i = lastNode.getChildNodes().getLength() - 1; i >= 0; i--) {
        stack.push(lastNode.getChildNodes().item(i));
      }
      return lastNode;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public Iterator<Node> iterator() {
    return new IgroreableIterator();
  }
}
