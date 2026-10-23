package com.unitesk.requality.vscode.ui.commands;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;

/** {@code org.eclipse.ui.menus.UIElement}: records what a handler sets on its menu item. */
public class UIElement {
  private String text;
  private String tooltip;
  private ImageDescriptor icon;
  private Boolean checked;

  public void setText(String text) {
    this.text = text;
  }

  public void setTooltip(String text) {
    this.tooltip = text;
  }

  public void setIcon(ImageDescriptor desc) {
    this.icon = desc;
  }

  public void setDisabledIcon(ImageDescriptor desc) {
  }

  public void setHoverIcon(ImageDescriptor desc) {
  }

  public void setChecked(boolean checked) {
    this.checked = checked;
  }

  public void setDropDownId(String id) {
  }

  public String getText() {
    return text;
  }

  public String getTooltip() {
    return tooltip;
  }

  public ImageDescriptor getIcon() {
    return icon;
  }

  public Boolean getChecked() {
    return checked;
  }
}
