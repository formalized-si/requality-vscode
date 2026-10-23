package com.unitesk.requality.vscode.ui.workbench;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Path;

/**
 * The file of the active text editor of VS Code (the editors of files are VS Code editors): the workbench parts that
 * follow the active editor (GenericHistoryView "Link with Editor") listen to its changes. The extension sends
 * {@code editor/activeTextEditor} when the active text editor changes.
 */
public final class ActiveTextEditor {
  private static final List<Consumer<IFile>> LISTENERS = new CopyOnWriteArrayList<>();
  private static volatile IFile current;

  private ActiveTextEditor() {
  }

  /** The workspace file of the active text editor, or null. */
  public static IFile current() {
    return current;
  }

  /** Called (on the UI thread) when the active text editor shows another file (path null: no text editor). */
  public static void changed(String path) {
    IFile file = path == null ? null
        : ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(Path.fromOSString(new File(path).getAbsolutePath()));
    current = file != null && file.exists() ? file : null;
    if (current != null)
      for (Consumer<IFile> l : LISTENERS)
        l.accept(current);
  }

  public static void addListener(Consumer<IFile> listener) {
    LISTENERS.add(listener);
  }

  public static void removeListener(Consumer<IFile> listener) {
    LISTENERS.remove(listener);
  }
}
