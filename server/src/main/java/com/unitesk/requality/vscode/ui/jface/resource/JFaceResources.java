package com.unitesk.requality.vscode.ui.jface.resource;

import com.unitesk.requality.vscode.ui.swt.graphics.Font;

/** {@code org.eclipse.jface.resource.JFaceResources}. */
public final class JFaceResources {
  public static final String DEFAULT_FONT = "org.eclipse.jface.defaultfont";
  public static final String DIALOG_FONT = "org.eclipse.jface.dialogfont";
  public static final String TEXT_FONT = "org.eclipse.jface.textfont";
  public static final String HEADER_FONT = "org.eclipse.jface.headerfont";
  public static final String BANNER_FONT = "org.eclipse.jface.bannerfont";
  private static final FontRegistry FONTS = new FontRegistry();
  private static final ColorRegistry COLORS = new ColorRegistry();
  private static final ImageRegistry IMAGES = new ImageRegistry();

  private JFaceResources() {
  }

  public static FontRegistry getFontRegistry() {
    return FONTS;
  }

  public static ColorRegistry getColorRegistry() {
    return COLORS;
  }

  public static ImageRegistry getImageRegistry() {
    return IMAGES;
  }

  public static Font getFont(String name) {
    return FONTS.get(name);
  }

  public static Font getDialogFont() {
    return FONTS.get(DIALOG_FONT);
  }

  public static Font getBannerFont() {
    return FONTS.getBold(BANNER_FONT);
  }

  public static Font getHeaderFont() {
    return FONTS.getBold(HEADER_FONT);
  }
}
