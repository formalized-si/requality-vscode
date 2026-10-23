package com.unitesk.requality.vscode.ui.team;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.function.Supplier;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.compare.IStreamContentAccessor;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.team.core.history.IFileRevision;
import com.unitesk.requality.vscode.ui.team.core.history.LocalFileRevision;

/**
 * {@code org.eclipse.team.internal.ui.history.FileRevisionTypedElement}: a typed element of a file revision. Its contents
 * come from the storage of the revision ({@link #FileRevisionTypedElement(IFileRevision, String)}) or from a supplied
 * stream (a new stream is supplied on every {@link #getContents()} call).
 */
public class FileRevisionTypedElement implements IStreamContentAccessor, ITypedElement {
  private final String name;
  private final String type;
  private final Supplier<InputStream> contents;
  private final IPath fullPath;
  private final IFileRevision revision;
  private final String encoding;

  public FileRevisionTypedElement(String name, String type, Supplier<InputStream> contents) {
    this(name, type, contents, null);
  }

  /** A revision of the workspace file {@code fullPath} (workspace-relative, {@code /Project/...}). */
  public FileRevisionTypedElement(String name, String type, Supplier<InputStream> contents, IPath fullPath) {
    this.name = name;
    this.type = type;
    this.contents = contents;
    this.fullPath = fullPath;
    this.revision = null;
    this.encoding = null;
  }

  /** The element of a file revision: its name and type are those of the revision, its contents the revision's storage. */
  public FileRevisionTypedElement(IFileRevision revision, String encoding) {
    this.revision = revision;
    this.encoding = encoding;
    this.name = revision.getName();
    String ext = ITypedElement.TEXT_TYPE;
    if (name != null && name.lastIndexOf('.') >= 0 && name.lastIndexOf('.') < name.length() - 1)
      ext = name.substring(name.lastIndexOf('.') + 1);
    this.type = ext;
    // the contents are read at once and the stream closed: a caller that does not close the stream (the Requality
    // compare viewer) does not keep the file open
    this.contents = () -> {
      try (InputStream is = revision.getStorage(null).getContents()) {
        return new ByteArrayInputStream(is == null ? new byte[0] : is.readAllBytes());
      } catch (java.io.IOException e) {
        RLogger.logError(e);
        return new ByteArrayInputStream(new byte[0]);
      } catch (CoreException e) {
        RLogger.logError(e);
        return new ByteArrayInputStream(new byte[0]);
      }
    };
    this.fullPath = revision instanceof LocalFileRevision local && local.getState() != null
        ? local.getState().getFullPath()
        : revision instanceof LocalFileRevision local2 && local2.getFile() != null ? local2.getFile().getFullPath() : null;
  }

  /** {@code IFileRevision} storage of the revision; Eclipse viewers read its workspace path. */
  public static final class BufferedStorage {
    private final IPath fullPath;

    BufferedStorage(IPath fullPath) {
      this.fullPath = fullPath;
    }

    public IPath getFullPath() {
      return fullPath;
    }
  }

  public BufferedStorage getBufferedStorage() {
    return fullPath == null ? null : new BufferedStorage(fullPath);
  }

  /** The revision of this element, or null when its contents are supplied. */
  public IFileRevision getRevision() {
    return revision;
  }

  /** The encoding of the revision's contents (null for the default). */
  public String getEncoding() {
    return encoding;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Image getImage() {
    return null;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public InputStream getContents() throws CoreException {
    return contents.get();
  }
}
