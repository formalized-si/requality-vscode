package com.unitesk.requality.vscode.ui.team;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.ResourceAttributes;
import com.unitesk.requality.vscode.ui.resources.team.FileModificationValidationContext;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.team.core.DefaultFileModificationValidator;
import com.unitesk.requality.vscode.ui.team.preferences.IPreferenceIds;
import com.unitesk.requality.vscode.ui.team.preferences.TeamUIPlugin;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.team.internal.ui.DefaultUIFileModificationValidator}: the default file modification validator of
 * the Team UI: read-only files being modified are made writable when the user agrees ("Read-only File Encountered").
 */
public class DefaultUIFileModificationValidator extends DefaultFileModificationValidator {

  /**
   * {@code DefaultUIFileModificationValidator.FileListDialog}: the question for several read-only files, their list in
   * the details. VSCODE: the DetailsDialog of the Team UI is a dialog with a "Details >>" button that shows the list.
   */
  public static class FileListDialog extends Dialog {
    private final IFile[] files;
    private Composite details;
    private Button detailsButton;

    public static boolean openQuestion(Shell shell, IFile[] files) {
      FileListDialog dialog = new FileListDialog(shell, files);
      int code = dialog.open();
      return code == OK;
    }

    public FileListDialog(Shell parentShell, IFile[] files) {
      super(parentShell);
      this.files = files;
    }

    @Override
    protected void configureShell(Shell shell) {
      super.configureShell(shell);
      shell.setText("Read-only Files Encountered");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
      Composite composite = (Composite) super.createDialogArea(parent);
      Composite main = new Composite(composite, SWT.NONE);
      main.setLayout(new GridLayout(2, false));
      main.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      Label image = new Label(main, SWT.NONE);
      image.setImage(Display.getDefault().getSystemImage(SWT.ICON_WARNING));
      Label message = new Label(main, SWT.WRAP);
      message.setText("Some of the files being modified by the current operation are read-only. Do you wish to make them writable?");
      message.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      details = new Composite(composite, SWT.NONE);
      details.setLayout(new GridLayout(1, false));
      GridData dd = new GridData(GridData.FILL_BOTH);
      dd.exclude = true;
      details.setLayoutData(dd);
      details.setVisible(false);
      Label listLabel = new Label(details, SWT.WRAP);
      listLabel.setText("The following files are read-only:");
      com.unitesk.requality.vscode.ui.swt.widgets.List fileList = new com.unitesk.requality.vscode.ui.swt.widgets.List(details,
          SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
      GridData data = new GridData();
      data.heightHint = 75;
      data.horizontalAlignment = GridData.FILL;
      data.grabExcessHorizontalSpace = true;
      fileList.setLayoutData(data);
      for (IFile file : files)
        fileList.add(file.getFullPath().toString());
      return composite;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
      createButton(parent, IDialogConstants.YES_ID, IDialogConstants.YES_LABEL, true);
      createButton(parent, IDialogConstants.NO_ID, IDialogConstants.NO_LABEL, true);
      detailsButton = createButton(parent, IDialogConstants.DETAILS_ID, IDialogConstants.SHOW_DETAILS_LABEL, false);
    }

    @Override
    protected void buttonPressed(int id) {
      if (IDialogConstants.YES_ID == id) {
        okPressed();
      } else if (IDialogConstants.NO_ID == id) {
        cancelPressed();
      } else if (IDialogConstants.DETAILS_ID == id) {
        boolean show = !details.getVisible();
        ((GridData) details.getLayoutData()).exclude = !show;
        details.setVisible(show);
        detailsButton.setText(show ? IDialogConstants.HIDE_DETAILS_LABEL : IDialogConstants.SHOW_DETAILS_LABEL);
        details.getParent().layout(true);
      } else {
        super.buttonPressed(id);
      }
    }
  }

  @Override
  public IStatus validateEdit(final IFile[] allFiles, FileModificationValidationContext context) {
    final IFile[] readOnlyFiles = getReadOnlyFiles(allFiles);
    if (readOnlyFiles.length > 0 && context != null) {
      final Shell shell = getShell(context);
      final boolean[] ok = new boolean[] { false };
      if (readOnlyFiles.length == 1) {
        syncExec(() -> ok[0] = MessageDialog.openQuestion(shell, "Read-only File Encountered", MessageFormat.format(
            "File ''{0}'' is read-only. Do you wish to make it writable?", readOnlyFiles[0].getFullPath().toString())));
      } else {
        syncExec(() -> ok[0] = FileListDialog.openQuestion(shell, readOnlyFiles));
      }
      if (ok[0])
        setWritable(readOnlyFiles);
    } else if (readOnlyFiles.length > 0 && context == null) {
      if (isMakeWrittableWhenContextNotProvided())
        setWritable(readOnlyFiles);
    }
    return getStatus(readOnlyFiles);
  }

  private Shell getShell(FileModificationValidationContext context) {
    if (context.getShell() instanceof Shell shell)
      return shell;
    IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    return window == null ? null : window.getShell();
  }

  private static void syncExec(Runnable runnable) {
    Display display = Display.getDefault();
    if (display.isUIThread())
      runnable.run();
    else
      display.syncExec(runnable);
  }

  @Override
  public IStatus validateSave(IFile file) {
    if (file.isReadOnly() && isMakeWrittableWhenContextNotProvided()) {
      IFile[] readOnlyFiles = new IFile[] { file };
      setWritable(readOnlyFiles);
      return getStatus(readOnlyFiles);
    }
    return getDefaultStatus(file);
  }

  private boolean isMakeWrittableWhenContextNotProvided() {
    return TeamUIPlugin.getPreferenceStore().getBoolean(IPreferenceIds.MAKE_FILE_WRITTABLE_IF_CONTEXT_MISSING);
  }

  private IFile[] getReadOnlyFiles(IFile[] files) {
    List<IFile> result = new ArrayList<>();
    for (IFile file : files)
      if (file.isReadOnly())
        result.add(file);
    return result.toArray(new IFile[result.size()]);
  }

  protected IStatus setWritable(final IFile[] files) {
    for (IFile file : files) {
      ResourceAttributes attributes = file.getResourceAttributes();
      if (attributes != null)
        attributes.setReadOnly(false);
      try {
        file.setResourceAttributes(attributes);
      } catch (CoreException e) {
        return e.getStatus();
      }
    }
    return com.unitesk.requality.vscode.ui.runtime.Status.OK_STATUS;
  }
}
