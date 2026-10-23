package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.resource.ColorRegistry;
import com.unitesk.requality.vscode.ui.jface.resource.FontRegistry;

/** {@code org.eclipse.ui.themes.ITheme}. */
public interface ITheme {
  ColorRegistry getColorRegistry();

  FontRegistry getFontRegistry();

  String getId();

  String getLabel();
}
