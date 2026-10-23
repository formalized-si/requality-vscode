package com.unitesk.requality.vscode.ui.workbench.model;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.core.Icons;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.model.WorkbenchLabelProvider}: labels and images of workspace resources, tree nodes (as the
 * Requality TreeNodeAdapterFactory provides them) and elements adapting to {@link IWorkbenchAdapter}.
 */
public class WorkbenchLabelProvider extends LabelProvider {
  public static WorkbenchLabelProvider getDecoratingWorkbenchLabelProvider() {
    return new WorkbenchLabelProvider();
  }

  /** Adapters.adapt(element, IWorkbenchAdapter.class): the element, its getAdapter or an adapter factory. */
  protected IWorkbenchAdapter getAdapter(Object element) {
    return (IWorkbenchAdapter) com.unitesk.requality.vscode.ui.runtime.Platform.getAdapterManager().getAdapter(element,
        IWorkbenchAdapter.class);
  }

  @Override
  public String getText(Object element) {
    IWorkbenchAdapter a = getAdapter(element);
    if (a != null)
      return a.getLabel(element);
    if (element instanceof IResource)
      return ((IResource) element).getName();
    if (element instanceof TreeNode)
      return ((TreeNode) element).getUserVisibleName();
    return element == null ? "" : element.toString();
  }

  @Override
  public Image getImage(Object element) {
    IWorkbenchAdapter a = getAdapter(element);
    if (a != null) {
      ImageDescriptor d = a.getImageDescriptor(element);
      return d == null ? null : d.createImage();
    }
    ISharedImages si = PlatformUI.getWorkbench().getSharedImages();
    // WorkbenchProject.getImageDescriptor: the open or the closed project image
    if (element instanceof IProject project)
      return si.getImage(project.isOpen() ? ISharedImages.IMG_OBJ_PROJECT : ISharedImages.IMG_OBJ_PROJECT_CLOSED);
    if (element instanceof IFolder)
      return si.getImage(ISharedImages.IMG_OBJ_FOLDER);
    if (element instanceof IFile)
      return si.getImage(ISharedImages.IMG_OBJ_FILE);
    if (element instanceof TreeNode)
      return ImageDescriptor.fromResource(Icons.nodeTypeIcon(((TreeNode) element).getType())).createImage();
    return null;
  }
}
