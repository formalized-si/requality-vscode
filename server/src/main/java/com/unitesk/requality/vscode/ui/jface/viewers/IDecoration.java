package com.unitesk.requality.vscode.ui.jface.viewers;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.graphics.Font;

/** {@code org.eclipse.jface.viewers.IDecoration}. */
public interface IDecoration {
  int TOP_LEFT = 0;
  int TOP_RIGHT = 1;
  int BOTTOM_LEFT = 2;
  int BOTTOM_RIGHT = 3;
  int UNDERLAY = 4;
  int REPLACE = 5;
  String ENABLE_REPLACE = "org.eclipse.jface.viewers.IDecoration.disableReplace";

  void addPrefix(String prefix);

  void addSuffix(String suffix);

  void addOverlay(ImageDescriptor overlay);

  void addOverlay(ImageDescriptor overlay, int quadrant);

  void setForegroundColor(Color color);

  void setBackgroundColor(Color color);

  void setFont(Font font);

  IDecorationContext getDecorationContext();
}
