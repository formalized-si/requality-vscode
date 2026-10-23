package com.unitesk.requality.vscode.ui.jface.resource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.graphics.RGB;

/** {@code org.eclipse.jface.resource.ColorRegistry}: theme colors map to the VS Code theme. */
public class ColorRegistry {
  private final Map<String, Color> colors = new HashMap<>();

  public Color get(String symbolicName) {
    Color c = colors.get(symbolicName);
    if (c != null)
      return c;
    if (symbolicName != null && symbolicName.contains("background"))
      return Color.theme("var(--vscode-editor-background)", new RGB(255, 255, 255));
    return Color.theme("var(--vscode-foreground)", new RGB(0, 0, 0));
  }

  public void put(String symbolicName, RGB rgb) {
    colors.put(symbolicName, new Color(rgb.red, rgb.green, rgb.blue));
  }

  /** {@code org.eclipse.jface.resource.ColorRegistry.getKeySet()}: the symbolic names of the registered colors. */
  public Set<String> getKeySet() {
    return colors.keySet();
  }

  public RGB getRGB(String symbolicName) {
    return get(symbolicName).getRGB();
  }
}
