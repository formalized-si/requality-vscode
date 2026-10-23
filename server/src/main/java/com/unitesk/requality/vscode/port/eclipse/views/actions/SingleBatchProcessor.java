package com.unitesk.requality.vscode.port.eclipse.views.actions;

import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.mass.AbstractNodeOperation;

public abstract class SingleBatchProcessor extends com.unitesk.requality.vscode.ui.jface.action.Action {
  private AbstractNodeOperation amo;
  private TreeNode node;

  public SingleBatchProcessor(TreeNode sel, String name) {
    super(name);
    node = sel;
  }

  public abstract AbstractNodeOperation initBatchProcessor(TreeNode sel);

  @Override
  public void runWithEvent(Event event) {
    amo = initBatchProcessor(node); //todo comment
    if (amo != null) amo.execute(node, "");
    //super.runWithEvent(event);
  }
}
