package com.unitesk.requality.vscode.port.eclipse.views.compare;

import java.util.Map;
import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.port.eclipse.views.panels.ElementViewPanel;

public class TreeNodeViewer extends Viewer {
  Composite core;
  ElementViewPanel evp;
  Object input;
  public TreeNodeViewer(Composite parent, CompareConfiguration config) {
    core = new Composite(parent, SWT.NONE);
    core.setLayout(new GridLayout(1, true));
    Map<String, String> labelsOverride = ElementViewPanel.getDefaultLabels();
//    if (Requirement.TYPE_NAME.equals(rep.getType())) {
//      labelsOverride = new HashMap<>(labelsOverride);
//      labelsOverride.put("descriptionLabel", "Alternative description");
//    }
    evp = new ElementViewPanel(core, 1, true);
    evp.setTreeNode(null);
    evp.setTransactionIgnore(false);
    //evp.addProblemListener(this);
  }

  @Override
  public Control getControl() {
    return core;
  }

  @Override
  public Object getInput() {
    return input;
  }

  @Override
  public ISelection getSelection() {
    return new StructuredSelection();
  }

  @Override
  public void refresh() {
    core.redraw();
  }

  @Override
  public void setInput(Object input) {
    this.input = input;
  }

  @Override
  public void setSelection(ISelection selection, boolean reveal) {
    
  }
}
