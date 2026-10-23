package com.unitesk.requality.vscode.ui.compare;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;

import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IStructureComparator;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.resources.ResourceAttributes;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Assert;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.compare.ResourceNode}: a workspace resource as a compare element. A file has its (buffered)
 * contents, a container has its members as children for the structure compare.
 */
public class ResourceNode extends BufferedContent implements IEncodedStreamContentAccessor, IStructureComparator,
    ITypedElement, IEditableContent, IModificationDate, IResourceProvider, IEditableContentExtension {
  private final IResource fResource;
  private ArrayList<Object> fChildren;

  public ResourceNode(IResource resource) {
    fResource = resource;
    Assert.isNotNull(resource);
  }

  @Override
  public IResource getResource() {
    return fResource;
  }

  @Override
  public InputStream getContents() throws CoreException {
    if (fResource instanceof IStorage)
      return super.getContents();
    return null;
  }

  @Override
  public long getModificationDate() {
    return fResource.getLocalTimeStamp();
  }

  @Override
  public String getName() {
    return fResource != null ? fResource.getName() : null;
  }

  @Override
  public String getType() {
    if (fResource instanceof IContainer)
      return ITypedElement.FOLDER_TYPE;
    if (fResource != null) {
      String s = fResource.getFileExtension();
      if (s != null)
        return s;
    }
    return ITypedElement.UNKNOWN_TYPE;
  }

  @Override
  public Image getImage() {
    return CompareUI.getImage(fResource);
  }

  /** Two resource nodes are equal when their names are (the children of two folders are matched by name). */
  @Override
  public boolean equals(Object other) {
    if (other instanceof ITypedElement) {
      String otherName = ((ITypedElement) other).getName();
      return getName().equals(otherName);
    }
    return super.equals(other);
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }

  @Override
  public Object[] getChildren() {
    if (fChildren == null) {
      fChildren = new ArrayList<>();
      if (fResource instanceof IContainer) {
        try {
          for (IResource member : ((IContainer) fResource).members()) {
            IStructureComparator child = createChild(member);
            if (child != null)
              fChildren.add(child);
          }
        } catch (CoreException ex) {
          // NeedWork
        }
      }
    }
    return fChildren.toArray();
  }

  protected IStructureComparator createChild(IResource child) {
    return new ResourceNode(child);
  }

  @Override
  protected InputStream createStream() throws CoreException {
    if (fResource instanceof IStorage) {
      InputStream is = ((IStorage) fResource).getContents();
      if (is != null)
        return new BufferedInputStream(is);
    }
    return null;
  }

  @Override
  public boolean isEditable() {
    return true;
  }

  @Override
  public ITypedElement replace(ITypedElement child, ITypedElement other) {
    return child;
  }

  @Override
  public String getCharset() {
    if (fResource instanceof IFile) {
      try {
        return ((IFile) fResource).getCharset();
      } catch (CoreException e) {
        return null;
      }
    }
    return null;
  }

  @Override
  public boolean isReadOnly() {
    if (fResource.getType() == IResource.FILE) {
      ResourceAttributes attrs = fResource.getResourceAttributes();
      if (attrs != null)
        return attrs.isReadOnly();
    }
    return false;
  }

  @Override
  public IStatus validateEdit(Shell shell) {
    if (isReadOnly())
      return ResourcesPlugin.getWorkspace().validateEdit(new IFile[] { (IFile) fResource }, shell);
    return Status.OK_STATUS;
  }
}
