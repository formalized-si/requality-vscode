package com.unitesk.requality.vscode.ui.navigator;

import com.unitesk.requality.vscode.ui.navigator.framelist.TreeFrame;
import com.unitesk.requality.vscode.ui.navigator.framelist.TreeViewerFrameSource;

/**
 * {@code org.eclipse.ui.internal.navigator.CommonNavigatorFrameSource}: frames of a common navigator, named by the
 * navigator; a frame change updates the navigator title.
 */
public class CommonNavigatorFrameSource extends TreeViewerFrameSource {
  private final CommonNavigator navigator;

  public CommonNavigatorFrameSource(CommonNavigator navigator) {
    super(navigator.getCommonViewer());
    this.navigator = navigator;
  }

  @Override
  protected TreeFrame createFrame(Object input) {
    TreeFrame frame = super.createFrame(input);
    frame.setName(navigator.getTitle());
    frame.setToolTipText(navigator.getFrameToolTipText(input));
    return frame;
  }

  @Override
  protected void frameChanged(TreeFrame frame) {
    super.frameChanged(frame);
    navigator.updateTitle();
  }
}
