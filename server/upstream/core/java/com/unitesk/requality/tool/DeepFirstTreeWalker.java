/*
 * Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
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

package com.unitesk.requality.tool;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.VirtualNode;

public class DeepFirstTreeWalker extends TreeWalker {
  boolean ignoreVirtual = false;
  boolean sorted = true;
  boolean declarative = true;
  boolean onlyloaded = false, ignoreCPUP = false;
  boolean nch = false;

  public DeepFirstTreeWalker(
      TreeNode startNode, boolean ignoreVirtual, boolean sorted, boolean declarative) {
    this(startNode, ignoreVirtual, sorted, declarative, false, false);
  }

  public DeepFirstTreeWalker(
      TreeNode startNode,
      boolean ignoreVirtual,
      boolean sorted,
      boolean declarative,
      boolean notcheck,
      boolean onlyloaded) {
    super(startNode);
    this.ignoreVirtual = ignoreVirtual;
    this.sorted = sorted;
    this.nch = notcheck;
    this.declarative = declarative;
    this.onlyloaded = this.ignoreCPUP =  onlyloaded;
  }

  public DeepFirstTreeWalker(TreeNode startNode) {
    this(startNode, true, true, true, false, false);
  }

  @Override
  public Iterator<TreeNode> iterator() {
    return new Iterator<TreeNode>() {
      private LinkedList<TreeNode> stack = new LinkedList<>();
      // private LinkedList<TreeNode> stack = new LinkedList<TreeNode>();
      {
        stack.add(getStart());
      }

      @Override
      public boolean hasNext() {
        return !stack.isEmpty();
      }

      @Override
      public TreeNode next() {
        TreeNode result = stack.removeFirst();
        while ((result == null || result.isDisposed()) && !stack.isEmpty()) {
          result = stack.removeFirst();
        }
        if (result == null || result.getType() == null) return null;
        if (!result.getType().equals(VirtualNode.TYPE_NAME) || !ignoreVirtual) {
          List<TreeNode> childs2 =
              declarative
                  ? sorted
                      ? result.getSortedWorkChildren(null, null, nch, onlyloaded, ignoreCPUP)
                      : result.getWorkChildren(null, nch, onlyloaded,ignoreCPUP)
                  : sorted
                      ? result.getSortedShownChildren(null, null, nch)
                      : result.getShownChildren(null, nch);
          for (int i = childs2.size() - 1; i >= 0; i--) {
            TreeNode node = childs2.get(i);//TODO rework?
            if (node != null && node.getType() != null) {
              stack.addFirst(node);
            }
          }
        }
        return result;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public void setIgnoreUnloaded(boolean b) {
    onlyloaded = b;
  }
}
