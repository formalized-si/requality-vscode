package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;

/** {@code org.eclipse.ui.IPerspectiveRegistry}: the perspectives of plugin.xml ({@code org.eclipse.ui.perspectives}). */
public interface IPerspectiveRegistry {
  /** {@code IPerspectiveRegistry#getPerspectives()}: every perspective of the registry, in declaration order. */
  IPerspectiveDescriptor[] getPerspectives();

  /** {@code IPerspectiveRegistry#findPerspectiveWithId(String)}: null if there is no such perspective. */
  IPerspectiveDescriptor findPerspectiveWithId(String perspectiveId);

  /** {@code IPerspectiveRegistry#getDefaultPerspective()}: the id of the product's default perspective. */
  String getDefaultPerspective();

  /** {@code org.eclipse.ui.IPerspectiveDescriptor}. */
  interface IPerspectiveDescriptor {
    String getId();

    default String getLabel() {
      return getId();
    }

    /** The description of the perspective (the text of its {@code description} element), never null. */
    default String getDescription() {
      return "";
    }

    /** The icon of the perspective; VSCODE: the views of VS Code show no perspective icons, so null. */
    default ImageDescriptor getImageDescriptor() {
      return null;
    }
  }
}
