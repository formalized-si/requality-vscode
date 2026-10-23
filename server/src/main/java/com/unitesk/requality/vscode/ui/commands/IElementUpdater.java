package com.unitesk.requality.vscode.ui.commands;

import java.util.Map;

/** {@code org.eclipse.ui.commands.IElementUpdater}: the handler adjusts the label of its menu item. */
public interface IElementUpdater {
  @SuppressWarnings("rawtypes")
  void updateElement(UIElement element, Map parameters);
}
