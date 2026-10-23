package com.unitesk.requality.vscode.ui.team.history;

import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.team.ui.history.HistoryPageSource;
import com.unitesk.requality.vscode.ui.team.ui.history.IHistoryPageSource;
import com.unitesk.requality.vscode.ui.workbench.Page;

/** {@code org.eclipse.team.internal.ui.history.LocalHistoryPageSource}: the history page source of the local history of files. */
public class LocalHistoryPageSource extends HistoryPageSource {
  private static LocalHistoryPageSource instance;

  /** The file of the object (a file, or an object that adapts to one), or null. */
  public static IFile getFile(Object object) {
    IResource resource = object instanceof IResource r ? r : Adapters.adapt(object, IResource.class);
    return resource instanceof IFile file ? file : null;
  }

  @Override
  public boolean canShowHistoryFor(Object object) {
    return getFile(object) != null;
  }

  @Override
  public Page createPage(Object object) {
    return new LocalHistoryPage();
  }

  public static synchronized IHistoryPageSource getInstance() {
    if (instance == null) {
      instance = new LocalHistoryPageSource();
    }
    return instance;
  }
}
