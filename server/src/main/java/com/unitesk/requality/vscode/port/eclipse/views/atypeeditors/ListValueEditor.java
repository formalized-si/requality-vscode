package com.unitesk.requality.vscode.port.eclipse.views.atypeeditors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.CellEditor;
import com.unitesk.requality.vscode.ui.jface.viewers.DialogCellEditor;
import com.unitesk.requality.vscode.ui.jface.viewers.EditingSupport;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProviderListener;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ITableLabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.TableViewerColumn;
import com.unitesk.requality.vscode.ui.jface.viewers.TextCellEditor;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.DisposeEvent;
import com.unitesk.requality.vscode.ui.swt.events.DisposeListener;
import com.unitesk.requality.vscode.ui.swt.events.FocusEvent;
import com.unitesk.requality.vscode.ui.swt.events.FocusListener;
import com.unitesk.requality.vscode.ui.swt.events.KeyEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.TableItem;
import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.port.eclipse.ui.ImageContainer;

public class ListValueEditor extends DialogCellEditor {
  private Map<AttributeType, List<Object>> vals = new HashMap<>();
  private AttributeType lasttype;
  private static final String TITLE = "List Values Editor";
  private ImageContainer ih = new ImageContainer();
  private TreeNode node;
  private String name;
  private boolean init;

  public ListValueEditor(TreeNode node, Composite table, int style, boolean initial) {
    super(table, style);
    this.node = node;
    init = initial;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected Object openDialogBox(Control cellEditorWindow) {
    Object o = ((Table) cellEditorWindow.getParent()).getSelection()[0].getData();
    List<Object> val = null;
    Attribute a;
    ListValue ol;
    if (o instanceof Attribute) {
      a = (Attribute) o;
      name = a.getKey();
      ol = a.getGValue();
    } else {
      if (o instanceof ListValue) ol = (ListValue) o;
      else {
        o = ((ValueItem) o).getValue();
        if (o instanceof ListValue) ol = (ListValue) o;
        else ol = new ListValue(AttributeType.STRING, (List<Object>) o);
      }
    }
    lasttype = ol.getType();
    val = ol.getValues();
    ListDialog ld = new ListDialog(cellEditorWindow.getShell(), val);
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
      if (value2 == null) return;
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

  private class ListDialog extends Dialog {
    boolean sure = false;

    @Override
    public boolean close() {
      if (!sure
          && isDirty()
          && !MessageDialog.openConfirm(
              PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
              "Cancel request",
              "Are you sure want to cancel all changes?")) {
        return false;
      }
      return super.close();
    };

    public TableViewer table;
    private Combo ltype;

    public ListDialog(Shell shell, List<Object> value) {
      super(shell);
      vals.put(lasttype, value);
      setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    public Object getResult() {
      return new ListValue(lasttype, vals.get(lasttype));
    }

    ArrayList<AttributeType> types;

    @Override
    protected Control createDialogArea(Composite parent) {
      Composite container = createClientContainer(parent, 1);
      container.setLayoutData(new GridData(GridData.FILL_BOTH));
      Composite topPanel = new Composite(container, SWT.NONE);
      //Flow fl = new FillLayout();
      GridLayout gl = new GridLayout(1, false);
      gl.marginHeight = gl.marginWidth = 0;

      topPanel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      topPanel.setLayout(gl);

      ltype = new Combo(topPanel, SWT.NONE);
      ltype.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      types = new ArrayList<>();

      for (AttributeType at : AttributeType.visibleListValues()) {
        if (init || at != AttributeType.REFERENCE) {
          ltype.add(at.toString());
          types.add(at);
        }
      }
      int j = 0;
      for (int i = 0; i < AttributeType.visibleListValues().length; i++)
        if (types.get(i).equals(lasttype)) {
          j = i;
          break;
        }
      ltype.select(j);
      ltype.addSelectionListener(
          new SelectionListener() {

            @Override
            public void widgetSelected(SelectionEvent e) {
              int ind = ltype.getSelectionIndex();
              if (ind < 0) return;
              if (lasttype.ordinal() != ind) {
                lasttype = types.get(ind);
                if (vals.get(lasttype) == null) vals.put(lasttype, new ArrayList<>());
                table.setInput(vals.get(lasttype));
                table.refresh();
              }
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
              // TODO Auto-generated method stub

            }
          });
      ltype.addFocusListener(
          new FocusListener() {

            @Override
            public void focusLost(FocusEvent e) {
              int ind = ltype.getSelectionIndex();
              if (ind < 0) return;
              if (lasttype.ordinal() != ind) {
                lasttype = types.get(ind);
                if (vals.get(lasttype) == null) vals.put(lasttype, new ArrayList<>());
                table.setInput(vals.get(lasttype));
                table.refresh();
              }
            }

            @Override
            public void focusGained(FocusEvent e) {
              // TODO Auto-generated method stub

            }
          });
      final Table basetable =
          new Table(container, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.FULL_SELECTION);
      GridData gd = new GridData(GridData.FILL_HORIZONTAL);
      gd.heightHint = 250;
      basetable.setLayoutData(gd);
      table = new TableViewer(basetable);
      TableViewerColumn column =
          new TableViewerColumn(table, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
      column.getColumn().setText("value");
      column.getColumn().setWidth(300);
      column.getColumn().setResizable(true);
      column.getColumn().setMoveable(true);
      column.setEditingSupport(
          new EditingSupport(column.getViewer()) {
            @Override
            protected void setValue(final Object element, Object value) {
              ValueItem it = (ValueItem) element;
              if (value == null || it == null) return;
              if (it.getId() == Double.MAX_VALUE) {
                int cnt = table.getTable().getItemCount();
                if (cnt > 1)
                  it.setId(
                      ((ValueItem) table.getTable().getItems()[cnt - 2].getData()).getId() + 1);
                else it.setId(1);
              }
              if (!(value instanceof ValueItem)) it.setValue(value);
              else it = (ValueItem) value;
              markDirty();
              table.refresh(it);
              if (((ValueItem)
                          table
                              .getTable()
                              .getItems()[table.getTable().getItemCount() - 1]
                              .getData())
                      .getId()
                  != Double.MAX_VALUE) table.add(new ValueItem(null, Double.MAX_VALUE));
            }

            @Override
            protected Object getValue(Object element) {
              //if(lasttype==AttributeType.LIST && ((Object[])((ValueItem)element).getValue()) instanceof Object[])
              //return ((Object[])((ValueItem)element).getValue())[1];
              Object o = ((ValueItem) element).getValue();
              if (o == null) o = AttributeType.getDefaultValue(lasttype);
              return o;
            }

            @Override
            protected CellEditor getCellEditor(Object element) {
              return getEditor(node, lasttype, table.getTable(), SWT.FULL_SELECTION);
            }

            @Override
            protected boolean canEdit(Object element) {
              return true;
            }
          });

      TableViewerColumn cup = new TableViewerColumn(table, SWT.FULL_SELECTION);
      cup.getColumn().setResizable(false);
      cup.getColumn().setWidth(25);
      cup.setEditingSupport(
          new EditingSupport(cup.getViewer()) {
            @Override
            protected void setValue(Object element, Object value) {}

            @Override
            protected Object getValue(Object element) {
              return "";
            }

            @Override
            protected CellEditor getCellEditor(Object element) {
              return new TextCellEditor();
            }

            @Override
            protected boolean canEdit(Object element) {
              if (((ValueItem) element).getId() == Double.MAX_VALUE) return false;
              moveUp(element);
              return false;
            }
          });

      TableViewerColumn cdown = new TableViewerColumn(table, SWT.FULL_SELECTION);
      cdown.getColumn().setResizable(false);
      cdown.getColumn().setWidth(25);
      cdown.setEditingSupport(
          new EditingSupport(cup.getViewer()) {
            @Override
            protected void setValue(Object element, Object value) {}

            @Override
            protected Object getValue(Object element) {
              return "";
            }

            @Override
            protected CellEditor getCellEditor(Object element) {
              return new TextCellEditor();
            }

            @Override
            protected boolean canEdit(Object element) {
              if (((ValueItem) element).getId() == Double.MAX_VALUE) return false;
              moveDown(element);
              return false;
            }
          });

      TableViewerColumn cdel = new TableViewerColumn(table, SWT.FULL_SELECTION);
      cdel.getColumn().setResizable(false);
      cdel.getColumn().setWidth(25);
      cdel.setEditingSupport(
          new EditingSupport(cup.getViewer()) {
            @Override
            protected void setValue(Object element, Object value) {}

            @Override
            protected Object getValue(Object element) {
              return "";
            }

            @Override
            protected CellEditor getCellEditor(Object element) {
              return new TextCellEditor();
            }

            @Override
            protected boolean canEdit(Object element) {
              table.remove(element);
              markDirty();
              return false;
            }
          });

      table.setLabelProvider(
          new ITableLabelProvider() {
            @Override
            public void removeListener(ILabelProviderListener listener) {}

            @Override
            public boolean isLabelProperty(Object element, String property) {
              return true;
            }

            @Override
            public void dispose() {}

            @Override
            public void addListener(ILabelProviderListener listener) {}

            private int getIndex(ValueItem vi) {
              TableItem[] its = table.getTable().getItems();
              int l = 0, r = its.length - 1, ci = (l + r) / 2;
              ValueItem vi2 = ((ValueItem) (its[ci].getData()));
              double c = 0;
              int k = -1, m = -1;
              if (vi2 != null) c = vi2.getId();
              else {
                k = ci - 1;
                m = ci + 1;
                while (k > 0 && its[k].getData() == null) k--;
                while (m < (its.length - 1) && its[m].getData() == null) m++;
                c =
                    (((ValueItem) (its[k].getData())).ind + ((ValueItem) (its[m].getData())).ind)
                        / 2;
              }
              while (l < r) {
                if (vi.getId() > c) l = m != -1 ? m : ci + 1;
                if (vi.getId() < c) r = k != -1 ? k : ci - 1;
                m = k = -1;
                if (vi.getId() == c) return ci;
                ci = (l + r) / 2;
                vi2 = ((ValueItem) (its[ci].getData()));
                if (vi2 != null) c = vi2.getId();
                else {
                  k = ci - 1;
                  m = ci + 1;
                  while (k > 0 && its[k].getData() == null) k--;
                  while (m < (its.length - 1) && its[m].getData() == null) m++;
                  c =
                      (((ValueItem) (its[k].getData())).ind + ((ValueItem) (its[m].getData())).ind)
                          / 2;
                }
              }
              return l;
            }

            @Override
            public String getColumnText(Object element, int columnIndex) {
              if (columnIndex == 0) {
                
                if (((ValueItem) element).getValue() == null) return "";
                return Attribute.valueToString(node.attrs(), lasttype, ((ValueItem) element).getValue());
              }
              return null;
            }

            @Override
            public Image getColumnImage(Object element, int columnIndex) {
              Display d = Display.getCurrent();
              if (columnIndex == 3 && ((ValueItem) element).getId() != Double.MAX_VALUE)
                return PlatformUI.getWorkbench()
                    .getSharedImages()
                    .getImage(ISharedImages.IMG_ELCL_REMOVE);
              if (columnIndex == 1 && ((ValueItem) element).getId() != Double.MAX_VALUE)
                return ih.getImage(d,"icons/uparrow.png");
              if (columnIndex == 2 && ((ValueItem) element).getId() != Double.MAX_VALUE)
                return ih.getImage(d,"icons/downarrow.png");
              return null;
            }
          });
      table.getControl().addDisposeListener(new DisposeListener() {
        
        @Override
        public void widgetDisposed(DisposeEvent e) {
          ih.dispose();
        }
      });
      table.setComparator(
          new ViewerComparator() {
            @Override
            public int compare(Viewer viewer, Object e1, Object e2) {
              if (e1 instanceof ValueItem && e2 instanceof ValueItem)
                return ((Double) ((ValueItem) e1).getId()).compareTo(((ValueItem) e2).getId());
              return super.compare(viewer, e1, e2);
            }
          });
      table.setContentProvider(
          new IStructuredContentProvider() {
            @Override
            public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

            @Override
            public void dispose() {}

            @Override
            public Object[] getElements(Object inputElement) {
              List<?> l = (List<?>) inputElement;
              Object[] vals = new Object[l.size() + 1];
              int i = 0;
              for (Object o : l)
                if (o instanceof ValueItem) vals[i++] = o;
                else vals[i++] = new ValueItem(o, i);
              vals[vals.length - 1] = new ValueItem(null, Double.MAX_VALUE);
              return vals;
            }
          });
      if (vals.get(lasttype) == null) vals.put(lasttype, new ArrayList<>());
      table.setInput(vals.get(lasttype));
      basetable.addKeyListener(
          new KeyListener() {
            @Override
            public void keyReleased(KeyEvent e) {}

            @Override
            public void keyPressed(KeyEvent e) {
              if (e.keyCode == SWT.CR) okPressed();
              if (e.keyCode == SWT.DEL) removeSelected();
              if (e.keyCode == SWT.F2) {
                StructuredSelection ss = (StructuredSelection) table.getSelection();
                if (ss != null && !ss.isEmpty()) {
                  Object o = ss.getFirstElement();
                  table.editElement(o, 0);
                }
              }
              ;

              //if(e.keyCode == SWT.INSERT)
              //insertAfterSelected();
              if (e.keyCode == SWT.ARROW_UP) {
                if ((e.stateMask & SWT.CTRL) != 0) {
                  StructuredSelection ss = (StructuredSelection) table.getSelection();
                  if (ss != null && !ss.isEmpty()) {
                    Object o = ss.getFirstElement();
                    int ind = table.getTable().getSelectionIndex();
                    moveUp(o);
                    table.setSelection(
                        new StructuredSelection(
                            table.getTable().getItem(ind > 0 ? ind - 1 : 0).getData()));
                  }
                }
              }
              if (e.keyCode == SWT.ARROW_DOWN) {
                if ((e.stateMask & SWT.CTRL) != 0) {
                  StructuredSelection ss = (StructuredSelection) table.getSelection();
                  if (ss != null && !ss.isEmpty()) {
                    Object o = ss.getFirstElement();
                    int ind = table.getTable().getSelectionIndex();
                    moveDown(o);
                    table.setSelection(new StructuredSelection());
                    int limit = table.getTable().getItemCount() - 1;
                    table.setSelection(
                        new StructuredSelection(
                            table.getTable().getItem(ind < limit ? ind + 1 : limit).getData()));
                  }
                }
              }
            }
          });
      this.getShell().setText(TITLE);
      this.getShell()
          .setImage(
              PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT));
      return container;
    }

    protected void moveDown(Object element) {
      ValueItem vi = (ValueItem) element;
      vi.setId(getNextId(vi));
      table.refresh(vi);
      SortedMap<Double, Object> v = new TreeMap<>();
      for (TableItem ti : table.getTable().getItems()) {
        ValueItem vi2 = (ValueItem) ti.getData();
        v.put(vi2.getId(), vi2.getValue());
      }
      v.remove(Double.MAX_VALUE);
      markDirty();
      vals.put(lasttype, new ArrayList<>(v.values()));
      table.setInput(vals.get(lasttype));
      table.refresh();
    }

    protected void moveUp(Object element) {
      ValueItem vi = (ValueItem) element;
      vi.setId(getPreviousId(vi));
      table.refresh(vi);
      SortedMap<Double, Object> v = new TreeMap<>();
      for (TableItem ti : table.getTable().getItems()) {
        ValueItem vi2 = (ValueItem) ti.getData();
        v.put(vi2.getId(), vi2.getValue());
      }
      v.remove(Double.MAX_VALUE);
      vals.put(lasttype, new ArrayList<>(v.values()));
      markDirty();
      table.setInput(vals.get(lasttype));
      table.refresh(); // TODO Auto-generated method stub
    }

    protected double getPreviousId(ValueItem vi) {
      TableItem[] tis = table.getTable().getItems();
      if (tis.length > 0) {
        if (tis[0].getData().equals(vi)) return vi.getId();

        if (tis.length > 1 && tis[1].getData().equals(vi))
          return ((ValueItem) tis[0].getData()).getId() - 1;
        for (int i = 2; i < tis.length; i++)
          if (tis[i].getData().equals(vi))
            return (((ValueItem) tis[i - 2].getData()).getId()
                    + ((ValueItem) tis[i - 1].getData()).getId())
                / 2;
      }
      return vi.getId();
    }

    protected double getNextId(ValueItem vi) {
      TableItem[] tis = table.getTable().getItems();
      for (int i = 0; i < tis.length; i++)
        if (tis[i].getData().equals(vi)) {
          if ((i + 2) >= tis.length) return vi.getId();
          if ((i + 3) >= tis.length) return ((ValueItem) tis[i + 1].getData()).getId() + 1;
          return (((ValueItem) tis[i + 1].getData()).getId()
                  + ((ValueItem) tis[i + 2].getData()).getId())
              / 2;
        }
      return vi.getId();
    }

    protected CellEditor getEditor(TreeNode node, AttributeType lasttype, Composite t, int style) {
      if (lasttype == AttributeType.STRING) return new TextCellEditor(t, style);
      if (lasttype == AttributeType.LIST) return new ListValueEditor(node, t, style, false);
      if (lasttype == AttributeType.INT) return new IntValueEditor(t, style);
      if (lasttype == AttributeType.BOOL) return new BoolValueEditor(t, style);
      if (lasttype == AttributeType.FLOAT) return new FloatValueEditor(t, style);
      if (lasttype == AttributeType.REFERENCE) return new ReferenceValueEditor(node, t, style);
      if (lasttype == AttributeType.RESOURCE) return new ResourceValueEditor(t, style);
      //if(lasttype == AttributeType.GENERATED)
      //return new GeneratorValueEditor(node,t,style);//ERROR?
      return null;
    }

    private void removeSelected() {
      Table t = table.getTable();
      int sel = t.getSelectionIndex();
      if (sel > -1 && sel < t.getItemCount()) {
        if (((ValueItem) t.getItem(sel).getData()).getId() == Double.MAX_VALUE) return;
        t.remove(sel);
        t.setFocus();
        if (sel >= t.getItemCount()) {
          t.select(sel - 1);
          t.setFocus();
        } else {
          t.select(sel);
          t.setFocus();
        }
      }
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
      sure = true;
      ArrayList<Object> list = new ArrayList<>();
      TableItem[] vls = table.getTable().getItems();
      int cnt = vls.length;
      for (int i = 0; i < cnt - 1; i++) {
        TableItem s = vls[i];
        Object val = s.getData();
        if (val != null) {
          if (val instanceof ValueItem) list.add(((ValueItem) val).getValue());
          else list.add(val);
        }
      }
      vals.put(lasttype, list); //(lasttype,list);
      sure = true;
      super.okPressed();
    }
  }
}
