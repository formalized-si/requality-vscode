package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.runtime.CoreException;

/** {@code org.eclipse.ui.part.FileEditorInput}: an editor input that is a workspace file. */
public class FileEditorInput implements IStorageEditorInput {
  private final IFile file;

  /** {@code org.eclipse.ui.part.FileEditorInput(IFile)}. */
  public FileEditorInput(IFile file) {
    this.file = file;
  }

  /** {@code org.eclipse.ui.part.FileEditorInput#getFile()}. */
  public IFile getFile() {
    return file;
  }

  @Override
  public IStorage getStorage() throws CoreException {
    return file;
  }

  @Override
  public boolean exists() {
    return file.exists();
  }

  @Override
  public ImageDescriptor getImageDescriptor() {
    return null;
  }

  @Override
  public String getName() {
    return file.getName();
  }

  @Override
  public IPersistableElement getPersistable() {
    return null;
  }

  @Override
  public String getToolTipText() {
    return file.getFullPath().makeRelative().toString();
  }

  @Override
  public <T> T getAdapter(Class<T> adapter) {
    if (adapter.equals(IFile.class) || adapter.equals(IResource.class))
      return adapter.cast(file);
    return null;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!(obj instanceof FileEditorInput))
      return false;
    return file.equals(((FileEditorInput) obj).file);
  }

  @Override
  public int hashCode() {
    return file.hashCode();
  }
}
