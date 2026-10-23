package com.unitesk.requality.vscode.ui.jface.viewers;

import com.unitesk.requality.vscode.ui.swt.events.MouseEvent;

/** {@code org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy} with the JFace default rule. */
public class ColumnViewerEditorActivationStrategy {
  private final ColumnViewer viewer;

  public ColumnViewerEditorActivationStrategy(ColumnViewer viewer) {
    this.viewer = viewer;
  }

  protected boolean isEditorActivationEvent(ColumnViewerEditorActivationEvent event) {
    boolean singleSelect = viewer.getStructuredSelection().size() == 1;
    boolean leftClick = event.eventType == ColumnViewerEditorActivationEvent.MOUSE_CLICK_SELECTION
        && (!(event.sourceEvent instanceof MouseEvent) || ((MouseEvent) event.sourceEvent).button == 1);
    return singleSelect && (leftClick || event.eventType == ColumnViewerEditorActivationEvent.PROGRAMMATIC
        || event.eventType == ColumnViewerEditorActivationEvent.TRAVERSAL);
  }

  boolean accepts(ColumnViewerEditorActivationEvent event) {
    return isEditorActivationEvent(event);
  }

  public void setEnableEditorActivationWithKeyboard(boolean enable) {
  }
}
