package com.unitesk.requality.vscode.ui.jface.preference;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.IMessageProvider;
import com.unitesk.requality.vscode.ui.jface.dialogs.TitleAreaDialog;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.jface.preference.PreferenceDialog} (as used by the Eclipse Properties and Preferences
 * dialogs): a page list on the left, the selected page with its title and message, Restore Defaults/Apply and
 * OK/Cancel.
 */
public class PreferenceDialog extends TitleAreaDialog {
  private final List<IPreferencePage> pages = new ArrayList<>();
  private final String windowTitle;
  private com.unitesk.requality.vscode.ui.swt.widgets.List pageList;
  private Composite pageContainer;
  private IPreferencePage current;
  private Button defaultsButton;
  private Button applyButton;
  private int initial;

  public PreferenceDialog(Shell parent, String windowTitle, List<? extends IPreferencePage> pages) {
    super(parent);
    this.windowTitle = windowTitle;
    this.pages.addAll(pages);
  }

  public void setSelectedPage(int index) {
    initial = index;
  }

  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);
    newShell.setText(windowTitle);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = (Composite) super.createDialogArea(parent);
    Composite body = new Composite(area, SWT.NONE);
    GridLayout gl = new GridLayout(2, false);
    gl.marginWidth = 10;
    gl.marginHeight = 10;
    gl.horizontalSpacing = 10;
    body.setLayout(gl);
    body.setLayoutData(new GridData(GridData.FILL_BOTH));
    pageList = new com.unitesk.requality.vscode.ui.swt.widgets.List(body, SWT.SINGLE | SWT.BORDER);
    GridData ld = new GridData(GridData.FILL_VERTICAL);
    ld.widthHint = 200;
    pageList.setLayoutData(ld);
    pageContainer = new Composite(body, SWT.NONE);
    GridLayout pl = new GridLayout();
    pl.marginWidth = 0;
    pl.marginHeight = 0;
    pageContainer.setLayout(pl);
    pageContainer.setLayoutData(new GridData(GridData.FILL_BOTH));
    for (IPreferencePage p : pages) {
      pageList.add(p.getTitle());
      p.setContainer(this);
    }
    pageList.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        int i = pageList.getSelectionIndex();
        if (i < 0 || pages.get(i) == current)
          return;
        if (current != null && !current.okToLeave()) {
          pageList.setSelection(pages.indexOf(current));
          return;
        }
        showPage(pages.get(i));
      }
    });
    if (!pages.isEmpty()) {
      int i = Math.max(0, Math.min(initial, pages.size() - 1));
      pageList.setSelection(i);
      showPage(pages.get(i));
    }
    return area;
  }

  private void showPage(IPreferencePage page) {
    if (current != null && current.getControl() != null) {
      GridData gd = (GridData) current.getControl().getLayoutData();
      gd.exclude = true;
      current.getControl().setVisible(false);
    }
    current = page;
    if (page.getControl() == null)
      page.createControl(pageContainer);
    GridData gd = new GridData(GridData.FILL_BOTH);
    page.getControl().setLayoutData(gd);
    page.getControl().setVisible(true);
    pageContainer.layout();
    setTitle(page.getTitle());
    updateMessage();
    updateButtons();
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    defaultsButton = createButton(parent, IDialogConstants.CLIENT_ID + 1, "Restore Defaults", false);
    applyButton = createButton(parent, IDialogConstants.CLIENT_ID + 2, "Apply", false);
    createButton(parent, IDialogConstants.OK_ID, "Apply and Close", true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    updateButtons();
  }

  @Override
  protected void buttonPressed(int id) {
    if (id == IDialogConstants.CLIENT_ID + 1) {
      if (current instanceof PreferencePage)
        ((PreferencePage) current).performDefaults();
    } else if (id == IDialogConstants.CLIENT_ID + 2) {
      if (current instanceof PreferencePage)
        ((PreferencePage) current).performApply();
    } else {
      super.buttonPressed(id);
    }
  }

  @Override
  protected void okPressed() {
    for (IPreferencePage p : pages) {
      if (p.getControl() == null)
        continue;
      if (!p.performOk())
        return;
    }
    super.okPressed();
  }

  @Override
  protected void cancelPressed() {
    for (IPreferencePage p : pages)
      if (p.getControl() != null && !p.performCancel())
        return;
    super.cancelPressed();
  }

  /** {@code PreferenceDialog.getApplyButton()}: the Apply button of the dialog. */
  protected Button getApplyButton() {
    return applyButton;
  }

  public void updateButtons() {
    boolean valid = true;
    for (IPreferencePage p : pages)
      if (p.getControl() != null && !p.isValid())
        valid = false;
    Button ok = getButton(IDialogConstants.OK_ID);
    if (ok != null)
      ok.setEnabled(valid);
    boolean da = current instanceof PreferencePage && ((PreferencePage) current).hasDefaultAndApply();
    if (defaultsButton != null) {
      defaultsButton.setVisible(da);
      applyButton.setVisible(da);
      if (current != null)
        applyButton.setEnabled(current.isValid());
    }
  }

  public void updateMessage() {
    if (!(current instanceof PreferencePage))
      return;
    PreferencePage p = (PreferencePage) current;
    setErrorMessage(p.getErrorMessage());
    setMessage(p.getMessage() != null ? p.getMessage() : p.getDescription(),
        p.getMessage() != null ? p.getMessageType() : IMessageProvider.NONE);
  }

  @Override
  public boolean close() {
    for (IPreferencePage p : pages)
      p.dispose();
    return super.close();
  }
}
