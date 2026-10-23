package com.unitesk.requality.vscode.ui.navigator;

import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/** {@code org.eclipse.ui.navigator.CommonViewer}: the tree viewer of a common navigator. */
public class CommonViewer extends TreeViewer {
  private final String viewerId;
  private final INavigatorContentService contentService;
  private CommonNavigator commonNavigator;
  private com.unitesk.requality.vscode.ui.navigator.framelist.FrameList frameList;

  public CommonViewer(String viewerId, Composite parent, int style) {
    super(parent, style);
    this.viewerId = viewerId;
    this.contentService = new INavigatorContentService() {
      @Override
      public String getViewerId() {
        return CommonViewer.this.viewerId;
      }

      @Override
      public void dispose() {
      }
    };
  }

  public INavigatorContentService getNavigatorContentService() {
    return contentService;
  }

  public String getViewerId() {
    return viewerId;
  }

  // VSCODE: protected (package-private in Eclipse) for the viewer of the VS Code tree of the Requality Explorer.
  protected void setCommonNavigator(CommonNavigator navigator) {
    commonNavigator = navigator;
  }

  /** The navigator whose viewer this is. */
  public CommonNavigator getCommonNavigator() {
    return commonNavigator;
  }

  /** The frame list of the viewer (Go Into, Back, Forward, Up), created by the navigator. */
  public void createFrameList() {
    CommonNavigatorFrameSource frameSource = new CommonNavigatorFrameSource(commonNavigator);
    frameList = new com.unitesk.requality.vscode.ui.navigator.framelist.FrameList(frameSource);
    frameSource.connectTo(frameList);
  }

  public com.unitesk.requality.vscode.ui.navigator.framelist.FrameList getFrameList() {
    return frameList;
  }
}
