package com.unitesk.requality.vscode.ui.compare.patch;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.Path;

/**
 * The text of a patch as a storage (the text typed in or pasted in the input page of the Apply Patch wizard, or read from
 * the clipboard), so that the patch parser reads it as it reads a file.
 */
public final class StringStorage implements IStorage, IAdaptable {
  private final String text;
  private final String name;

  public StringStorage(String name, String text) {
    this.name = name;
    this.text = text;
  }

  @Override
  public InputStream getContents() {
    return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public IPath getFullPath() {
    return Path.fromPortableString(name);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public Object getAdapter(Class adapter) {
    return adapter.isInstance(this) ? this : null;
  }
}
