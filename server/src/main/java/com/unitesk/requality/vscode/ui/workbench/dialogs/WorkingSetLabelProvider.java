package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;

/**
 * {@code org.eclipse.ui.internal.dialogs.WorkingSetLabelProvider}: the label of a working set is its label, its image is
 * the image of the working set's type (the aggregate's generic icon for the window working set).
 */
public class WorkingSetLabelProvider extends LabelProvider {
  private final Map<String, Image> images = new HashMap<>();

  @Override
  public String getText(Object element) {
    if (element instanceof IWorkingSet workingSet) {
      return workingSet.getLabel();
    }
    return super.getText(element);
  }

  @Override
  public Image getImage(Object element) {
    if (!(element instanceof IWorkingSet workingSet)) {
      return super.getImage(element);
    }
    ImageDescriptor descriptor = workingSet.getImageDescriptor();
    if (descriptor == null) {
      return null;
    }
    return images.computeIfAbsent(descriptor.getPath(), path -> descriptor.createImage());
  }
}
