package com.unitesk.requality.vscode.ui.navigator.framelist;

/** {@code org.eclipse.ui.internal.navigator.framelist.GoIntoAction}: makes the selected element the input (a new frame). */
public class GoIntoAction extends FrameAction {
  private static final String ID = "org.eclipse.ui.framelist.goInto";

  public GoIntoAction(FrameList frameList) {
    super(frameList);
    setId(ID);
    setText(FrameListMessages.GoInto_text);
    setToolTipText(FrameListMessages.GoInto_toolTip);
    update();
  }

  private Frame getSelectionFrame(int flags) {
    return getFrameList().getSource().getFrame(IFrameSource.SELECTION_FRAME, flags);
  }

  @Override
  public void run() {
    Frame selectionFrame = getSelectionFrame(IFrameSource.FULL_CONTEXT);
    if (selectionFrame != null)
      getFrameList().gotoFrame(selectionFrame);
  }

  @Override
  public void update() {
    super.update();
    Frame selectionFrame = getSelectionFrame(0);
    setEnabled(selectionFrame != null);
  }
}
