package com.unitesk.requality.vscode.ui.compare;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.runtime.CoreException;

/**
 * {@code org.eclipse.compare.BufferedContent}: contents read once from a stream and kept in a buffer, which can be
 * changed ({@link #setContent}) and discarded.
 */
public abstract class BufferedContent implements IContentChangeNotifier, IStreamContentAccessor {
  byte[] fContent;
  private List<IContentChangeListener> fListeners;

  protected BufferedContent() {
  }

  /**
   * The contents: the buffer, else the contents of the stream. VSCODE: the stream is read at once and closed (a copy
   * in memory is returned): a caller that does not read or close the stream (TreeNodeCompareViewer for the file of
   * a project without a catalog) does not keep the file open, which on Windows can then not be moved or deleted.
   */
  @Override
  public InputStream getContents() throws CoreException {
    if (fContent != null)
      return new ByteArrayInputStream(fContent);
    try (InputStream is = createStream()) {
      return is == null ? null : new ByteArrayInputStream(is.readAllBytes());
    } catch (IOException e) {
      throw new CoreException(new com.unitesk.requality.vscode.ui.runtime.Status(com.unitesk.requality.vscode.ui.runtime.IStatus.ERROR,
          CompareUI.PLUGIN_ID, 0, e.getMessage(), e));
    }
  }

  protected abstract InputStream createStream() throws CoreException;

  public void setContent(byte[] contents) {
    fContent = contents;
    fireContentChanged();
  }

  public byte[] getContent() {
    if (fContent == null) {
      try (InputStream is = createStream()) {
        if (is == null)
          return null;
        fContent = is.readAllBytes();
      } catch (IOException | CoreException ex) {
        return null;
      }
    }
    return fContent;
  }

  public void discardBuffer() {
    fContent = null;
  }

  @Override
  public synchronized void addContentChangeListener(IContentChangeListener listener) {
    if (fListeners == null)
      fListeners = new ArrayList<>();
    if (!fListeners.contains(listener))
      fListeners.add(listener);
  }

  @Override
  public synchronized void removeContentChangeListener(IContentChangeListener listener) {
    if (fListeners != null) {
      fListeners.remove(listener);
      if (fListeners.isEmpty())
        fListeners = null;
    }
  }

  protected void fireContentChanged() {
    List<IContentChangeListener> ls;
    synchronized (this) {
      ls = fListeners == null ? null : new ArrayList<>(fListeners);
    }
    if (ls != null)
      for (IContentChangeListener l : ls)
        l.contentChanged(this);
  }
}
