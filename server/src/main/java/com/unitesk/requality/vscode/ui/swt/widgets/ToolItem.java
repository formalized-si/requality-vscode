package com.unitesk.requality.vscode.ui.swt.widgets;

import java.io.File;

import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.jface.action.ContributionItem;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/**
 * {@code org.eclipse.swt.widgets.ToolItem}. A server-side proxy of a VS Code view/title button: it is not rendered; its
 * image and checked state are published as the VS Code context keys {@code requality.toolItem.<commandId>.image} (the
 * file name of the image) and {@code requality.toolItem.<commandId>.checked}, where the command id is the id of the
 * {@link ContributionItem} that is its data.
 */
public class ToolItem extends Item {
  private final ToolBar parent;
  private boolean selection;
  private String toolTipText;

  public ToolItem(ToolBar parent, int style) {
    this(parent, style, -1);
  }

  public ToolItem(ToolBar parent, int style, int index) {
    super(parent, style, "toolitem");
    this.parent = parent;
    parent.addItem(this, index);
  }

  public ToolBar getParent() {
    return parent;
  }

  @Override
  public void setImage(Image image) {
    super.setImage(image);
    publish("image", image == null ? null : new File(image.getPath()).getName());
  }

  public void setToolTipText(String text) {
    checkWidget();
    toolTipText = text;
    set("tooltip", text);
  }

  public String getToolTipText() {
    return toolTipText;
  }

  public void setSelection(boolean b) {
    checkWidget();
    selection = b;
    publish("checked", b);
  }

  public boolean getSelection() {
    return selection;
  }

  @Override
  void releaseWidget() {
    parent.removeItem(this);
  }

  /** Sets a VS Code context key of this item's command (only when the server is running). */
  private void publish(String property, Object value) {
    if (!(getData() instanceof ContributionItem) || ServerContext.server() == null)
      return;
    String commandId = ((ContributionItem) getData()).getId();
    if (commandId == null)
      return;
    ServerContext.client().setContext("requality.toolItem." + commandId + "." + property, value);
  }
}
