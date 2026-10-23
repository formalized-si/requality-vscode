package com.unitesk.requality.vscode.ui.navigator.framelist;

/** {@code org.eclipse.ui.internal.navigator.framelist.IFrameSource}: a source of frames for a frame list. */
public interface IFrameSource {
  /** The current frame. */
  int CURRENT_FRAME = 0x0001;
  /** The frame of the selection (Go Into). */
  int SELECTION_FRAME = 0x0002;
  /** The parent frame (Up). */
  int PARENT_FRAME = 0x0003;
  /** The frame gets the full context: selection and expanded elements. */
  int FULL_CONTEXT = 0x0001;

  Frame getFrame(int whichFrame, int flags);
}
