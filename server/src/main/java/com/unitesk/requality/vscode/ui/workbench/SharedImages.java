package com.unitesk.requality.vscode.ui.workbench;

import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** Workbench images: SVG files in {@code resources/icons/shared}. */
final class SharedImages implements ISharedImages {
  static final SharedImages INSTANCE = new SharedImages();
  private static final Map<String, String> FILES = new HashMap<>();
  static {
    FILES.put(IMG_ELCL_REMOVE, "remove.png");
    FILES.put(IMG_OBJ_ELEMENT, "element.png");
    FILES.put(IMG_OBJ_FILE, "file.png");
    FILES.put(IMG_OBJ_FOLDER, "folder.png");
    FILES.put(IMG_OBJ_PROJECT, "project.png");
    FILES.put(IMG_OBJ_PROJECT_CLOSED, "cprj_obj.png");
    FILES.put(IMG_OBJS_ERROR_TSK, "error.png");
    FILES.put(IMG_OBJS_WARN_TSK, "warning.png");
    FILES.put(IMG_OBJS_INFO_TSK, "info.png");
    FILES.put(IMG_TOOL_BACK, "back.png");
    FILES.put(IMG_TOOL_FORWARD, "forward.png");
    FILES.put(IMG_TOOL_UP, "up.png");
    // VSCODE: the disabled variants are the same images (VS Code greys out disabled actions itself).
    FILES.put(IMG_TOOL_BACK_DISABLED, "back.png");
    FILES.put(IMG_TOOL_FORWARD_DISABLED, "forward.png");
    FILES.put(IMG_TOOL_UP_DISABLED, "up.png");
    FILES.put(IMG_TOOL_DELETE, "delete.png");
    FILES.put(IMG_TOOL_NEW_WIZARD, "new.png");
    FILES.put(IMG_ETOOL_CLEAR, "clear.png");
    FILES.put(IMG_ELCL_COLLAPSEALL, "collapseall.png");
    FILES.put(IMG_ELCL_SYNCED, "synced.png");
  }
  private final Map<String, Image> images = new HashMap<>();

  @Override
  public synchronized Image getImage(String symbolicName) {
    ImageDescriptor d = getImageDescriptor(symbolicName);
    if (d == null)
      return null;
    return images.computeIfAbsent(symbolicName, k -> d.createImage());
  }

  @Override
  public ImageDescriptor getImageDescriptor(String symbolicName) {
    String f = FILES.get(symbolicName);
    return f == null ? null : ImageDescriptor.fromResource("icons/shared/" + f);
  }
}
