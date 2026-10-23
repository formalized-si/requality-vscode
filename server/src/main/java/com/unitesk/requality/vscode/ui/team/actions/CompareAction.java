package com.unitesk.requality.vscode.ui.team.actions;

import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;

import com.unitesk.requality.vscode.ui.compare.CompareEditorInput;
import com.unitesk.requality.vscode.ui.compare.CompareUI;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.team.synchronize.SaveablesCompareEditorInput;

/**
 * {@code org.eclipse.team.internal.ui.actions.CompareAction}: Compare With &gt; Each Other of two files (a two-way
 * compare) or of three files (the common ancestor is asked first, then a three-way compare).
 */
public class CompareAction extends TeamAction {

  @Override
  protected void execute(IAction action) throws InvocationTargetException, InterruptedException {
    IResource[] selectedResources = getSelectedResources();
    ITypedElement ancestor = null;
    ITypedElement left = null;
    ITypedElement right = null;
    if (selectedResources.length == 2) {
      if (selectedResources[0] != null)
        left = getElementFor(selectedResources[0]);
      if (selectedResources[1] != null)
        right = getElementFor(selectedResources[1]);
    } else if (selectedResources.length == 3) {
      // prompt for ancestor
      SelectAncestorDialog dialog = new SelectAncestorDialog(getShell(), selectedResources);
      int code = dialog.open();
      if (code != Window.OK)
        return;
      ancestor = getElementFor(dialog.ancestorResource);
      left = getElementFor(dialog.leftResource);
      right = getElementFor(dialog.rightResource);
    } else {
      return;
    }
    openInCompare(ancestor, left, right);
  }

  private void openInCompare(ITypedElement ancestor, ITypedElement left, ITypedElement right) {
    CompareEditorInput input = new SaveablesCompareEditorInput(ancestor, left, right);
    CompareUI.openCompareEditor(input);
  }

  @Override
  public boolean isEnabled() {
    int l = getSelectedResources().length;
    return l == 2 || l == 3;
  }

  private static ITypedElement getElementFor(IResource resource) {
    return SaveablesCompareEditorInput.createFileElement((IFile) resource);
  }

  /** {@code CompareAction.SelectAncestorDialog}: the radio buttons choosing the common ancestor of three files. */
  private static class SelectAncestorDialog extends MessageDialog {
    private final IResource[] theResources;
    IResource ancestorResource;
    IResource leftResource;
    IResource rightResource;
    private Button[] buttons;

    SelectAncestorDialog(Shell parentShell, IResource[] theResources) {
      super(parentShell, "Select Common Ancestor",
          null, "Which resource would you like to use as the common ancestor in the three-way compare?",
          MessageDialog.QUESTION, new String[] { IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL }, 0);
      this.theResources = theResources;
    }

    @Override
    protected Control createCustomArea(Composite parent) {
      Composite composite = new Composite(parent, SWT.NONE);
      composite.setLayout(new GridLayout());
      buttons = new Button[3];
      for (int i = 0; i < 3; i++) {
        buttons[i] = new Button(composite, SWT.RADIO);
        buttons[i].addSelectionListener(selectionListener);
        buttons[i].setText(MessageFormat.format("''{0}''", theResources[i].getFullPath().toPortableString()));
        buttons[i].setFont(parent.getFont());
        // set initial state
        buttons[i].setSelection(i == 0);
      }
      pickAncestor(0);
      return composite;
    }

    private void pickAncestor(int i) {
      ancestorResource = theResources[i];
      leftResource = theResources[i == 0 ? 1 : 0];
      rightResource = theResources[i == 2 ? 1 : 2];
    }

    private final SelectionListener selectionListener = new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        Button selectedButton = (Button) e.widget;
        if (!selectedButton.getSelection())
          return;
        for (int i = 0; i < 3; i++) {
          if (selectedButton == buttons[i])
            pickAncestor(i);
        }
      }
    };
  }
}
