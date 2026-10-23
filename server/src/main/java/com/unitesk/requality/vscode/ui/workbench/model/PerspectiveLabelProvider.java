package com.unitesk.requality.vscode.ui.workbench.model;

import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveRegistry.IPerspectiveDescriptor;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.model.PerspectiveLabelProvider}: the label of a perspective, followed by "(default)" for the
 * default perspective of the product. VSCODE: the perspectives have no icons in VS Code, so there are no images.
 */
public final class PerspectiveLabelProvider extends LabelProvider {
  private final boolean markDefault;

  /** The label provider that marks the default perspective. */
  public PerspectiveLabelProvider() {
    this(true);
  }

  /** @param markDefault true if the default perspective is marked with "(default)" */
  public PerspectiveLabelProvider(boolean markDefault) {
    this.markDefault = markDefault;
  }

  @Override
  public String getText(Object element) {
    if (element instanceof IPerspectiveDescriptor) {
      IPerspectiveDescriptor desc = (IPerspectiveDescriptor) element;
      String label = desc.getLabel();
      if (markDefault && desc.getId().equals(PlatformUI.getWorkbench().getPerspectiveRegistry().getDefaultPerspective()))
        label = label + " (default)";
      return label;
    }
    return "Unknown Element Type";
  }
}
