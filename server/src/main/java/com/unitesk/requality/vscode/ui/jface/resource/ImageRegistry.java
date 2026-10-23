package com.unitesk.requality.vscode.ui.jface.resource;

import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.jface.resource.ImageRegistry}. */
public class ImageRegistry {
  private final Map<String, ImageDescriptor> descriptors = new HashMap<>();

  public void put(String key, ImageDescriptor d) {
    descriptors.put(key, d);
  }

  public void put(String key, Image image) {
    descriptors.put(key, ImageDescriptor.createFromImage(image));
  }

  public ImageDescriptor getDescriptor(String key) {
    return descriptors.get(key);
  }

  public Image get(String key) {
    ImageDescriptor d = descriptors.get(key);
    return d == null ? null : d.createImage();
  }

  public void remove(String key) {
    descriptors.remove(key);
  }

  public void dispose() {
    descriptors.clear();
  }
}
