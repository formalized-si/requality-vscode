package com.unitesk.requality.vscode.ui.navigator.framelist;

import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/** {@code org.eclipse.ui.internal.navigator.framelist.BackAction}: goes to the previous frame of the frame list. */
public class BackAction extends FrameAction {
  private static final String ID = "org.eclipse.ui.framelist.back";

  public BackAction(FrameList frameList) {
    super(frameList);
    setId(ID);
    setText(FrameListMessages.Back_text);
    ISharedImages images = PlatformUI.getWorkbench().getSharedImages();
    setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_BACK));
    setDisabledImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_BACK_DISABLED));
    update();
  }

  private Frame getPreviousFrame() {
    FrameList list = getFrameList();
    return list.getFrame(list.getCurrentIndex() -1);
  }

  private String getToolTipText(Frame previousFrame) {
    if (previousFrame != null) {
      String text = previousFrame.getToolTipText();
      if (text != null && text.length() > 0)
        return FrameListMessages.bind(FrameListMessages.Back_toolTipOneArg, text);
    }
    return FrameListMessages.Back_toolTip;
  }

  @Override
  public void run() {
    getFrameList().back();
  }

  @Override
  public void update() {
    super.update();
    Frame previousFrame = getPreviousFrame();
    setEnabled(previousFrame != null);
    setToolTipText(getToolTipText(previousFrame));
  }
}
