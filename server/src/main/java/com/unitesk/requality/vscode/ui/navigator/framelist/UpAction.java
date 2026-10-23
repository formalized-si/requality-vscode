package com.unitesk.requality.vscode.ui.navigator.framelist;

import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/** {@code org.eclipse.ui.internal.navigator.framelist.UpAction}: goes to the frame of the parent of the input. */
public class UpAction extends FrameAction {
  private static final String ID = "org.eclipse.ui.framelist.up";

  public UpAction(FrameList frameList) {
    super(frameList);
    setId(ID);
    setText(FrameListMessages.Up_text);
    ISharedImages images = PlatformUI.getWorkbench().getSharedImages();
    setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_UP));
    setDisabledImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_UP_DISABLED));
    update();
  }

  Frame getParentFrame(int flags) {
    return getFrameList().getSource().getFrame(IFrameSource.PARENT_FRAME, flags);
  }

  String getToolTipText(Frame parentFrame) {
    if (parentFrame != null) {
      String text = parentFrame.getToolTipText();
      if (text != null && text.length() > 0)
        return FrameListMessages.bind(FrameListMessages.Up_toolTipOneArg, text);
    }
    return FrameListMessages.Up_toolTip;
  }

  @Override
  public void run() {
    Frame parentFrame = getParentFrame(IFrameSource.FULL_CONTEXT);
    if (parentFrame != null)
      getFrameList().gotoFrame(parentFrame);
  }

  @Override
  public void update() {
    super.update();
    Frame parentFrame = getParentFrame(0);
    setEnabled(parentFrame != null);
    setToolTipText(getToolTipText(parentFrame));
  }
}
