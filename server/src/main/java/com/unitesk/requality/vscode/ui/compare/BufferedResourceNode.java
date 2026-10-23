package com.unitesk.requality.vscode.ui.compare;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IStructureComparator;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.compare.internal.BufferedResourceNode}: a resource node whose changed contents are kept in a buffer
 * until {@link #commit} writes them to the file.
 */
public class BufferedResourceNode extends ResourceNode {
  private boolean fDirty = false;
  private IFile fDeleteFile;

  /** Creates a resource node for the given resource. */
  public BufferedResourceNode(IResource resource) {
    super(resource);
  }

  /** Returns true if the buffer contains uncommitted changes. */
  public boolean isDirty() {
    return fDirty;
  }

  @Override
  protected IStructureComparator createChild(IResource child) {
    return new BufferedResourceNode(child);
  }

  @Override
  public void setContent(byte[] contents) {
    fDirty = true;
    super.setContent(contents);
  }

  /** Commits the buffered contents to the resource (or deletes the file of a removed member). */
  public void commit(IProgressMonitor pm) throws CoreException {
    if (fDirty) {
      if (fDeleteFile != null) {
        fDeleteFile.delete(true, pm);
        return;
      }
      IResource resource = getResource();
      if (resource instanceof IFile) {
        byte[] bytes = getContent();
        ((IFile) resource).setContents(new ByteArrayInputStream(bytes), IResource.KEEP_HISTORY, pm);
        fDirty = false;
      }
    }
  }

  @Override
  public ITypedElement replace(ITypedElement child, ITypedElement other) {
    if (child == null) { // add resource
      // create a node without a resource behind it!
      IResource resource = getResource();
      if (resource instanceof IFolder) {
        IFile file = ((IFolder) resource).getFile(other.getName());
        child = new BufferedResourceNode(file);
      }
    }
    if (other == null) { // delete resource
      IResource resource = getResource();
      if (resource instanceof IFolder) {
        IFile file = ((IFolder) resource).getFile(child.getName());
        if (file != null && file.exists()) {
          fDeleteFile = file;
          fDirty = true;
        }
      }
      return null;
    }
    if (other instanceof IStreamContentAccessor && child instanceof IEditableContent) {
      try (InputStream is = ((IStreamContentAccessor) other).getContents()) {
        byte[] bytes = is.readAllBytes();
        if (bytes != null)
          ((IEditableContent) child).setContent(bytes);
      } catch (CoreException | IOException ex) {
        // NeedWork
      }
    }
    return child;
  }
}
