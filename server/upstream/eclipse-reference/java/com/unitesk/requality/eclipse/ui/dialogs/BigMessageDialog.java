package com.unitesk.requality.eclipse.ui.dialogs;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class BigMessageDialog extends MessageDialog {

  private String message;
  private String label2;

  public BigMessageDialog(
      Shell parentShell,
      String dialogTitle,
      String dialogLabel1,
      String dialogMessage,
      String dialogLabel2,
      int dialogImageType,
      String[] dialogButtonLabels,
      int defaultIndex) {
    super(
        parentShell,
        dialogTitle,
        null,
        dialogLabel1,
        dialogImageType,
        dialogButtonLabels,
        defaultIndex);
    message = dialogMessage;
    label2 = dialogLabel2;
  }

  protected Control createCustomArea(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    composite.setLayout(layout);
    GridData data = new GridData(GridData.FILL_BOTH);
    data.horizontalSpan = 2;
    composite.setLayoutData(data);

    Text text = new Text(composite, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
    data = new GridData(SWT.FILL, SWT.TOP, true, false);
    data.heightHint = 100;
    text.setLayoutData(data);
    text.setText(message);
    text.setEditable(false);

    Label label = new Label(composite, SWT.NULL);
    label.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
    label.setText(label2);

    return composite;
  }

  public static boolean open(
      Shell parent, String title, String label1, String message, String label2) {
    return open(
        parent,
        title,
        label1,
        message,
        label2,
        new String[] {IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL});
  }

  public static boolean open(
      Shell parent, String title, String label1, String message, String label2, String[] buttons) {
    return openInt(parent, title, label1, message, label2, buttons) == BigMessageDialog.OK;
  }

  public static int openInt(
      Shell parent, String title, String label1, String message, String label2, String[] buttons) {
    BigMessageDialog dialog =
        new BigMessageDialog(parent, title, label1, message, label2, CONFIRM, buttons, 0);
    return dialog.open();
  }

  public static int openErr(
      Shell parent, String title, String label1, String message, String label2, String[] buttons) {
    BigMessageDialog dialog =
        new BigMessageDialog(parent, title, label1, message, label2, ERROR, buttons, 0);
    return dialog.open();
  }
}
