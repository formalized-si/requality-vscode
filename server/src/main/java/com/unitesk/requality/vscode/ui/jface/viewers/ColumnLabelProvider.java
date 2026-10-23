package com.unitesk.requality.vscode.ui.jface.viewers;

import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.graphics.Font;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.jface.viewers.ColumnLabelProvider}. */
public class ColumnLabelProvider extends CellLabelProvider implements ILabelProvider, IColorProvider, IFontProvider {
  @Override
  public void update(ViewerCell cell) {
    Object e = cell.getElement();
    cell.setText(getText(e));
    cell.setImage(getImage(e));
    cell.setForeground(getForeground(e));
    cell.setBackground(getBackground(e));
    cell.setFont(getFont(e));
  }

  @Override
  public String getText(Object element) {
    return element == null ? "" : element.toString();
  }

  @Override
  public Image getImage(Object element) {
    return null;
  }

  @Override
  public Color getForeground(Object element) {
    return null;
  }

  @Override
  public Color getBackground(Object element) {
    return null;
  }

  @Override
  public Font getFont(Object element) {
    return null;
  }
}
