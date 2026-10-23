package com.unitesk.requality.vscode.port.eclipse.views.actions;

import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.create.CreateElementOperation;
import com.unitesk.requality.core.operation.create.CreateNode;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF;

public class NewTypedTemplatedMenuPoint extends com.unitesk.requality.vscode.ui.jface.action.Action {
  private String type, templ;
  private CreateElementOperation neh;
  private TreeNode node;

  public NewTypedTemplatedMenuPoint(
      TreeNode sel, String type, String template, CreateElementOperation neh) {
    this(
        sel,
        type,
        template,
        neh,
        (template == null || template.equals(TreeDB.EMPTY_TEMPLATE))
            ? "From Scratch"
            : ("From '" + template + "'"));
  }

  public NewTypedTemplatedMenuPoint(
      TreeNode sel, String type, String template, CreateElementOperation neh, String label) {
    super(label);
    this.type = type;
    this.templ = template;
    this.neh = neh;
    node = sel;
  }

  @Override
  public void runWithEvent(Event event) {
    TreeNode nodeAfter = null;
    if(RequalityCNF.shiftPressed)
    {
      nodeAfter = node;
      node = node.getWorkParent();
    }
    CreateNode.call(false, node, null, type, templ,false, nodeAfter);
    //super.runWithEvent(event);
  }
}
