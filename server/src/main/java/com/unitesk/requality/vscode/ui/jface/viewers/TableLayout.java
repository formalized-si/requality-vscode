package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.widgets.Layout;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.TableColumn;

/** {@code org.eclipse.jface.viewers.TableLayout}: column widths from pixel/weight data. */
public class TableLayout extends Layout implements com.unitesk.requality.vscode.ui.swt.widgets.TableAware {
  private final List<ColumnLayoutData> columns = new ArrayList<>();

  public TableLayout() {
  }

  public TableLayout(boolean adjustForScrollBar) {
  }

  public void addColumnData(ColumnLayoutData data) {
    columns.add(data);
  }

  /** Applies the widths; called whenever the table is synchronized. */
  @Override
  public void apply(Table table) {
    TableColumn[] cols = table.getColumns();
    for (int i = 0; i < cols.length && i < columns.size(); i++) {
      ColumnLayoutData d = columns.get(i);
      if (d instanceof ColumnPixelData)
        cols[i].setWidth(((ColumnPixelData) d).width);
      else if (d instanceof ColumnWeightData)
        cols[i].setWeight(((ColumnWeightData) d).weight);
    }
  }

  @Override
  public JsonObject toJson() {
    JsonObject o = new JsonObject();
    o.addProperty("type", "table");
    return o;
  }
}
