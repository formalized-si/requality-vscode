package com.unitesk.requality.eclipse.views.compare;

import java.util.Map;
import org.eclipse.compare.CompareConfiguration;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import com.unitesk.requality.eclipse.views.panels.ElementViewPanel;

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
