package com.unitesk.requality.vscode.ui.swt.graphics;

import java.io.File;

/** {@code org.eclipse.swt.graphics.Image}: a reference to an image file shown by the webview. */
public final class Image extends Resource {
  private final String path;

  public Image(Object device, String filename) {
    this.path = new File(filename).getAbsolutePath();
  }

  public Image(File file) {
    this.path = file.getAbsolutePath();
  }

  public String getPath() {
    return path;
  }

  public Rectangle getBounds() {
    return new Rectangle(0, 0, 16, 16);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Image && ((Image) o).path.equals(path);
  }

  @Override
  public int hashCode() {
    return path.hashCode();
  }
}
