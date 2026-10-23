package com.unitesk.requality.vscode.ui.ide;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.jface.viewers.IDecoration;
import com.unitesk.requality.vscode.ui.jface.viewers.ILightweightLabelDecorator;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.internal.ide.ContentTypeDecorator}: replaces the icon of a file by the image of the editor of
 * its content type.
 */
public class ContentTypeDecorator implements ILightweightLabelDecorator {
  @Override
  public void decorate(Object element, IDecoration decoration) {
    if (!(element instanceof IFile))
      return;
    IWorkbench workbench = PlatformUI.getWorkbench();
    if (workbench.isClosing())
      return;
    ImageDescriptor image = contentTypeImage((IFile) element);
    if (image != null)
      decoration.addOverlay(image);
  }

  /**
   * The image of the editor of the content type of the file. VSCODE: the workspace emulation has no content types
   * (IFile.getContentDescription) and EditorRegistry has no content-type images, so a file has none and is not
   * decorated (the file icon of the VS Code theme stays).
   */
  private static ImageDescriptor contentTypeImage(IFile file) {
    return null;
  }
}
