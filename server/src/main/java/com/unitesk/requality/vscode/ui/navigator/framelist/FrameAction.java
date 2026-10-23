package com.unitesk.requality.vscode.ui.navigator.framelist;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;

/** {@code org.eclipse.ui.internal.navigator.framelist.FrameAction}: an action on a frame list, updated on its changes. */
public abstract class FrameAction extends Action {
  private final FrameList frameList;
  private final IPropertyChangeListener propertyChangeListener = this::handlePropertyChange;

  protected FrameAction(FrameList frameList) {
    this.frameList = frameList;
    frameList.addPropertyChangeListener(propertyChangeListener);
  }

  public void dispose() {
    frameList.removePropertyChangeListener(propertyChangeListener);
  }

  public FrameList getFrameList() {
    return frameList;
  }

  protected void handlePropertyChange(PropertyChangeEvent event) {
    update();
  }

  public void update() {
  }
}
