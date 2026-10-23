package com.unitesk.requality.eclipse.views.atypeeditors;

import org.eclipse.jface.viewers.ICellEditorValidator;
import org.eclipse.swt.widgets.Composite;

import com.unitesk.requality.core.attribute.AttributeType;

public class IntValueEditor extends RequalityTextValueEditor {
  static final ICellEditorValidator validator =
      new ICellEditorValidator() {
        @Override
        public String isValid(Object value) {
          if (value instanceof Integer) return null;
          String s = value.toString();
          try {
            Integer.parseInt(s);
          } catch (NumberFormatException nfe) {
            return "Value '" + s + "' cannot be parsed as integer";
          }
          return null;
        }
      };

  public IntValueEditor(Composite parent, int style) {
    super(parent, style);
    setValidator(validator);
  }

  @Override
  protected AttributeType getAttributeType() {
    return AttributeType.INT;
  }
}
