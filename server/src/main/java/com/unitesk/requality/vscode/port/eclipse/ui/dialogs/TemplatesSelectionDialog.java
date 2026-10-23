package com.unitesk.requality.vscode.port.eclipse.ui.dialogs;

import java.util.HashMap;
import java.util.Map;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.vscode.port.eclipse.views.properties.TemplatesPanel;

public class TemplatesSelectionDialog extends Dialog {
  public static final String NAME = Consts.TEMPLATES_SELECTION_DIALOG_NAME;
  public TemplatesPanel panel;
  private TreeDB db;
  private String seltp = null;
  private static String title = "Templates Editor";

  @Override
  protected com.unitesk.requality.vscode.ui.swt.widgets.Control createContents(final Composite parent) {
    getShell().setText(title);
    parent.setLayoutData(new GridData(GridData.FILL_BOTH));
    Composite c = new Composite(parent, SWT.NONE);
    c.setLayoutData(new GridData(GridData.FILL_BOTH));
    c.setLayout(new GridLayout(1, true));
    panel = new TemplatesPanel(c);
    panel.setTreeDB(db);
    panel.setSelectedType(seltp);
    createButtonBar(c);
    return c;
  }

  private Map<String, String> oldmap;

  @Override
  public Shell getShell() {
    Shell shell = super.getShell();
    shell.setMinimumSize(400, 300);
    return shell;
  };

  @Override
  protected int getShellStyle() {
    return super.getShellStyle() | SWT.RESIZE;
  };

  public TemplatesSelectionDialog(Shell shell, TreeDB db, String type) {
    super(shell);
    this.db = db;
    oldmap = new HashMap<>(db.getActiveTemplates());
    seltp = type;
  }

  private boolean sure = false;

  @Override
  public boolean close() {
    if (!sure
        && !MessageDialog.openConfirm(
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
            "Cancel request",
            "Are you sure want to cancel all changes?")) {
      return false;
    }
    return super.close();
  };

  @Override
  protected void okPressed() {
    sure = true;
    super.okPressed();
  };

  @Override
  protected void cancelPressed() {
    super.cancelPressed();
    Map<String, String> newmap = db.getActiveTemplates();
    for (String s : newmap.keySet())
      if (!oldmap.containsKey(s)) db.setActiveTemplate(s, null);
      else db.setActiveTemplate(s, oldmap.get(s));
    for (String s : oldmap.keySet())
      if (!newmap.containsKey(s)) db.setActiveTemplate(s, oldmap.get(s));
  }
}
