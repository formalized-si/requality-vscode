package com.unitesk.requality.eclipse.ui.dialogs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.views.panels.ElementViewPanel;
import com.unitesk.requality.eclipse.views.panels.ElementViewPanel.IProblemListener;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;

public class EditElementDialog extends Dialog implements IProblemListener {
  Object filter;
  public static final String NAME = Consts.EDIT_ELEMENT_DIALOG_NAME;
  public ElementViewPanel evp;
  private boolean transactionIgnore = false;
  private String title = "";

  public void setTransactionIgnore(boolean b) {
    if (evp != null) evp.setTransactionIgnore(b);
    transactionIgnore = b;
  }

  @Override
  protected org.eclipse.swt.widgets.Control createContents(Composite parent) {
    getShell().setText(title);
    Composite c = new Composite(parent, SWT.NONE);
    c.setLayout(new GridLayout(1, true));
    if (labelsOverride == null) labelsOverride = ElementViewPanel.getDefaultLabels();
    if (Requirement.TYPE_NAME.equals(rep.getType())) {
      labelsOverride = new HashMap<>(labelsOverride);
      labelsOverride.put("descriptionLabel", "Alternative description");
    }
    evp = new ElementViewPanel(c, 1, labelsOverride, rep);
    evp.setTreeNode(rep);
    evp.setTransactionIgnore(transactionIgnore);
    evp.addProblemListener(this);
    super.createContents(c);
    return c;
  }

  TreeNode rep;
  Shell shell;
  boolean started;
  private Map<String, String> labelsOverride = null;

  public void setTitle(String v) {
    shell.setText(v);
  }

  public EditElementDialog(Shell shell, TreeNode rep, String title, boolean ignoreTrans) {
    this(shell, rep, title, ignoreTrans, null);
  }

  public EditElementDialog(
      Shell shell, TreeNode rep, String title, boolean ignoreTrans, Map<String, String> labels) {
    super(shell);
    //this.shell = getShell();
    this.rep = rep;
    this.title = title;
    started = false;
    labelsOverride = labels;
    transactionIgnore = ignoreTrans;
    try {
      if (!ignoreTrans) if (rep.getTreeDB().startTransaction("Change " + rep)) started = true;
    } catch (Exception e) {
      RLogger.logDebug(CoreUtils.getTrace(e),7);
    }
  }

  boolean sure = false;

  @Override
  protected void okPressed() {
    getButton(IDialogConstants.OK_ID).forceFocus();
    if (started){
      rep.getTreeDB().commit();
      started = false;
    }
    sure = true;
    super.okPressed();
  };

  @Override
  public boolean close() {
    if (!sure
        && evp.isDirty()
        && !MessageDialog.openConfirm(
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
            "Cancel request",
            "Are you sure want to cancel all changes?")) {
      return false;
    }
    if(started){
      rep.getTreeDB().rollback();
      started = false;
    }
    return super.close();
  };

  @Override
  protected void cancelPressed() {
    getButton(IDialogConstants.CANCEL_ID).forceFocus();
    if (started){
      rep.getTreeDB().rollback();
      started = false;
    }
    super.cancelPressed();
  }

  private Set<String> problems = new HashSet<>();

  @Override
  public void problemAppears(String problem) {
    problems.add(problem);
    getButton(OK).setEnabled(problems.size() == 0);
  }

  @Override
  public void problemResolved(String problem) {
    problems.remove(problem);
    getButton(OK).setEnabled(problems.size() == 0);
  }
}
