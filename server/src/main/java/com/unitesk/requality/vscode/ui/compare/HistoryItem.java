package com.unitesk.requality.vscode.ui.compare;

import java.io.BufferedInputStream;
import java.io.InputStream;

import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFileState;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.compare.HistoryItem}: a state of the local history of a file as a compare element. */
public class HistoryItem implements IEncodedStreamContentAccessor, ITypedElement, IModificationDate, IResourceProvider {
  private final ITypedElement fBase;
  private final IFileState fFileState;

  public HistoryItem(ITypedElement base, IFileState fileState) {
    fBase = base;
    fFileState = fileState;
  }

  @Override
  public String getName() {
    return fBase.getName();
  }

  @Override
  public Image getImage() {
    return fBase.getImage();
  }

  @Override
  public String getType() {
    return fBase.getType();
  }

  @Override
  public long getModificationDate() {
    return fFileState.getModificationTime();
  }

  @Override
  public InputStream getContents() throws CoreException {
    return new BufferedInputStream(fFileState.getContents());
  }

  @Override
  public String getCharset() throws CoreException {
    String charset = fFileState.getCharset();
    if (charset == null) {
      IResource resource = getResource();
      if (resource instanceof IFile)
        charset = ((IFile) resource).getCharset();
    }
    return charset;
  }

  @Override
  public IResource getResource() {
    return ResourcesPlugin.getWorkspace().getRoot().findMember(fFileState.getFullPath());
  }

  /** The state of the local history. */
  public IFileState getFileState() {
    return fFileState;
  }
}
