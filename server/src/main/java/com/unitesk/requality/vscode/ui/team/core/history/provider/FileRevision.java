package com.unitesk.requality.vscode.ui.team.core.history.provider;

import java.net.URI;

import com.unitesk.requality.vscode.ui.team.core.history.IFileRevision;
import com.unitesk.requality.vscode.ui.team.core.history.ITag;

/** {@code org.eclipse.team.core.history.provider.FileRevision}: an abstract implementation of {@link IFileRevision}. */
public abstract class FileRevision implements IFileRevision {
  @Override
  public URI getURI() {
    return null;
  }

  @Override
  public long getTimestamp() {
    return -1;
  }

  @Override
  public boolean exists() {
    return true;
  }

  @Override
  public String getContentIdentifier() {
    return null;
  }

  @Override
  public String getAuthor() {
    return null;
  }

  @Override
  public String getComment() {
    return null;
  }

  @Override
  public ITag[] getBranches() {
    return new ITag[0];
  }

  @Override
  public ITag[] getTags() {
    return new ITag[0];
  }
}
