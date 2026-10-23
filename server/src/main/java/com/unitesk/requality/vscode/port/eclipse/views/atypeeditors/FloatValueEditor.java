package com.unitesk.requality.vscode.port.eclipse.views.atypeeditors;

import com.unitesk.requality.vscode.ui.jface.viewers.ICellEditorValidator;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

import com.unitesk.requality.core.attribute.AttributeType;

public class FloatValueEditor extends RequalityTextValueEditor {
  static final ICellEditorValidator validator =
      new ICellEditorValidator() {
        @Override
        public String isValid(Object value) {
          if (value instanceof Float) return null;
          String s = value.toString();
          if (s.startsWith("-")) s = s.substring(1);
          try {
            Float.parseFloat(s);
          } catch (NumberFormatException nfe) {
            return "Value '" + s + "' cannot be parsed as float";
          }
          return null;
        }
      };

  public FloatValueEditor(Composite parent, int style) {
    super(parent, style);
    setValidator(validator);
  }

  @Override
  protected AttributeType getAttributeType() {
    return AttributeType.FLOAT;
  }
}
