package com.unitesk.requality.vscode.ui.runtime;

import java.io.IOException;
import java.net.URL;

/** {@code org.eclipse.core.runtime.FileLocator}: plug-in entries are plain files already. */
public final class FileLocator {
  private FileLocator() {
  }

  public static URL toFileURL(URL url) throws IOException {
    if (url == null)
      throw new IOException("No URL");
    return url;
  }

  public static URL resolve(URL url) throws IOException {
    return toFileURL(url);
  }
}
