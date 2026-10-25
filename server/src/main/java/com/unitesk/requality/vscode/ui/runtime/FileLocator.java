package com.unitesk.requality.vscode.ui.runtime;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * {@code org.eclipse.core.runtime.FileLocator}: plug-in entries are plain files already. As in Eclipse, the path of the
 * file URL is not encoded (a space stays a space): the plug-in takes {@code getPath()} as a file path.
 */
public final class FileLocator {
  private FileLocator() {
  }

  public static URL toFileURL(URL url) throws IOException {
    if (url == null)
      throw new IOException("No URL");
    if (!"file".equals(url.getProtocol()))
      return url;
    try {
      return new URL("file", "", new File(url.toURI()).toURI().getPath());
    } catch (URISyntaxException | IllegalArgumentException e) {
      // the path is not encoded already
      return url;
    }
  }

  public static URL resolve(URL url) throws IOException {
    return toFileURL(url);
  }
}
