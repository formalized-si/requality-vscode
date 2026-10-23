package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;

/** {@code org.eclipse.ui.IWorkingSet}: a named set of elements (resources of the explorer) that can be shown alone. */
public interface IWorkingSet extends IPersistableElement, IAdaptable {
  /** The elements of the working set. */
  IAdaptable[] getElements();

  /** The id of the working set page (the working set type), or null. */
  String getId();

  /** @deprecated use {@link #getImageDescriptor()}. */
  @Deprecated
  ImageDescriptor getImage();

  ImageDescriptor getImageDescriptor();

  String getName();

  void setElements(IAdaptable[] elements);

  void setId(String id);

  void setName(String name);

  boolean isEditable();

  boolean isVisible();

  String getLabel();

  void setLabel(String label);

  boolean isSelfUpdating();

  boolean isAggregateWorkingSet();

  boolean isEmpty();

  IAdaptable[] adaptElements(IAdaptable[] objects);
}
