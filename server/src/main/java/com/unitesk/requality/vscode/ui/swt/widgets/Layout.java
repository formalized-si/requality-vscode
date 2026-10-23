package com.unitesk.requality.vscode.ui.swt.widgets;

import com.google.gson.JsonObject;

/** {@code org.eclipse.swt.widgets.Layout}: serialized for the renderer, which lays out with CSS. */
public abstract class Layout {
  public abstract JsonObject toJson();
}
