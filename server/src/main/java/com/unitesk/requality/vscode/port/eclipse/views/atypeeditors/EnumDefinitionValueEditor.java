package com.unitesk.requality.vscode.port.eclipse.views.atypeeditors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.CellEditor;
import com.unitesk.requality.vscode.ui.jface.viewers.ColumnViewerEditorActivationEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.ColumnViewerEditorActivationListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ColumnViewerEditorDeactivationEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.DialogCellEditor;
import com.unitesk.requality.vscode.ui.jface.viewers.EditingSupport;
import com.unitesk.requality.vscode.ui.jface.viewers.IBaseLabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProviderListener;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ITableColorProvider;
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
import com.unitesk.requality.vscode.ui.swt.events.KeyEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.graphics.RGB;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Listener;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.TableItem;
import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.EnumDefinitionValue;
import com.unitesk.requality.core.attribute.EnumValue;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.port.eclipse.ui.ImageContainer;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.EnumRetypeDeleteDialog;
import com.unitesk.requality.vscode.port.eclipse.views.panels.AttributesPanel;

public class EnumDefinitionValueEditor extends DialogCellEditor {
  private static String MES_ENUM_DEF_DEL =
      "Removing value is used in {cnt} ENUMs. Please choose actions to be performed:\n\t"
          + "'Remove' to remove all ENUMs\n\t'Change type' to change"
          + " ENUMs type to String\n\t'Cancel' to cancel changes";
  private Map<AttributeType, List<ValueItem>> vals = new HashMap<>();
  private AttributeType lasttype;
  private static final String TITLE = "Enum Definition Editor";
  private TreeNode node;
  private String name;
  private Map<String, Map<TreeNode, Set<String>>> usages;
  private Map<String, String> storedName = new HashMap<>();
  private ImageContainer ih = new ImageContainer();
  private Map<String, EnumChangeAction> removedActions =
      new HashMap<>();
  public TextCellEditor commentEditor;

  public static enum EnumChangeAction {
    REMOVE,
    RETYPE
  };

  public static class EnumDefValueResult {
    public EnumDefValueResult(
        AttributeType lasttype,
        List<EnumValue> list,
        Map<String, String> revert,
        Map<String, EnumChangeAction> rema) {
      this.type = lasttype;
      this.values = list;
      this.renames = revert;
      this.remact = rema;
    }

    public AttributeType type;
    public List<EnumValue> values;
    public Map<String, String> renames;
    public Map<String, EnumChangeAction> remact;
  }

  public EnumDefinitionValueEditor(TreeNode node, Composite table, int style) {
    super(table, style);
    this.node = node;
  }

  public boolean isUsed(String s) {
    String name = s;
    if (storedName.containsKey(name)) name = storedName.get(name);
    return usages.containsKey(name);
  }

  public long getUsagesCount(String s) {
    String name = s;
    if (storedName.containsKey(name)) name = storedName.get(name);
    Map<TreeNode, Set<String>> vl = usages.get(name);
    long cnt = 0;
    if (vl != null) for (TreeNode node : vl.keySet()) cnt += vl.get(node).size();
    return cnt;
  }

  public boolean removeUsages(String s) {
    String name = s;
    if (storedName.containsKey(name)) name = storedName.remove(s);
    return usages.remove(name) != null;
  }

  public void setUsages(Map<String, Map<TreeNode, Set<String>>> usg) {
    usages = usg;
  }

  Object ret = null;

  @Override
  protected Object doGetValue() {
    return ret;
  };

  @Override
  protected Object openDialogBox(Control cellEditorWindow) {
    Object o = ((Table) cellEditorWindow.getParent()).getSelection()[0].getData();
    List<EnumValue> val = null;
    Attribute a;
    EnumDefinitionValue ol;
    if (o instanceof Attribute) {
      a = (Attribute) o;
      name = a.getKey();
      ol = (EnumDefinitionValue) a.getValue();
    } else {
      if (o instanceof EnumDefinitionValue) ol = (EnumDefinitionValue) o;
      else {
        o = ((ValueItem) o).getValue();
        ol = (EnumDefinitionValue) o;
      }
    }
    ret = ol;
    lasttype = ol.getType();
    val = ol.getValues();
    if (node != null && name != null) setUsages(AttributesPanel.getEnumDefUsages(node, name).map);
    DefEditDialog ld = new DefEditDialog(cellEditorWindow.getShell(), val);
    if (ld.open() == Dialog.OK) {
      Object res = ld.getResult();
      ret = res;
      return ret;
    }
    return null;
  }

  protected class ValueItem implements Comparable<ValueItem> {
    private EnumValue value;
    private double ind = 0;
    private boolean isPinned;

    public ValueItem(EnumValue val, boolean pin, double d) {
      value = val;
      this.isPinned = pin;
      ind = d;
    }

    public double getId() {
      return ind;
    }

    public void setId(double id) {
      ind = id;
    }

    public EnumValue getValue() {
      return value;
    }

    public String getComment() {
      if (isPinned) return "";
      if (value.getComment() == null) return "<no comments>";
      return value.getComment();
    }

    public String getRealComment() {
      return value.getComment();
    }

    @Override
    public String toString() {
      if (value == null) return "";
      return Attribute.valueToString(node.attrs(), lasttype, value);
    }

    public void setValue(EnumValue value2) {
      if (value2 == null) return;
      value = value2;
    }

    public void setComment(String c) {
      if (c.equals("")) c = null;
      value.setComment(c);
    }

    public boolean getPinState() {
      return isPinned;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ValueItem)) return false;
      if (isPinned ^ ((ValueItem) obj).isPinned) return false;
      return ((ValueItem) obj).getValue().equals(getValue()) && ((ValueItem) obj).ind == ind;
    }

    public void setPinState(boolean b) {
      isPinned = b;
    }

    @Override
    public int compareTo(ValueItem o) {
      return isPinned ? 1 : o.isPinned ? -1 : ((Double) ind).compareTo(o.getId());
    }

    public void setValue(Object string) {
      value.setValue(string);
    }
  }

  private class DefEditDialog extends Dialog {
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

    private Map<AttributeType, CellEditor> cellEditors = null;

    private Map<AttributeType, CellEditor> getCellEditors() {
      if (cellEditors == null) {
        cellEditors = new HashMap<>();
        for (AttributeType at : AttributeType.values()) {
          CellEditor ce = getEditor(node, at, table.getTable(), SWT.FULL_SELECTION);
          cellEditors.put(at, ce);
        }
      }
      return cellEditors;
    }

    private class EnumDefLabelProvider implements ITableLabelProvider, ITableColorProvider {
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

      @Override
      public String getColumnText(Object element, int columnIndex) {
        if (columnIndex == 0) {
//          if (lasttype == AttributeType.REFERENCE && !((ValueItem) element).getPinState()) {
//            Object res = LinkResolver.getErrorsOrNodes(
//                node,
//                (((ValueItem) element).getValue().getValue()).toString(),
//                name + "[" + getIndex((ValueItem) element) + "]")[0];
//            if(res instanceof TreeNode)
//              return ((TreeNode)res).getUserVisibleName();
//            return res.toString();
//          }
          return Attribute.valueToString(node.attrs(), lasttype, ((ValueItem) element).getValue().getValue());
        }
        if (columnIndex == 1) return ((ValueItem) element).getComment();
        return null;
      }

      @Override
      public Image getColumnImage(Object element, int columnIndex) {
        Display d = Display.getCurrent();
        //TODO hide
        if (columnIndex == 4 && !((ValueItem) element).getPinState())
          return PlatformUI.getWorkbench()
              .getSharedImages()
              .getImage(ISharedImages.IMG_ELCL_REMOVE);
        if (columnIndex == 2 && ((ValueItem) element).getId() != Double.MAX_VALUE)
          return ih.getImage(d,"icons/uparrow.png");
        if (columnIndex == 3 && ((ValueItem) element).getId() != Double.MAX_VALUE)
          return ih.getImage(d,"icons/downarrow.png");
        return null;
      }

      @Override
      public Color getForeground(Object element, int columnIndex) {
        // TODO Auto-generated method stub
        return null;
      }

      @Override
      public Color getBackground(Object element, int columnIndex) {
        ValueItem vi = (ValueItem) element;
        if (!vi.getPinState() && isUsed(vi.getValue().getValue().toString()))
          return new Color(table.getTable().getDisplay(), new RGB(200, 255, 200));
        return null;
      }
    }

    public IBaseLabelProvider labelProvider = new EnumDefLabelProvider();
    public TableViewer table;
    //private Combo ltype;
    public DefEditDialog(Shell shell, List<EnumValue> arrayList) {
      super(shell);
      vals.put(lasttype, new ArrayList<ValueItem>());
      int i = 0;
      for (Object ev : arrayList) vals.get(lasttype).add(new ValueItem((EnumValue) ev, false, i++));
      setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    private boolean dialogEffectOk(ValueItem vi) {
      if (!vi.getPinState()) {
        EnumRetypeDeleteDialog erdd =
            new EnumRetypeDeleteDialog(
                table.getTable().getShell(),
                MES_ENUM_DEF_DEL.replace(
                    "{cnt}", Long.toString(getUsagesCount(vi.getValue().getValue().toString()))));
        String name = vi.getValue().getValue().toString();
        if (storedName.containsKey(name)) name = storedName.get(name);
        int res = erdd.open();
        if (res == EnumRetypeDeleteDialog.RETYPE) removedActions.put(name, EnumChangeAction.RETYPE);
        else if (res == EnumRetypeDeleteDialog.REMOVE)
          removedActions.put(name, EnumChangeAction.REMOVE);
        return res != EnumRetypeDeleteDialog.CANCEL;
      }
      return true;
    }

    private void removeItem(ValueItem vi) {
      removeItem(vi, true);
    }

    private void removeItem(ValueItem vi, boolean ask) {
      if (vi.isPinned) return;
      if (ask && isUsed(vi.getValue().getValue().toString())) {
        if (!dialogEffectOk(vi)) return;
        //removeUsages(vi.getValue().getValue().toString());
      }
      removeUsages(vi.getValue().getValue().toString());
      //usages.remove(vi.getValue().toString());
      //table.remove(vi);
      vals.get(lasttype).remove(vi);
      table.refresh();
    }

    private void changeItem(ValueItem vi, Object value) {
      if (find(value)) return;
      removeItem(vi, false);
      double ind = vi.getId();
      if (vi.isPinned) {
        ind = 1;
        if (vals.get(lasttype).size() > 0)
          for (ValueItem vi2 : vals.get(lasttype)) if (vi2.getId() >= ind) ind = vi2.getId() + 1;
      }
      Object old = vi.getValue().getValue();
      String rename = null;
      if (storedName.containsKey(vi.getValue().getValue().toString()))
        rename = storedName.remove(vi.getValue().getValue().toString());
      vi.setValue(value);
      vi.setId(ind);
      ;
      if (rename != null) storedName.put(vi.getValue().getValue().toString(), rename);
      else storedName.put(vi.getValue().getValue().toString(), old.toString());
      if (vi.getPinState()) vi.setPinState(false);
      addItem(vi);
      table.refresh();
    }

    private boolean find(Object ev) {
      for (ValueItem vi2 : vals.get(lasttype))
        if (ev.equals(vi2.getValue().getValue())) return true;
      return false;
    }

    private boolean addItem(ValueItem vi) {
      if (find(vi.getValue())) return false;
      vals.get(lasttype).add(vi);
      table.refresh();
      return true;
    }

    public EnumDefValueResult getResult() {
      Map<String, String> revert = new HashMap<>();
      for (String s : storedName.keySet()) revert.put(storedName.get(s), s);
      List<EnumValue> evlist = new ArrayList<>();
      for (ValueItem vi : vals.get(lasttype)) evlist.add(vi.getValue());
      return new EnumDefValueResult(lasttype, evlist, revert, removedActions);
      //	return new Object[]{lasttype,vals.get(lasttype),old.toString()};
    }

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
      final Table basetable =
          new Table(container, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.FULL_SELECTION);
      GridData gd = new GridData(GridData.FILL_HORIZONTAL);
      gd.heightHint = 250;
      basetable.setLayoutData(gd);
      table = new TableViewer(basetable);
      TableViewerColumn column =
          new TableViewerColumn(table, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
      column.getColumn().setText("Value");
      column.getColumn().setWidth(300);
      column.getColumn().setResizable(true);
      column.getColumn().setMoveable(true);

      table
          .getColumnViewerEditor()
          .addEditorActivationListener(
              new ColumnViewerEditorActivationListener() {
                KeyListener tabListener =
                    new KeyListener() {

                      @Override
                      public void keyReleased(KeyEvent e) {
                        int next = 1;
                        if (activated == commentEditor) next = 0;
                        if (e.keyCode == SWT.ARROW_RIGHT || e.keyCode == SWT.ARROW_LEFT) {
                          StructuredSelection ss = (StructuredSelection) table.getSelection();
                          if (ss != null && !ss.isEmpty()) {
                            Object o = ss.getFirstElement();
                            if (o != null) table.editElement(o, next);
                          }
                        }
                      }

                      @Override
                      public void keyPressed(KeyEvent e) {
                        // TODO Auto-generated method stub

                      }
                    };

                @Override
                public void beforeEditorDeactivated(ColumnViewerEditorDeactivationEvent event) {
                  CellEditor o = activated;
                  if (o != null) {
                    o.getControl().removeKeyListener(tabListener);
                    if (o.getControl() instanceof Composite)
                      for (Control c : ((Composite) o.getControl()).getChildren())
                        c.removeKeyListener(tabListener);
                  }
                }

                @Override
                public void beforeEditorActivated(ColumnViewerEditorActivationEvent event) {}

                @Override
                public void afterEditorDeactivated(ColumnViewerEditorDeactivationEvent event) {}

                CellEditor activated = null;

                @Override
                public void afterEditorActivated(ColumnViewerEditorActivationEvent event) {
                  // TODO Auto-generated method stub
                  CellEditor o;
                  if (getCellEditors().get(lasttype).isActivated())
                    o = getCellEditors().get(lasttype);
                  else o = commentEditor;
                  if (o != null && o.isActivated()) {
                    o.getControl().addKeyListener(tabListener);
                    if (o.getControl() instanceof Composite)
                      for (Control c : ((Composite) o.getControl()).getChildren())
                        c.addKeyListener(tabListener);
                    activated = o;
                  }
                }
              });

      column.setEditingSupport(
          new EditingSupport(column.getViewer()) {
            @Override
            protected void setValue(final Object element, Object value) {
              ValueItem it = (ValueItem) element;
              if (value == null || it == null) return;
              if (lasttype.equals(AttributeType.BOOL)) value = (value.equals(1)) ? "true" : "false";
              changeItem(it, value);
              //it.setValue(new EnumValue(value, it.getRealComment()));
              int cnt = 0;
              TableItem[] arr = table.getTable().getItems();
              for (int i = 0; i < arr.length; i++)
                if (arr[i].getData().equals(it) && !((ValueItem) arr[i].getData()).getPinState()) {
                  cnt++;
                }
              if (cnt > 2) removeItem(it);
              markDirty();
              if (!((ValueItem)
                      table.getTable().getItems()[table.getTable().getItemCount() - 1].getData())
                  .getPinState())
                table.add(new ValueItem(new EnumValue("", null), true, Double.MAX_VALUE));
            }

            @Override
            protected Object getValue(Object element) {
              //if(lasttype.equals(AttributeType.BOOL))
              //return element.toString().toLowerCase().equals("true")?1:0;
              //if(lasttype==AttributeType.LIST && ((ValueItem)element).getValue() instanceof ListValue)
              //return (ListValue)((ValueItem)element).getValue());
              return (((ValueItem) element).getValue()).getValue();
            }

            @Override
            protected CellEditor getCellEditor(Object element) {
              return getCellEditors().get(lasttype);
            }

            @Override
            protected boolean canEdit(Object element) {
              return true;
            }
          });

      commentEditor = new TextCellEditor(table.getTable());
      column = new TableViewerColumn(table, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
      column.getColumn().setText("Comment");
      column.getColumn().setWidth(300);
      column.getColumn().setResizable(true);
      column.getColumn().setMoveable(true);
      commentEditor
          .getControl()
          .addListener(
              SWT.KeyDown,
              new Listener() {
                @Override
                public void handleEvent(Event e) {
                  if (e.keyCode == SWT.ARROW_RIGHT || e.keyCode == SWT.ARROW_LEFT) {
                    StructuredSelection ss = (StructuredSelection) table.getSelection();
                    if (ss != null && !ss.isEmpty()) {
                      Object o = ss.getFirstElement();
                      table.editElement(o, 1);
                    }
                  }
                }
              });
      column.setEditingSupport(
          new EditingSupport(column.getViewer()) {
            @Override
            protected void setValue(final Object element, Object value) {
              ValueItem it = (ValueItem) element;
              if (it == null) return;
              markDirty();
              updateComment(it, value == null ? null : value.toString());
            }

            @Override
            protected Object getValue(Object element) {
              String o = ((ValueItem) element).getRealComment();
              if (o == null) o = "";
              return o;
            }

            @Override
            protected CellEditor getCellEditor(Object element) {
              return commentEditor;
            }

            @Override
            protected boolean canEdit(Object element) {
              return !((ValueItem) element).getPinState();
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
              if (((ValueItem) element).isPinned) return false;
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
              if (((ValueItem) element).isPinned) return false;
              moveDown(element);
              return false;
            }
          });

      TableViewerColumn cdel = new TableViewerColumn(table, SWT.FULL_SELECTION);
      cdel.getColumn().setResizable(false);
      cdel.getColumn().setWidth(25);
      cdel.setEditingSupport(
          new EditingSupport(table) {
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
              if (table.getTable().getItems().length > 1) {
                removeItem((ValueItem) element);
                markDirty();
              }

              return false;
            }
          });

      table.setLabelProvider(labelProvider);
      table.getControl().addDisposeListener(new DisposeListener() {
        @Override
        public void widgetDisposed(DisposeEvent e) {
          ih.dispose();
        }
      });
      table.getTable().setHeaderVisible(true);

      table.setComparator(
          new ViewerComparator() {
            @Override
            public int compare(Viewer viewer, Object e1, Object e2) {
              //if(e1 instanceof ValueItem && e2 instanceof ValueItem)
              //return (((ValueItem)e1).getValue().toString()).compareTo(((ValueItem) e2).getValue().toString());
              return ((ValueItem) e1).getPinState()
                  ? 1
                  : ((ValueItem) e2).getPinState()
                      ? -1
                      : ((ValueItem) e1).compareTo((ValueItem) e2);
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
              @SuppressWarnings("unchecked")
              List<ValueItem> l = (List<ValueItem>) inputElement;
              List<ValueItem> srt = new ArrayList<>();
              int cnt = l.size();
              for (ValueItem ev : l) {
                if (ev == null || ev.isPinned) {
                  RLogger.logInfo("Null value was found in enum definition ");
                  cnt--;
                } else {
                  int j = -2;
                  for (int i = 0; i < srt.size() && j == -2; i++)
                    if (srt.get(i).getId() > ev.getId()) j = i;
                  if (j < 0) {
                    j = srt.size();
                  }
                  srt.add(j, ev);
                }
              }
              Object[] vals = new Object[cnt + 1];
              int i = 0;
              for (ValueItem ev : srt) {
                vals[i++] = ev;
              }
              vals[vals.length - 1] =
                  new ValueItem(new EnumValue("", null), true, Double.MAX_VALUE);
              return vals;
            }
          });
      table.setLabelProvider(labelProvider);
      table.setInput(vals.get(lasttype));
      basetable.addKeyListener(
          new KeyListener() {
            @Override
            public void keyReleased(KeyEvent e) {}

            @Override
            public void keyPressed(KeyEvent e) {
              if (e.keyCode == SWT.CR) okPressed();
              if (e.keyCode == SWT.DEL) removeSelected();
              //if(e.keyCode == SWT.INSERT)
              //insertAfterSelected();
              if (e.keyCode == SWT.F2) {
                StructuredSelection ss = (StructuredSelection) table.getSelection();
                if (ss != null && !ss.isEmpty()) {
                  Object o = ss.getFirstElement();
                  table.editElement(o, 0);
                }
              }
              if ((e.stateMask & SWT.CTRL) != 0) {
                if (e.keyCode == SWT.ARROW_UP) {
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
                if (e.keyCode == SWT.ARROW_DOWN) {
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
      vals.put(lasttype, new ArrayList<ValueItem>());
      int i = 0;
      for (Object ev : v.values())
        vals.get(lasttype).add(new ValueItem((EnumValue) ev, false, i++));
      markDirty();
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
      vals.put(lasttype, new ArrayList<ValueItem>());
      int i = 0;
      for (Object ev : v.values())
        vals.get(lasttype).add(new ValueItem((EnumValue) ev, false, i++));
      markDirty();
      table.setInput(vals.get(lasttype));
      table.refresh();
    }

    public double getPreviousId(ValueItem vi) {
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

    public double getNextId(ValueItem vi) {
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

    protected void updateComment(ValueItem it, String string) {
      vals.get(lasttype).remove(it);
      it.setComment(string);
      vals.get(lasttype).add(it);
      table.refresh();
    }

    protected int getIndex(ValueItem element) {
      for (int i = 0; i < table.getTable().getItemCount(); i++) {
        if (((TableItem) table.getElementAt(i)).getData().equals(element)) return i;
      }
      return -1;
    }

    protected CellEditor getEditor(TreeNode node, AttributeType lasttype, Composite t, int style) {
      //if(lasttype == AttributeType.GENERATED)
      //return new GeneratorValueEditor(node,t,style);
      //TODO error??
      if (lasttype == AttributeType.STRING) return new TextCellEditor(t, style);
      if (lasttype == AttributeType.LIST) return new ListValueEditor(node, t, style, false);
      if (lasttype == AttributeType.INT) return new IntValueEditor(t, style);
      if (lasttype == AttributeType.BOOL) return new BoolValueEditor(t, style);
      if (lasttype == AttributeType.FLOAT) return new FloatValueEditor(t, style);
      if (lasttype == AttributeType.REFERENCE) return new ReferenceValueEditor(node, t, style);
      if (lasttype == AttributeType.RESOURCE) return new ResourceValueEditor(t, style);
      return null;
    }

    private void removeSelected() {
      Table t = table.getTable();
      int sel = t.getSelectionIndex();
      if (sel > -1 && sel < t.getItemCount()) {
        if (((ValueItem) t.getItem(sel).getData()).getPinState()) return;
        removeItem((ValueItem) table.getElementAt(sel));
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

    boolean sure = false;

    @Override
    protected void okPressed() {
      sure = true;
      List<ValueItem> list = new ArrayList<>();
      TableItem[] vls = table.getTable().getItems();
      int cnt = vls.length;
      for (int i = 0; i < cnt - 1; i++) {
        TableItem s = vls[i];
        Object val = s.getData();
        if (val != null) {
          if (val instanceof ValueItem) list.add((ValueItem) val);
          else list.add(new ValueItem(new EnumValue(val, null), false, i));
        }
      }
      vals.put(lasttype, list); //(lasttype,list);
      super.okPressed();
    }
  }
}
