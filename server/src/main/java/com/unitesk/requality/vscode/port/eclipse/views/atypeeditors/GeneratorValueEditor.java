package com.unitesk.requality.vscode.port.eclipse.views.atypeeditors;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.viewers.DialogCellEditor;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ControlEvent;
import com.unitesk.requality.vscode.ui.swt.events.ControlListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeGenerator;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.valueprovider.GeneratorValueProvider;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.nodeiterator.AttributeValueIteratorType;
import com.unitesk.requality.vscode.port.eclipse.views.panels.AttributeGeneratorPanel;

public class GeneratorValueEditor extends DialogCellEditor {
  private static final String TITLE = "Attribute value generator";
  private TreeNode node;
  private AttributeGenerator generator;
  private boolean ro = false;

  public GeneratorValueEditor(TreeNode node, Composite table, int style) {
    super(table, style);
    this.node = node;
  }
  @Override
  protected void updateContents(Object value) {
    super.updateContents(value==null?null:((AttributeGenerator)value).getGeneratorType());
  }
  @Override
  protected Object openDialogBox(Control cellEditorWindow) {
    Object o = ((Table) cellEditorWindow.getParent()).getSelection()[0].getData();
    Attribute a = null;

    if (o instanceof Attribute) {
      a = (Attribute) o;
      ro = !a.getOrigin().equals(node.getUUId());
      o = null;
      if(a.getDefinition().getValueProvider() instanceof GeneratorValueProvider)
        o = ((GeneratorValueProvider)a.getDefinition().getValueProvider()).getGenerator();
    } else {
      //o = o;
    }
    generator = (AttributeGenerator) o;
    if (generator == null){
      if(a==null)
        throw new RuntimeException("attribute and generator are null");
      Map<String, TypedValue> map = new HashMap<>();
      map.put("generator_type", new TypedValue(AttributeType.STRING,AttributeValueIteratorType.FORMULA_ID));
      generator = new AttributeGenerator(UUID.randomUUID(), map);
    }
    GeneratorDialog ld = new GeneratorDialog(cellEditorWindow.getShell(), ro);
    if (ld.open() == Dialog.OK) {
      return ld.getResult();
    }
    return null;
  }

  protected class ValueItem {
    private Object value;
    private double ind = 0;

    public ValueItem(Object val, double d) {
      value = val;
      ind = d;
    }

    public Object getValue() {
      return value;
    }

    @Override
    public String toString() {
      if (value == null) return "";
      return value.toString();
    }

    public void setValue(Object value2) {
      value = value2;
    }

    public double getId() {
      return ind;
    }

    public void setId(double id) {
      ind = id;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ValueItem)) return false;
      return ((ValueItem) obj).getId() == getId() && ((ValueItem) obj).getValue() == getValue();
    }
  }

  private class GeneratorDialog extends Dialog {
    public AttributeGeneratorPanel panel;
    private boolean readonly;

    public GeneratorDialog(Shell shell, boolean ro) {
      super(shell);
      readonly = ro;

      setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    public Object getResult() {
      return panel.getGenerator();
    }

    @Override
    protected Control createDialogArea(Composite parent) {
      Composite container = createClientContainer(parent, 1);
      GridData gd = new GridData(GridData.FILL_BOTH);
      gd.widthHint = 600;
      container.setLayoutData(gd);
      panel = new AttributeGeneratorPanel(node, container, readonly);
      panel.setIterator(node, generator, readonly);
      panel
          .getControl()
          .addControlListener(
              new ControlListener() {

                @Override
                public void controlResized(ControlEvent e) {
                  getShell().pack();
                }

                @Override
                public void controlMoved(ControlEvent e) {
                  // TODO Auto-generated method stub

                }
              });
      this.getShell().setText(TITLE);
      this.getShell()
          .setImage(
              PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT));
      return container;
    }

    private Composite createClientContainer(Composite parent, int span) {
      Composite container = new Composite(parent, SWT.NONE);
      container.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      GridLayout layout = new GridLayout();
      layout.marginWidth = layout.marginHeight = 10;
      layout.verticalSpacing = 2;
      layout.numColumns = span;
      container.setLayout(layout);
      return container;
    }

    @Override
    protected void okPressed() {
      panel.getControl().forceFocus();
      super.okPressed();
    }
  }
}
