package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ColumnWeightData}. */
public class ColumnWeightData extends ColumnLayoutData {
  public int weight;
  public int minimumWidth;

  public ColumnWeightData(int weight) {
    this.weight = weight;
  }

  public ColumnWeightData(int weight, int minimumWidth) {
    this.weight = weight;
    this.minimumWidth = minimumWidth;
  }

  public ColumnWeightData(int weight, boolean resizable) {
    this.weight = weight;
    this.resizable = resizable;
  }

  public ColumnWeightData(int weight, int minimumWidth, boolean resizable) {
    this.weight = weight;
    this.minimumWidth = minimumWidth;
    this.resizable = resizable;
  }
}
