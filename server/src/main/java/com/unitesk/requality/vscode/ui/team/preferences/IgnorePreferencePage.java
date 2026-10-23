package com.unitesk.requality.vscode.ui.team.preferences;

import com.unitesk.requality.vscode.ui.jface.dialogs.InputDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.preference.PreferencePage;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.TableItem;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPreferencePage;

/**
 * {@code org.eclipse.team.internal.ui.preferences.IgnorePreferencePage}: the Team &gt; Ignored Resources page, the name
 * patterns of the resources excluded from version control (checked patterns are enabled), with Add Pattern... and Remove.
 */
public class IgnorePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
  private Table ignoreTable;
  private Button addButton;
  private Button removeButton;

  @Override
  public void init(IWorkbench workbench) {
    setDescription("Use this page to specify a list of resource name patterns to exclude from version control.");
  }

  @Override
  protected Control createContents(Composite ancestor) {
    Composite parent = new Composite(ancestor, SWT.NULL);
    GridLayout layout = new GridLayout();
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    layout.numColumns = 2;
    parent.setLayout(layout);
    GridData data = new GridData();
    data.verticalAlignment = GridData.FILL;
    data.horizontalAlignment = GridData.FILL;
    parent.setLayoutData(data);
    Label l1 = new Label(parent, SWT.NULL);
    l1.setText("&Ignore Patterns:");
    data = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    data.horizontalSpan = 2;
    l1.setLayoutData(data);
    ignoreTable = new Table(parent, SWT.CHECK | SWT.BORDER | SWT.MULTI);
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.heightHint = 300;
    ignoreTable.setLayoutData(gd);
    ignoreTable.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        removeButton.setEnabled(ignoreTable.getSelectionCount() > 0);
      }
    });
    Composite buttons = new Composite(parent, SWT.NULL);
    buttons.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
    layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    buttons.setLayout(layout);
    addButton = new Button(buttons, SWT.PUSH);
    addButton.setText("Add &Pattern...");
    addButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        addIgnore();
      }
    });
    removeButton = new Button(buttons, SWT.PUSH);
    removeButton.setText("&Remove");
    removeButton.setEnabled(false);
    removeButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        ignoreTable.remove(ignoreTable.getSelectionIndices());
        removeButton.setEnabled(false);
      }
    });
    fillTable(TeamIgnores.getAllIgnores());
    return parent;
  }

  @Override
  public boolean performOk() {
    int count = ignoreTable.getItemCount();
    String[] patterns = new String[count];
    boolean[] enabled = new boolean[count];
    TableItem[] items = ignoreTable.getItems();
    for (int i = 0; i < count; i++) {
      patterns[i] = items[i].getText();
      enabled[i] = items[i].getChecked();
    }
    TeamIgnores.setAllIgnores(patterns, enabled);
    return true;
  }

  @Override
  protected void performDefaults() {
    super.performDefaults();
    ignoreTable.removeAll();
    fillTable(TeamIgnores.getDefaultIgnores());
  }

  private void fillTable(TeamIgnores.Info[] ignore) {
    for (TeamIgnores.Info info : ignore) {
      TableItem item = new TableItem(ignoreTable, SWT.NONE);
      item.setText(info.getPattern());
      item.setChecked(info.getEnabled());
    }
  }

  private void addIgnore() {
    InputDialog dialog = new InputDialog(getShell(), "Add Ignore Pattern",
        "Enter a name or path pattern (* = any string, ? = any character):", null, null);
    dialog.open();
    if (dialog.getReturnCode() != Window.OK)
      return;
    String pattern = dialog.getValue();
    if (pattern == null || pattern.isEmpty())
      return;
    for (TableItem item : ignoreTable.getItems())
      if (item.getText().equals(pattern)) {
        MessageDialog.openWarning(getShell(), "Pattern Already Exists", "The entered pattern already exists.");
        return;
      }
    TableItem item = new TableItem(ignoreTable, SWT.NONE);
    item.setText(pattern);
    item.setChecked(true);
  }
}
