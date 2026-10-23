package com.unitesk.requality.vscode.ui.jface.resource;

import java.io.File;
import java.net.URL;

import com.unitesk.requality.vscode.core.Icons;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/**
 * {@code org.eclipse.jface.resource.ImageDescriptor}. Plug-in relative paths ("icons/...") resolve to the bundled
 * Requality resources (Eclipse {@code Activator.getImageDescriptor}).
 */
public class ImageDescriptor {
  private final String path;

  protected ImageDescriptor(String path) {
    this.path = path;
  }

  /** A plug-in relative image, e.g. {@code icons/req_error.gif}. */
  public static ImageDescriptor fromResource(String relPath) {
    return new ImageDescriptor(Icons.resource(relPath).getAbsolutePath());
  }

  public static ImageDescriptor createFromFile(Class<?> location, String filename) {
    return fromResource(filename.startsWith("/") ? filename.substring(1) : filename);
  }

  public static ImageDescriptor createFromURL(URL url) {
    return url == null ? getMissingImageDescriptor() : new ImageDescriptor(url.getPath());
  }

  public static ImageDescriptor createFromImage(Image image) {
    return new ImageDescriptor(image == null ? "" : image.getPath());
  }

  public static ImageDescriptor getMissingImageDescriptor() {
    return fromResource(Icons.DEFAULT_ICON);
  }

  public Object createResource(Object device) {
    return createImage();
  }

  public void destroyResource(Object previouslyCreatedObject) {
  }

  public Image createImage(Object device) {
    return createImage();
  }

  public Image createImage() {
    return new Image(new File(path));
  }

  public Image createImage(boolean returnMissingImageOnError) {
    return createImage();
  }

  public String getPath() {
    return path;
  }
}
