package com.unitesk.requality.vscode.ui.jface.dialogs;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.jface.dialogs.MessageDialog}. The instance form is a real dialog (message, custom area and one
 * button per label, the pressed button index is the return code, as in JFace). The static {@code open*} helpers are
 * shown natively by VS Code (modal message). Eclipse's IconAndMessageDialog level is folded into this class.
 */
public class MessageDialog extends Dialog {
  public static final int NONE = 0;
  public static final int ERROR = 1;
  public static final int INFORMATION = 2;
  public static final int QUESTION = 3;
  public static final int WARNING = 4;
  public static final int CONFIRM = 5;
  public static final int QUESTION_WITH_CANCEL = 6;

  private final String title;
  /** {@code IconAndMessageDialog.message}: the message shown (subclasses may set it before the dialog opens). */
  protected String message;
  private final String[] labels;
  private final int defaultIndex;
  private final int imageType;

  public MessageDialog(Shell parentShell, String dialogTitle, Image dialogTitleImage, String dialogMessage,
      int dialogImageType, String[] dialogButtonLabels, int defaultIndex) {
    super(parentShell);
    this.title = dialogTitle;
    this.message = dialogMessage;
    this.labels = dialogButtonLabels;
    this.defaultIndex = defaultIndex;
    this.imageType = dialogImageType;
  }

  /** {@code MessageDialog.getImage()}: the system icon of the dialog kind (none for NONE). */
  public Image getImage() {
    int icon;
    switch (imageType) {
      case ERROR:
        icon = SWT.ICON_ERROR;
        break;
      case INFORMATION:
        icon = SWT.ICON_INFORMATION;
        break;
      case QUESTION:
      case QUESTION_WITH_CANCEL:
      case CONFIRM:
        icon = SWT.ICON_QUESTION;
        break;
      case WARNING:
        icon = SWT.ICON_WARNING;
        break;
      default:
        return null;
    }
    return com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().getSystemImage(icon);
  }

  public MessageDialog(Shell parentShell, String dialogTitle, Image dialogTitleImage, String dialogMessage,
      int dialogImageType, int defaultIndex, String... dialogButtonLabels) {
    this(parentShell, dialogTitle, dialogTitleImage, dialogMessage, dialogImageType, dialogButtonLabels, defaultIndex);
  }

  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);
    if (title != null)
      newShell.setText(title);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite composite = (Composite) super.createDialogArea(parent);
    ((GridLayout) composite.getLayout()).numColumns = 2;
    // IconAndMessageDialog.createMessageArea: the icon of the kind left of the message.
    Image image = getImage();
    if (image != null) {
      Label imageLabel = new Label(composite, SWT.NONE);
      imageLabel.setImage(image);
      imageLabel.setLayoutData(new GridData(SWT.CENTER, SWT.BEGINNING, false, false));
    }
    if (message != null) {
      Label messageLabel = new Label(composite, SWT.WRAP);
      messageLabel.setText(message);
      GridData data = new GridData(GridData.FILL_BOTH);
      data.horizontalSpan = image != null ? 1 : 2;
      data.widthHint = convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH);
      messageLabel.setLayoutData(data);
    }
    createCustomArea(composite);
    return composite;
  }

  /** Hook for subclasses that add an area below the message. Returns null by default. */
  protected Control createCustomArea(Composite parent) {
    return null;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    for (int i = 0; i < labels.length; i++)
      createButton(parent, i, labels[i], defaultIndex == i);
  }

  @Override
  protected void buttonPressed(int buttonId) {
    setReturnCode(buttonId);
    close();
  }

  /** {@code MessageDialog.handleShellCloseEvent}: closing the window by its frame sets the return code SWT.DEFAULT. */
  @Override
  protected void handleShellCloseEvent() {
    super.handleShellCloseEvent();
    setReturnCode(SWT.DEFAULT);
  }

  public static String[] getButtonLabels(int kind) {
    switch (kind) {
      case QUESTION:
        return new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL };
      case CONFIRM:
        return new String[] { IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL };
      case QUESTION_WITH_CANCEL:
        return new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, IDialogConstants.CANCEL_LABEL };
      default:
        return new String[] { IDialogConstants.OK_LABEL };
    }
  }

  public static boolean open(int kind, Shell parent, String title, String message, int style) {
    String[] labels = getButtonLabels(kind);
    int r = NativeDialogs.show(kind, title, message, labels);
    return r == 0;
  }

  public static void openInformation(Shell parent, String title, String message) {
    NativeDialogs.show(INFORMATION, title, message, getButtonLabels(INFORMATION));
  }

  public static void openError(Shell parent, String title, String message) {
    NativeDialogs.show(ERROR, title, message, getButtonLabels(ERROR));
  }

  public static void openWarning(Shell parent, String title, String message) {
    NativeDialogs.show(WARNING, title, message, getButtonLabels(WARNING));
  }

  public static boolean openQuestion(Shell parent, String title, String message) {
    return NativeDialogs.show(QUESTION, title, message, getButtonLabels(QUESTION)) == 0;
  }

  public static boolean openConfirm(Shell parent, String title, String message) {
    return NativeDialogs.show(CONFIRM, title, message, getButtonLabels(CONFIRM)) == 0;
  }
}
