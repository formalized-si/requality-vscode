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

package com.unitesk.requality.vscode.port.eclipse.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IDeleteHandler;
import com.unitesk.requality.nodetype.Comment;

public class DeleteCommentHandler extends AbstractHandler implements IDeleteHandler {
  public void recUpdateProcessed(TreeNode node, List<UUID> toprocess) {
    toprocess.add(node.getUUId());
    for (UUID uid : node.getWorkChildrenUUIDs()) {
      TreeNode nde = node.getTreeDB().getNode(uid);
      recUpdateProcessed(nde, toprocess);
    }
  }

  @Override
  public boolean isSuitableFor(TreeNode tn) {
    return (tn.getType().equals(Comment.TYPE_NAME));
  }

  @Override
  public void process(TreeNode[] nodes, List<UUID> processed, byte flag)
      throws ExecutionException {
    if(nodes == null)
      return;
    for (TreeNode node : nodes) {
      if(node == null)
        continue;
      if (!node.getTreeDB().getTreeLogic().canBeDeleted(node)) continue;
      if (!isSuitableFor(node)) continue;
      List<UUID> toprocess = new ArrayList<>();
      recUpdateProcessed(node, toprocess);
      node.delete();
      processed.addAll(toprocess);
    }
  }

  @Override
  public String[] getWarnings(TreeNode[] nodes) {
    return null;
  }

  @Override
  public String titleOverride(TreeNode[] nodes) {
    return null;
  }

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    return null;
  }
}
