package com.unitesk.requality.vscode.ui.jface.viewers;

import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.graphics.Font;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.widgets.Item;
import com.unitesk.requality.vscode.ui.swt.widgets.TableItem;
import com.unitesk.requality.vscode.ui.swt.widgets.TreeItem;

/** {@code org.eclipse.jface.viewers.ViewerCell}: a column of a table or tree item. */
public class ViewerCell {
  private final Item item;
  private final int column;
  private final Object element;

  ViewerCell(Item item, int column, Object element) {
    this.item = item;
    this.column = column;
    this.element = element;
  }

  public Object getElement() {
    return element;
  }

  public int getColumnIndex() {
    return column;
  }

  public Item getItem() {
    return item;
  }

  public String getText() {
    return item instanceof TableItem ? ((TableItem) item).getText(column) : ((TreeItem) item).getText(column);
  }

  public void setText(String text) {
    if (item instanceof TableItem)
      ((TableItem) item).setText(column, text == null ? "" : text);
    else
      ((TreeItem) item).setText(column, text == null ? "" : text);
  }

  public void setImage(Image image) {
    if (item instanceof TableItem)
      ((TableItem) item).setImage(column, image);
    else
      ((TreeItem) item).setImage(column, image);
  }

  public void setForeground(Color c) {
    if (item instanceof TableItem)
      ((TableItem) item).setForeground(column, c);
    else
      ((TreeItem) item).setForeground(c);
  }

  public void setBackground(Color c) {
    if (item instanceof TableItem)
      ((TableItem) item).setBackground(column, c);
    else
      ((TreeItem) item).setBackground(c);
  }

  public void setFont(Font f) {
    if (item instanceof TableItem)
      ((TableItem) item).setFont(column, f);
    else
      ((TreeItem) item).setFont(f);
  }
}
