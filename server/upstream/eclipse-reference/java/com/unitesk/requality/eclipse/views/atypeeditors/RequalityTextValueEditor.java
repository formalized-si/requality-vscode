package com.unitesk.requality.eclipse.views.atypeeditors;

import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.widgets.Composite;

import com.unitesk.requality.core.attribute.AttributeType;

public abstract class RequalityTextValueEditor extends TextCellEditor {

  public RequalityTextValueEditor(Composite parent, int style) {
    super(parent, style);
  }

  protected abstract AttributeType getAttributeType();

  @Override
  protected Object doGetValue() {
    return getAttributeType().fromString(super.doGetValue().toString());
  }

  @Override
  protected void doSetValue(Object value) {
    super.doSetValue(value.toString());
  }
}
