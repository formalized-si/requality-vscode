package com.unitesk.requality.eclipse.views.atypeeditors;

import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import com.unitesk.requality.core.attribute.AttributeType;

public class BoolValueEditor extends ComboBoxCellEditor {
  static final String[] valueTitles = new String[] {"false", "true"};
  static final Boolean[] values = new Boolean[] {false, true};

  public BoolValueEditor(Composite parent, int style) {
    super(parent, valueTitles, style | SWT.READ_ONLY);
    setActivationStyle(DROP_DOWN_ON_KEY_ACTIVATION|
    		DROP_DOWN_ON_MOUSE_ACTIVATION|
    		DROP_DOWN_ON_PROGRAMMATIC_ACTIVATION|
    		DROP_DOWN_ON_TRAVERSE_ACTIVATION);
  }

  @Override
  protected Object doGetValue() {
    int ind = (Integer) super.doGetValue();
    return ind > -1 ? values[ind] : false;
  }

  @Override
  protected void doSetValue(Object value) {
    if (value instanceof Boolean) super.doSetValue((Boolean) value ? 1 : 0);
    if (value instanceof Integer) super.doSetValue(value);
    super.doSetValue((Boolean) AttributeType.BOOL.fromString(value.toString()) ? 1 : 0);
  }
}
