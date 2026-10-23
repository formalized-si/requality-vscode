package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;

import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.preference.PreferenceStore;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.FileDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/** The page of the Preferences export: the file the preferences of the plug-in are written to. */
public class PreferencesExportPage extends WizardPage {
  private Text fileField;

  public PreferencesExportPage(String name) {
    super(name);
    setTitle("Export Preferences");
    setDescription("Export preferences to the local file system.");
    setPageComplete(false);
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    setControl(composite);
    Composite row = new Composite(composite, SWT.NONE);
    row.setLayout(new GridLayout(3, false));
    row.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    Label label = new Label(row, SWT.NONE);
    label.setText("To preference file:");
    fileField = new Text(row, SWT.BORDER);
    fileField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    fileField.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        updatePageCompletion();
      }
    });
    Button browse = new Button(row, SWT.PUSH);
    browse.setText("Browse...");
    browse.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        FileDialog dialog = new FileDialog(getShell(), SWT.SAVE);
        dialog.setFilterExtensions(new String[] { "*.epf", "*.*" });
        String path = dialog.open();
        if (path != null)
          fileField.setText(path);
      }
    });
    updatePageCompletion();
  }

  void updatePageCompletion() {
    setPageComplete(fileField != null && !fileField.getText().trim().isEmpty());
  }

  /** Writes the preference file; returns true if the wizard may close. */
  public boolean finish() {
    File file = new File(fileField.getText().trim());
    if (file.isDirectory()) {
      MessageDialog.openError(getShell(), "Export Preferences", "The preference file must not be a directory.");
      return false;
    }
    try {
      PreferenceTransfer.export(PreferenceStore.getPlugin(), file);
      return true;
    } catch (Exception e) {
      MessageDialog.openError(getShell(), "Export Preferences", "Cannot write " + file + ": " + e.getMessage());
      return false;
    }
  }
}
