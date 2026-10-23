package com.unitesk.requality.vscode.ui.navigator.framelist;

import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/** {@code org.eclipse.ui.internal.navigator.framelist.ForwardAction}: goes to the next frame of the frame list. */
public class ForwardAction extends FrameAction {
  private static final String ID = "org.eclipse.ui.framelist.forward";

  public ForwardAction(FrameList frameList) {
    super(frameList);
    setId(ID);
    setText(FrameListMessages.Forward_text);
    ISharedImages images = PlatformUI.getWorkbench().getSharedImages();
    setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_FORWARD));
    setDisabledImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_FORWARD_DISABLED));
    update();
  }

  private Frame getNextFrame() {
    FrameList list = getFrameList();
    return list.getFrame(list.getCurrentIndex() +1);
  }

  private String getToolTipText(Frame nextFrame) {
    if (nextFrame != null) {
      String text = nextFrame.getToolTipText();
      if (text != null && text.length() > 0)
        return FrameListMessages.bind(FrameListMessages.Forward_toolTipOneArg, text);
    }
    return FrameListMessages.Forward_toolTip;
  }

  @Override
  public void run() {
    getFrameList().forward();
  }

  @Override
  public void update() {
    super.update();
    Frame nextFrame = getNextFrame();
    setEnabled(nextFrame != null);
    setToolTipText(getToolTipText(nextFrame));
  }
}
