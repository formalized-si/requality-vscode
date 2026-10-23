/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.unitesk.requality.eclipse.views.panels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationListener;
import org.eclipse.jface.viewers.ColumnViewerEditorDeactivationEvent;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ICellEditorValidator;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.DefaultToolTip;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;

import com.unitesk.requality.core.attribute.AppliedEnumValue;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeGenerator;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.EnumDefinitionValue;
import com.unitesk.requality.core.attribute.EnumValue;
import com.unitesk.requality.core.attribute.IAttributeFilter;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.GeneratorValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.attribute.valueprovider.TemplValueProvider;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.ui.dialogs.EnumRetypeDeleteDialog;
import com.unitesk.requality.eclipse.views.atypeeditors.BoolValueEditor;
import com.unitesk.requality.eclipse.views.atypeeditors.EnumDefinitionValueEditor;
import com.unitesk.requality.eclipse.views.atypeeditors.EnumDefinitionValueEditor.EnumChangeAction;
import com.unitesk.requality.eclipse.views.atypeeditors.EnumDefinitionValueEditor.EnumDefValueResult;
import com.unitesk.requality.eclipse.views.atypeeditors.FloatValueEditor;
import com.unitesk.requality.eclipse.views.atypeeditors.GeneratorValueEditor;
import com.unitesk.requality.eclipse.views.atypeeditors.IntValueEditor;
import com.unitesk.requality.eclipse.views.atypeeditors.ListValueEditor;
import com.unitesk.requality.eclipse.views.atypeeditors.ReferenceValueEditor;
import com.unitesk.requality.eclipse.views.properties.ProjectSettingsMainSection;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class AttributesPanel implements INodeChangeListener {
  //private static String Attribute.ENUM_CHAR = "\u24BA";
  public enum APanelWorkMode {
    GLOBAL,
    LOCAL
  };
  private FSId fsid;
  private static String LOADING_S = "LOADING...";
  private Map<String, Color> attributeMarkColors = new HashMap<>();
  private boolean transactionIgnore;
  private Image im = null;
  private String ignoreNameError = null;
  private static int COMBO_ACTIVATION = ComboBoxCellEditor.DROP_DOWN_ON_PROGRAMMATIC_ACTIVATION | 
      ComboBoxCellEditor.DROP_DOWN_ON_TRAVERSE_ACTIVATION | 
      ComboBoxCellEditor.DROP_DOWN_ON_MOUSE_ACTIVATION | 
      ComboBoxCellEditor.DROP_DOWN_ON_KEY_ACTIVATION;
  private static String
      MES_ENUM_DEF_DEL =
          "Altered ENUM definition is used in {cnt} ENUMs. Please select actions to be performed for all derived ENUMs:\n\t"
              + "'Remove' to remove all ENUMs\n\t'Change type' to change "
              + "from ENUM type to String\n\t'Cancel' to abort process",
      MES_ENUM_DEF_ID =
          "Altered ENUM definition is used in {cnt} ENUMs. Please select actions to be performed for all derived ENUMs:\n\t"
              + "'Rename' to change ENUM definition id to altered one\n\t'Remove' to remove all ENUMs\n\t'Change type' to change "
              + "from ENUM type to String\n\t'Cancel' to abort process";

  private TreeNode getEnumDefStorage() {
    if(node == null)
      return null;
    TreeDB db = node.getTreeDB();
    if(db.isVirtual())
      db = Instantiator.getRootTree(db);
    return db.getRootNode();
  }

  class TableLabelProvider implements ITableLabelProvider, ITableColorProvider {
    AttributesPanel ap;
    private List<Image> toBeDisposed = new LinkedList<>();
    public TableLabelProvider(AttributesPanel ap){
      this.ap = ap;
      ap.getControl().addDisposeListener(one -> {
        for(Image i :toBeDisposed)
          i.dispose();
        if(im!=null)
          im.dispose();//TODO test
      });
    }
    @Override
    public void removeListener(ILabelProviderListener arg0) {}

    @Override
    public void addListener(ILabelProviderListener arg0) {}

    @Override
    public boolean isLabelProperty(Object arg0, String arg1) {
      return false;
    }

    @Override
    public void dispose() {
    }

    @Override
    public String getColumnText(Object element, int columnIndex) {
      String title = attribTable.getTable().getColumn(columnIndex).getText();
      Attribute attr = (Attribute) element;
      if (attr.getKey().equals("")) return "";
      if (title.equals(NAME)) {
        return attr.getKey();
      }
      if (title.equals(TYPE)) {
        if (attr.getType().equals(AttributeType.ENUM)) {
          AppliedEnumValue aev = attr.getGValue();
          //Object[] val = (Object[]) attr.getRawValue();
          if (aev != null) {
            //String key = aev.getBaseKey();
            //Attribute b = node.getTreeDB().getRootNode().getAttribute(key);
            return aev.getBaseKey();
          }
          return AttributeType.ENUM.toString();
        }
        return attr.getType().toString();
      }
      if (title.equals(VALUE)) {
        //if (ap.readOnly && attr.getType() == AttributeType.REFERENCE)
          //return attr.getDefinition().getRawValue().getValue().toString();
        if (attr.getValue() == null) return "null";
        Object val = attr.getValue();
        if (attr.getType() == AttributeType.ENUM) {
          AppliedEnumValue aev = attr.getGValue();
          if (aev != null) {
            return Attribute.valueToString(node.attrs(), aev.getType(), aev.getValue());
          }
        }
        if (attr.getType() == AttributeType.LIST
            || attr.getType() == AttributeType.ENUM_DEFINITION) val = attr.getValue();
        if (attr.getType() == AttributeType.REFERENCE){
          TypedValue tv = attr.getTValue();
          List<Object> ret = new LinkedList<>();
          if(tv.getType()==AttributeType.REFERENCE)
            ret.add(((ReferenceValue)tv.getValue()).toString(node.attrs()));
          else
          {
            ListValue lv = (ListValue) tv.getValue();
            for(Object o : lv.getValues())
              ret.add(((ReferenceValue)o).toString(node.attrs()));
          }
          StringBuilder sb = new StringBuilder();
          for(Object s:ret)
          {
            if(sb.length()>0)
              sb.append(", ");
            sb.append(s);
          }
          return sb.toString();
        }
        return Attribute.valueToString(node.attrs(),attr.getType(), val);
      }
      if (title.equals(SCOPE) ){
        AttributeAvailability aa = attr.getAvailability();
        if(aa == null)
          return LOADING_S;
        else
          return attr.getAvailability().toString();
      }
      if (title.equals(GENERATOR))
        return attr.getDefinition().getGenerator() == null ? "" : attr.getDefinition().getGenerator().getGeneratorType().toString();
      if (title.isEmpty()
          || title.equals(GENERATOR) 
          || title.equals(LINK)) return "";
      return "n/a";
    }
    @Override
    public Image getColumnImage(Object element, int columnIndex) {
      String title = attribTable.getTable().getColumn(columnIndex).getText();
      Attribute a = ((Attribute) element);
      if (!a.getKey().equals("")) {
        if (title.equals(LINK)){
          if(!a.getOrigin().equals(node.getUUId()))
            return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_TOOL_BACK);
          AttributeDefinition ad = node.getTreeDB().getAttributeRules().getRequiredAttrRule().getDefinition(node.getType(), a.getKey());
          if(ad!=null) {
            if(im == null) {
              ImageDescriptor id = Activator.getImageDescriptor("/icons/multilink.png");
              if(id!=null)
                im = id.createImage();
            }
            toBeDisposed.add(im);
            return im;
          }
        }
        if (columnIndex == (mode == APanelWorkMode.GLOBAL ? 5 : 6) && enabled && !readOnly) {
          if (a.getOrigin().equals(node.getUUId()))
            return PlatformUI.getWorkbench()
                .getSharedImages()
                .getImage(ISharedImages.IMG_ELCL_REMOVE);
        }
      }

      return null;
    }

    @Override
    public Color getForeground(Object element, int columnIndex) {
      Attribute a = ((Attribute) element);
      if(a.isReadOnly(node.getTreeDB()))
        return new Color(attribTable.getTable().getDisplay(), new RGB(128,128,128));  
      return null;
    }

    @Override
    public Color getBackground(Object element, int columnIndex) {
      Attribute a = ((Attribute) element);
      if (!a.getKey().equals(""))
      {
        if(a.getAvailability()==null)
          return new Color(attribTable.getTable().getDisplay(), new RGB(128,128,128));
        String key = a.getKey();
        if(attributeMarkColors.containsKey(key))
          return attributeMarkColors.get(key);
        if (!readOnly && (columnIndex == 2 || columnIndex == 3) && a.getType().equals(AttributeType.ENUM)) {
          AppliedEnumValue aev = a.getGValue();
          //Object[]val = (Object[])a.getRawValue();
          if (aev != null) {
            String name = aev.getBaseKey();
            Object nowval = aev.getValue();
            Attribute b = getEnumDefStorage().attrs().getAttribute(name);
            if (b != null && columnIndex == 2) return null;
            if (b != null && b.getType().equals(AttributeType.ENUM_DEFINITION)) {
              EnumDefinitionValue edv = b.getGValue();
              if (edv.containsValue(nowval)) return null;
            }
          }
          return new Color(attribTable.getTable().getDisplay(), new RGB(255, 160, 0));
        }
      }
      return null;
    }
  }

  private Set<String> names;

  private static IAttributeNameFilter defnamefilter =
      new IAttributeNameFilter() {

        @Override
        public boolean filter(String key) {
          return !key.startsWith("_");
        }
      };

  private static IAttributeFilter deffilter =
      new IAttributeFilter() {

        @Override
        public boolean filter(Attribute a) {
          return !a.getKey().startsWith("_")&&!a.isVirtual();
        }
      };

  private final class AttributeTableContentProvider implements IStructuredContentProvider {
    Attribute cnull =null,
        cednull=null;
    UUID lastUUID = null;
    public Object[] attributes = new Attribute[0];
    @Override
    public Object[] getElements(Object input) {
      if (input instanceof TreeNode) {
        TreeNode node = (TreeNode) input;
        if(cnull == null|| !node.getUUId().equals(lastUUID)){
          lastUUID = node.getUUId();
          cnull = new Attribute(
              new AttributeDefinition(node.getUUId(), "", new ConstValueProvider(AttributeType.STRING, ""),0), node.attrs());
          cednull = new Attribute(
              new AttributeDefinition(node.getUUId(), "", new ConstValueProvider(AttributeType.ENUM_DEFINITION, AttributeType.getDefaultValue(AttributeType.ENUM_DEFINITION)),0), node.attrs());
        }
        ArrayList<Attribute> data = new ArrayList<>();
        for (String key : node.attrs().getAllAttributeKeys()) {
          if(nameFilter!=null && nameFilter.filter(key)){
            Attribute a = node.attrs().getAttribute(key);
            if (a != null && filter.filter(a)) { //null == removed
              data.add(a);
            }
          }
        }
        if (mode == APanelWorkMode.LOCAL)
          data.add(
              cnull);
        else
          data.add(cednull);
        //cmce
        attributes = data.toArray(new Object[0]);
        return attributes;
      }
      if(input instanceof Object[])
        return (Object[]) input;
      return null;
    }

    @Override
    public void inputChanged(Viewer arg0, Object arg1, Object arg2) {
      updateSorting(true);
    }
  }

  public void setAttributeFilter(IAttributeFilter f,IAttributeNameFilter nf) {
    filter = f;
    nameFilter = nf;
  }

  private IAttributeNameFilter nameFilter = defnamefilter;
  private IAttributeFilter filter = deffilter;
  private Composite panel;
  private APanelWorkMode mode = APanelWorkMode.LOCAL;
  private static final String NAME="Name",TYPE="Type",VALUE="Value",SCOPE="Scope",GENERATOR="Generator", LINK="L";
  private static final String columnNames[] = new String[] {NAME,TYPE,VALUE,SCOPE};
  private TreeNode node;
  private TableViewer attribTable;
  private AttributeTableContentProvider provider;
  private ComboBoxCellEditor enumsel;
  private DefaultToolTip enumtip, tabletip;
  private boolean enabled = true;

  public static class EnumDefUsageRes {
    public Map<String, Map<TreeNode, Set<String>>> map;
    public long summ;

    public EnumDefUsageRes(Map<String, Map<TreeNode, Set<String>>> m, long s) {
      this.map = m;
      this.summ = s;
    }
  }

  public static EnumDefUsageRes getEnumDefUsages(TreeNode node, String name) {
    Map<String, Map<TreeNode, Set<String>>> mp = new HashMap<>();
    int cnt = 0;
    DeepFirstTreeWalker walker =
        new DeepFirstTreeWalker(node.getTreeDB().getRootNode(), true, true, true);
    Iterator<TreeNode> it = walker.iterator();
    while (it.hasNext()) {
      TreeNode node2 = it.next();
      if (node2.getId().equals("Instances")) //skip insts
      continue;
      if (node2.getType().equals(Document.TYPE_NAME)) continue;
      for (Attribute b : node2.attrs())
        if (b.getType().equals(AttributeType.ENUM)) {
          AppliedEnumValue aev = b.getGValue();
          if (aev != null && aev.getBaseKey().equals(name)) {
            cnt++;
            String v = aev.getValue().toString();
            if (!mp.containsKey(v)) mp.put(v, new HashMap<TreeNode, Set<String>>());
            if (!mp.get(v).containsKey(node2)) mp.get(v).put(node2, new TreeSet<String>());
            mp.get(v).get(node2).add(b.getKey());
          }
        }
    }
    //null key to overall summ
    return new EnumDefUsageRes(mp, cnt);
  }

  private boolean applyEnumDefDialogResult(String enumDefKey, int res) {
    return applyEnumDefDialogResult(enumDefKey, res, enumDefKey);
  }

  private boolean applyEnumDefDialogResult(String enumDefKey, int res, String newId) {
    if (res == IDialogConstants.CANCEL_ID) return false;
    DeepFirstTreeWalker walker =
        new DeepFirstTreeWalker(node.getTreeDB().getRootNode(), true, true, false);
    Iterator<TreeNode> it = walker.iterator();
    while (it.hasNext()) {
      TreeNode node2 = it.next();
      boolean change = false;
      for (Attribute b : node2.attrs()) {
        if (b.getType().equals(AttributeType.ENUM)
            && ((AppliedEnumValue) b.getValue()).getBaseKey().equals(enumDefKey)) {
          change = true;
          if (res == EnumRetypeDeleteDialog.REMOVE) node2.attrs().removeAttribute(b.getKey());
          else if (res == EnumRetypeDeleteDialog.RETYPE) {
            AttributeDefinition newad = new AttributeDefinition(
                node2.getUUId(),b.getKey(),b.getAvailability(),
                new ConstValueProvider(((AppliedEnumValue) b.getValue()).getType(), 
                    ((AppliedEnumValue) b.getValue()).getValue()),b.getDefinition().getFlags());
            node2.attrs().setAttribute(newad);
          } else if (res == EnumRetypeDeleteDialog.CHANGE_ID) {
            AppliedEnumValue aev = b.getGValue();
            AttributeDefinition ad = new AttributeDefinition(b.getOrigin(), b.getKey(), b.getAvailability(),
                new ConstValueProvider(AttributeType.ENUM, new AppliedEnumValue(
                    aev.getValue(), aev.getComment(), aev.getType(), newId)),b.getDefinition().getFlags());
            node2.attrs().setAttribute(ad);
          }
        }
      }
      if (change) node2.commitAttributes();
    }
    return true;
  }

  private boolean removeNode(Shell shell, String name, boolean startTransaction) {
    Attribute a = node.attrs().getAttribute(name);
    if (!a.getOrigin().equals(node.getUUId())) return false;
    boolean started = false;
    if (startTransaction && !transactionIgnore)
      //TODO may be message
      started = node.getTreeDB().startTransaction("Update "+node.getTreeDB().getProjectName()+":"+ node.getType(), true);
    if(!started && !node.getTreeDB().inTransaction())
      return false;
    try {
      int res = Dialog.CANCEL;
      if (a.getType().equals(AttributeType.ENUM_DEFINITION)) {
        long cnt = getEnumDefUsages(node, a.getKey()).summ;
        if (cnt > 0) {
          EnumRetypeDeleteDialog d =
              new EnumRetypeDeleteDialog(
                  shell, MES_ENUM_DEF_DEL.replace("{cnt}", Long.toString(cnt)));
          res = d.open();
          if (!applyEnumDefDialogResult(a.getKey(), res)) return false;
        }
      }
      Attribute old = node.attrs().removeAttribute(name);
      node.commitAttributes();
      refreshTable(attribTable, old);
      attribTable.getControl().setFocus();
      panel.notifyListeners(SWT.Modify, new Event());
      if (started) {
        node.getTreeDB().commit();
        started = false;
      }
    }finally {
      if (started)
        node.getTreeDB().commit();
    }
    return true;
  }

  private String[] getAvailableTypes() {
    int correction = mode == APanelWorkMode.LOCAL ? 1 : 0;
    AttributeType[] ats = AttributeType.visibleValues();
    String[] ret = new String[ats.length - correction];
    for (int i = 0; i < ret.length; i++) ret[i] = ats[i].toString();
    return ret;
  }

  private void removeAttribute() {
    TableItem[] items = attribTable.getTable().getSelection();
    String[] names = new String[items.length];
    for (int i = 0; i < items.length; i++) names[i] = (((Attribute) items[i].getData())).getKey();

    removeAttribute(panel.getShell(), names, false);
  }
  private boolean removeAttribute(Shell shell, String[] rawkeys, boolean b) {
    TableItem[] items = attribTable.getTable().getItems();
    List<TableItem> filtered = new ArrayList<>();
    Set<String> keys = new HashSet<>(Arrays.asList(rawkeys));
    for (TableItem ti : items)
      if (keys.contains((((Attribute) ti.getData())).getKey())
          && node.getUUId().equals(((Attribute) ti.getData()).getOrigin())) filtered.add(ti);
    final TreeNode node = this.node;
    items = filtered.toArray(new TableItem[0]);
    int cnt = 0;
    for (int i = 0; i < items.length; i++) {
      Object attrName = ((Attribute) items[i].getData()).getKey();
      if (!attrName.equals("")) cnt++;
    }
    if (cnt == 0) return false;
    //TODO message on fail
    TreeDB db = node.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    if (!transactionIgnore && !tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
      return false;
    String mes =
        items.length <= 1
            ? "Are you sure you want to remove selected attribute?"
            : "Are you sure you want to remove selected attributes?";

    if ((items.length > 0)
        && (MessageDialog.openQuestion(panel.getShell(), "Attribute Editor", mes) == true)) {
      boolean started = !transactionIgnore&& node.getTreeDB().startTransaction("Update "+node.getTreeDB().getProjectName()+":" + node.getType(), true);
      boolean st = transactionIgnore || started;
      if (st) {
        try {
          for (int i = 0; i < items.length; i++) {
            Object attrName = ((Attribute) items[i].getData()).getKey();
            if (attrName.equals("")) continue;
            if (!removeNode(attribTable.getControl().getShell(), attrName.toString(), b)) {
              if (started) {
                node.getTreeDB().rollback();
                started = false;
              }
              return false;
            }
          }
          if (started) {
            started = false;
            node.getTreeDB().commit();
          }
        }finally {
          if(started)
            node.getTreeDB().rollback();
        }

        return true;
      }
    }
    return false;
  }

  private Map<String, Integer> order = new HashMap<>();

  private int compare(Attribute a, Attribute b) {
    if (!order.containsKey(a.getKey()))
      order.put(a.getKey(), a.getKey().equals("") ? Integer.MAX_VALUE : order.size());
    if (!order.containsKey(b.getKey()))
      order.put(b.getKey(), b.getKey().equals("") ? Integer.MAX_VALUE : order.size());
    return order.get(a.getKey()).compareTo(order.get(b.getKey()));
  }

  private Map<AttributeType, CellEditor> valueEditors;
  private GeneratorValueEditor generatorEditor;
  private ComboBoxCellEditor scopeEditor;
  private ComboBoxCellEditor typeCombo;
  private ControlDecoration nameDecorator;

  public AttributesPanel(FSId fsid, Composite parent) {
    this(fsid, parent, APanelWorkMode.LOCAL);
  }

  private void setEnumSelItems(String[] vals) {
    enumsel.setItems(vals);
  }

  final Map<String,CellEditor> editors;
  private boolean readOnly;

  private ControlDecoration createDecorator(Control c) {
    ControlDecoration controlDecoration = new ControlDecoration(c, SWT.LEFT | SWT.TOP);
    FieldDecoration fieldDecoration =
        FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR);
    controlDecoration.setImage(fieldDecoration.getImage());
    return controlDecoration;
  }

  public AttributesPanel(FSId fsid, final Composite parent, APanelWorkMode md) {
    this(fsid, parent, md, false);
  }
  public AttributesPanel(FSId fsid, final Composite parent, APanelWorkMode md, boolean readOnly) {
    this.fsid = fsid;
    parent.setBackgroundMode(SWT.INHERIT_FORCE);//fixes wrong colors
    this.readOnly = readOnly;
    GridLayout layout = new GridLayout(1, false);
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    this.mode = md;

    panel = new Composite(parent, SWT.NONE);
    panel.setLayoutData(new GridData(GridData.FILL_BOTH));
    panel.setLayout(layout);
    panel.addDisposeListener(new DisposeListener() {
      @Override
      public void widgetDisposed(DisposeEvent e) {
        setNode(null, true);
      }
    });

    Label label = new Label(panel, SWT.NONE);
    label.setText("Attributes:");

    attribTable =
        new TableViewer(
            panel, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.RESIZE | SWT.FULL_SELECTION);
    tabletip = new DefaultToolTip(attribTable.getControl());
    attribTable
        .getControl()
        .addMouseMoveListener(
            new MouseMoveListener() {
              private String lasttext = "";

              @Override
              public void mouseMove(MouseEvent e) {
                ViewerCell vc = attribTable.getCell(new Point(e.x, e.y));
                if (vc != null) {
                  Attribute a = (Attribute) (vc.getElement());
                  if (a != null) {
                    String text = null;
                    String title = attribTable.getTable().getColumn(vc.getColumnIndex()).getText();
                    if (!readOnly&&!a.getKey().equals("")){
                      if (title.equals(LINK)){
                        if(!a.getOrigin().equals(node.getUUId())){
                          TreeNode or = node.getTreeDB().getNode(a.getOrigin());
                          text =  "Attribute is inherited from "+or.getUserVisibleName();
                        }else{
                          AttributeDefinition ad = node.getTreeDB().getAttributeRules().getRequiredAttrRule()
                                .getDefinition(node.getType(), a.getKey());
                          if(ad!=null)
                            text = "Attribute is defined in project setting as mandatory attribute. Mandatory attributes cannot be deleted.";
                        }
                      }
                      else
                      if ((title.equals(TYPE)||title.equals(VALUE))
                          && a.getType().equals(AttributeType.ENUM)) {
                        AppliedEnumValue aev = (AppliedEnumValue) a.getValue();
                        if (aev != null) {
                          String name = aev.getBaseKey();
                          Object nowval = aev.getValue();
                          Attribute b = getEnumDefStorage().attrs().getAttribute(name);
                          if (b == null)
                            text = "Enum definition with name '" + name + "' is not found!";
                          if (b != null && b.getType().equals(AttributeType.ENUM_DEFINITION)) {
                            EnumDefinitionValue edv = b.getGValue();
                            if (edv == null || !edv.containsValue(nowval)) {
                              text =
                                  "Definition of '"
                                      + name
                                      + "' does not contain value '"
                                      + nowval
                                      + "'";
                            } else {
                              Object comm = aev.getComment();
                              if (comm != null && title.equals(VALUE)) text = comm.toString();
                            }
                          } else text = "Attribute with name '" + name + "' has incorrect type";
                        }
                      }
                  }
                    tabletip.activate();
                    if (text == null) tabletip.deactivate();
                    else if (!lasttext.equals(text)) {
                      tabletip.setText(text);
                      lasttext = text;
                    }
                  }
                }
              }
            });
    attribTable
        .getControl()
        .addKeyListener(
            new KeyListener() {

              @Override
              public void keyReleased(KeyEvent e) {
                // TODO Auto-generated method stub
              }

              @Override
              public void keyPressed(KeyEvent e) {
                if(readOnly)
                  return;
                // TODO Auto-generated method stub
                //if(e.keyCode == SWT.INSERT)
                //insertAttribute();
                if (e.keyCode == SWT.DEL) removeAttribute();
                if (e.keyCode == SWT.F2) changeEditor(1);

                //open editor
                //change editor
              }
            });
    attribTable.setComparator(
        new ViewerComparator() {
          public int compare(Viewer viewer, Object e1, Object e2) {
            if (e1 instanceof Attribute && e2 instanceof Attribute) {
              Attribute a1 = (Attribute) e1, a2 = (Attribute) e2;
              return AttributesPanel.this.compare(a1, a2);
              //return TreeNodeComparator.compareStrings(a1.getKey(), a2.getKey());
            }
            return super.compare(viewer, e1, e2);
          }
        });
    GridData gridData = new GridData(GridData.FILL_BOTH);
    attribTable.getControl().setLayoutData(gridData);
    attribTable
        .getControl()
        .addFocusListener(
            new FocusListener() {

              @Override
              public void focusLost(FocusEvent e) {
                // TODO Auto-generated method stub
              }

              @Override
              public void focusGained(FocusEvent e) {
                ISelection s = attribTable.getSelection();
                if (s == null || s.isEmpty()) {
                  attribTable.getTable().select(0);
                }
              }
            });
    ColumnViewerToolTipSupport.enableFor(attribTable, ToolTip.RECREATE);

    // Create columns
    int i = 0;
    // Editors
    editors = new HashMap<>();// new CellEditor[columnNames.length - (mode == APanelWorkMode.GLOBAL ? 1 : 0)];
    editors.put(NAME, new TextCellEditor(attribTable.getTable(), readOnly?SWT.READ_ONLY:SWT.FULL_SELECTION));
    editors.get(NAME).setValidator(
        new ICellEditorValidator() {
          @Override
          public String isValid(Object value) {
            String s = (String) value;
            if (s.equals("")) return "Name is empty";
            if (node.attrs().getAttribute(s) != null && !s.equals(ignoreNameError))
              return "Attribute with the same name already exists";
            if (s.startsWith("_")) return "Attribute name cannot be started with '_' character";
            if (!Attribute.checkNameIsValid(s, false))
              return "Name contains illegal characters or starts with _ or $";
            return null;
          }
        });
    nameDecorator = createDecorator(editors.get(NAME).getControl());
    nameDecorator.hide();

    editors.get(NAME)
        .getControl()
        .addKeyListener(
            new KeyListener() {
              @Override
              public void keyReleased(KeyEvent e) {
                Object o = e.widget;
                if (o != null) o = ((Text) o).getText();
                if (o == null) return;
                String s = o.toString();
                String msg = editors.get(NAME).getValidator().isValid(s);
                nameDecorator.hide();
                if (msg != null) {
                  nameDecorator.setDescriptionText(msg);
                  nameDecorator.show();
                }
              }

              @Override
              public void keyPressed(KeyEvent e) {}
            });
    enumsel = new ComboBoxCellEditor(attribTable.getTable(), new String[0], SWT.READ_ONLY);
    //    enumsel.setActivationStyle(ComboBoxCellEditor.DROP_DOWN_ON_PROGRAMMATIC_ACTIVATION);
    
    enumsel.setActivationStyle(COMBO_ACTIVATION);
    
    Control c = enumsel
            .getControl();
    
    c.addMouseTrackListener(
        new MouseTrackListener() {

          @Override
          public void mouseHover(MouseEvent e) {
            // TODO Auto-generated method stub

          }

          @Override
          public void mouseExit(MouseEvent e) {}

          int lastnum = -1;

          @Override
          public void mouseEnter(MouseEvent e) {
            if(readOnly)
              return;
            for (final Shell s : enumsel.getControl().getShell().getShells()) {
              if (s.getChildren().length == 1
                  && (s.getChildren()[0] instanceof org.eclipse.swt.widgets.List)) {
                final org.eclipse.swt.widgets.List l =
                    (org.eclipse.swt.widgets.List) s.getChildren()[0];
                if (enumsel.getItems().length == l.getItemCount()) {
                  enumtip = new DefaultToolTip(l, DefaultToolTip.RECREATE, false);
                  enumtip.activate();
                  //Find list
                  l.addMouseMoveListener(
                      new MouseMoveListener() {

                        @Override
                        public void mouseMove(MouseEvent e) {
                          //l.getBounds().height
                          ScrollBar sb = l.getVerticalBar();
                          int realmax = sb.getMaximum() - sb.getThumb() - sb.getMinimum();
                          int sel = sb.getSelection() - sb.getMinimum();
                          //RLogger.logInfo((sel*100/realmax));
                          //RLogger.logInfo(l.getSize().y+" "+l.getVerticalBar().getSelection()+" "+l.getVerticalBar().getThumb()+" "+l.getVerticalBar().getSelection()+" "+l.getVerticalBar().getMinimum());
                          int span =
                              realmax == 0
                                  ? 0
                                  : sel
                                      * (l.getItemCount() * l.getItemHeight() - l.getSize().y)
                                      / realmax;
                          int num = (e.y + span) / l.getItemHeight();
                          if (num >= l.getItemCount()) num = 0;
                          if (num != lastnum) {
                            enumtip.deactivate();
                            Attribute a =
                                (Attribute)
                                    (attribTable.getTable().getSelection()[0].getData());
                            AppliedEnumValue aev = a.getGValue();
                            //Object[]vl = (Object[]) a.getRawValue();
                            if (aev != null) {
                              Attribute base =
                                  getEnumDefStorage().attrs().getAttribute(aev.getBaseKey());
                              if (base != null
                                  && base.getType() == AttributeType.ENUM_DEFINITION) {
                                EnumDefinitionValue edf = base.getGValue();
                                if (num < edf.getValues().size()) {
                                  String s = edf.getValues().get(num).getComment();
                                  if (s != null) {
                                    enumtip.setText(s);
                                    enumtip.activate();
                                  }
                                }
                              }
                            }
                            lastnum = num;
                          }
                        }
                      });
                }
              }
            }
            // TODO Auto-generated method stub
          }
        });
    final KeyListener tabListener =
        new KeyListener() {

          @Override
          public void keyReleased(KeyEvent e) {}

          @Override
          public void keyPressed(KeyEvent e) {
            if (e.keyCode == SWT.ARROW_RIGHT && e.stateMask == SWT.CONTROL) changeEditor(1);
            if (e.keyCode == SWT.ARROW_LEFT && e.stateMask == SWT.CONTROL) changeEditor(-1);
            //changeEditor();

          }
        };
    attribTable
        .getColumnViewerEditor()
        .addEditorActivationListener(
            new ColumnViewerEditorActivationListener() {
              @Override
              public void beforeEditorDeactivated(ColumnViewerEditorDeactivationEvent event) {
                for (CellEditor o : collectEditors())
                  if (o != null) {
                    o.getControl().removeKeyListener(tabListener);
                    if (o.getControl() instanceof Composite)
                      for (Control c : ((Composite) o.getControl()).getChildren())
                        c.removeKeyListener(tabListener);
                  }
                ignoreNameError = null;
              }

              @Override
              public void beforeEditorActivated(ColumnViewerEditorActivationEvent event) {
                Object o = event.getSource();
                if (o instanceof ViewerCell) {
                  ViewerCell vc = (ViewerCell)o;
                  int ci = vc.getColumnIndex();
                  if (ci == 1) {
                    ignoreNameError = vc.getText();
                  }
                }
              }

              @Override
              public void afterEditorDeactivated(ColumnViewerEditorDeactivationEvent event) {
                if (((Attribute) ((ViewerCell) event.getSource()).getElement()).getType()
                    == AttributeType.ENUM_DEFINITION) updateTypeCombo(null);
              }

              @Override
              public void afterEditorActivated(ColumnViewerEditorActivationEvent event) {
              
                // TODO Auto-generated method stub
                for (CellEditor o : collectEditors())
                  if (o != null && o.isActivated()) {
                    Control econ = o.getControl();
                    econ.addKeyListener(tabListener);
                    if (econ instanceof Composite) {
                      for (Control c : ((Composite) o.getControl()).getChildren())
                        c.addKeyListener(tabListener);
                    }
                    break;
                  }
              }

              private List<CellEditor> collectEditors() {
                List<CellEditor> list = new LinkedList<>(
                    Arrays.asList(getEditors()));
                editors.values().forEach(one->{
                  if(!list.contains(one))
                    list.add(one);
                  });
                list.remove(null);
                return list;
        }
            });
    String[] types = getAvailableTypes();
    typeCombo = new ComboBoxCellEditor(attribTable.getTable(), types, SWT.READ_ONLY | COMBO_ACTIVATION);
    typeCombo.setActivationStyle(COMBO_ACTIVATION);
    //typeCombo.setActivationStyle(ComboBoxCellEditor.DROP_DOWN_ON_PROGRAMMATIC_ACTIVATION);
    typeCombo.setValidator(
        new ICellEditorValidator() {
          @Override
          public String isValid(Object value) {
            if (value != null) {
              value.toString();
              //TODO add check
            }
            return null;
          }
        });
    editors.put(TYPE, typeCombo);
    String[] scopevars = new String[AttributeAvailability.values().length];
    int pai = 0;
    for (AttributeAvailability pa : AttributeAvailability.values())
      scopevars[pai++] = pa.toString();

    scopeEditor = new ComboBoxCellEditor(attribTable.getTable(), types, SWT.READ_ONLY);
    scopeEditor.setActivationStyle(COMBO_ACTIVATION);
    //scopeEditor.setActivationStyle(ComboBoxCellEditor.DROP_DOWN_ON_PROGRAMMATIC_ACTIVATION);
    scopeEditor.setItems(scopevars);
    if (mode == APanelWorkMode.LOCAL) editors.put(SCOPE, scopeEditor);
    valueEditors = new HashMap<>();
    valueEditors.put(
        AttributeType.STRING, new TextCellEditor(attribTable.getTable(), readOnly?SWT.READ_ONLY:SWT.FULL_SELECTION));
    valueEditors.put(
        AttributeType.HTML, new TextCellEditor(attribTable.getTable(), readOnly?SWT.READ_ONLY:SWT.FULL_SELECTION));
    //TODO html editor
    valueEditors.put(
        AttributeType.BOOL, new BoolValueEditor(attribTable.getTable(), readOnly?SWT.READ_ONLY:SWT.FULL_SELECTION));
    valueEditors.put(
        AttributeType.INT, new IntValueEditor(attribTable.getTable(), readOnly?SWT.READ_ONLY:SWT.FULL_SELECTION));
    valueEditors.put(
        AttributeType.FLOAT, new FloatValueEditor(attribTable.getTable(), readOnly?SWT.READ_ONLY:SWT.FULL_SELECTION));
    //valueEditors.put(AttributeType.RESOURCE, new ResourceValueEditor(attribTable.getTable(),SWT.FULL_SELECTION));
    //editors[3] = new TextCellEditor(attribTable.getTable(),SWT.FULL_SELECTION);
    //editors[4] = new TextCellEditor(attribTable.getTable(),SWT.FULL_SELECTION);
    TableViewerColumn clink = new TableViewerColumn(attribTable, readOnly?SWT.READ_ONLY:SWT.FULL_SELECTION);
    TableColumn linkColumn = clink.getColumn(); 
    linkColumn.setResizable(false);
    linkColumn.setText(LINK);
    linkColumn.setWidth(25);
    i = 0;
    attribTable.addDoubleClickListener(
        new IDoubleClickListener() {

          @Override
          public void doubleClick(DoubleClickEvent event) {
            updateSorting(false);
            panel.update();
          }
        });
    for (String colName : columnNames) {
      if (colName.equals(SCOPE) && mode == APanelWorkMode.GLOBAL) {
        i++;
        continue;
      }
      final TableViewerColumn column =
          new TableViewerColumn(attribTable, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);

      column.getColumn().setText(colName);
      column.getColumn().setWidth(100);
      column.getColumn().setResizable(true);
      column.getColumn().setMoveable(true);
      //final int iv = i;
      Display d = Display.findDisplay(Thread.currentThread());
      column
          .getColumn()
          .addListener(
              SWT.Selection,
              new Listener() {
                @Override
                public void handleEvent(Event event) {
                  String name = column.getColumn().getText();
                  updateSorting(sortingMethod.equals(name) ? name+"|" : name, true);
                  refreshTable(attribTable, null);
                }
              });
      column.setEditingSupport(
          new EditingSupport(column.getViewer()) {

            @Override
            protected void setValue(final Object element, Object val) {
              if(readOnly)
                return;
              CellEditor editor = getCellEditor(element);
              if(!editor.isDirty())
                return;
              final TreeNode node = AttributesPanel.this.node;
              if(node == null)
                return;
              int sel = attribTable.getTable().getSelectionIndex();
              String name = column.getColumn().getText();
              nameDecorator.hide();
              if (editor.getValidator() != null
                      && editor.getValidator().isValid(val) != null
                  || val == null) {
                //TODO message
                return;
              }
              Attribute a = (Attribute) element;
              if (a.getKey().equals("") && (!name.equals(NAME) || (val.toString().trim()).equals("")))
                return;
              String[]ltypes = typeCombo.getItems();
              boolean cneditordirty=getCellEditor(element).isDirty();
              final TableViewer attribTable = AttributesPanel.this.attribTable;
              final Composite panel = AttributesPanel.this.panel;
              final AttributeTableContentProvider provider = AttributesPanel.this.provider;
              final boolean transactionIgnore = AttributesPanel.this.transactionIgnore;
              final APanelWorkMode mode = AttributesPanel.this.mode;
              final String[] types = getAvailableTypes();
              final Map<String, Integer> order = AttributesPanel.this.order;
              final ComboBoxCellEditor typeCombo = AttributesPanel.this.typeCombo;
              WorkspaceJob wj = new WorkspaceJob("Edit '"+(node!=null?node.getUserVisibleName():"node")+"' attributes"){
                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
                  if(node == null)
                    return Status.CANCEL_STATUS;
                  boolean changed = false;
                  Object value = val;
                  boolean started = false;
                  if (!transactionIgnore)
                    started = false;
                  try{
                    {
                      String key = a.getKey();
                      AttributeDefinition old = null;
                      AttributeType at = a.getType();
                      if (a.getDefinition().getGenerator() != null) at = a.getDefinition().getGenerator().getAttributeType();
                      AttributeAvailability replpar = null;
                      if (mode == APanelWorkMode.GLOBAL)
                        replpar =
                            a.getType().equals(AttributeType.ENUM_DEFINITION)
                                ? AttributeAvailability.LOCAL
                                : AttributeAvailability.SUBTREE;
                      if (name.equals(NAME)) {
                        if (!a.getKey().equals("")) old = node.attrs().removeAttribute(a.getKey()).getDefinition();
                        String newkey = (value.toString()).trim();
                        AttributeDefinition b = new AttributeDefinition(node.getUUId(), newkey, 
                           mode==APanelWorkMode.GLOBAL?replpar:a.getAvailability(),
                                old!=null?old.getValueProvider():
                                  AttributeDefinition.detectValueProvider(newkey, a.getType(), a.getValue(),
                                    TempFolderFilesProvider.EMPTY),
                                old!=null?old.getFlags():
                                  AttributeDefinition.detectFlags(newkey));
                        node.attrs().setAttribute(b);
                        key = newkey;
                        changed = true;
                      }
                      if (name.equals(TYPE) && (Integer) value > -1) {
                        String selType = ltypes[(Integer) value];
                        if (!selType.equals(a.getType().toString())
                            && (a.getType() != AttributeType.ENUM
                                || !(a.getValue() instanceof AppliedEnumValue)
                                || !selType.equals(
                                    Attribute.ENUM_CHAR
                                        + ((AppliedEnumValue) (a.getGValue())).getBaseKey()))) {
                          String k = a.getKey();
                          int ind = (Integer) value;
                          AttributeType newt = AttributeType.ENUM;
                          if (ind < types.length)
                            newt = AttributeType.valueOf(types[(Integer) value]);
  
                          if (mode == APanelWorkMode.GLOBAL)
                            replpar =
                                newt.equals(AttributeType.ENUM_DEFINITION)
                                    ? AttributeAvailability.LOCAL
                                    : AttributeAvailability.SUBTREE;
                          //if(newt.equals(AttributeType.ENUM)&&!k.startsWith("*")&&getIndex(k)==-1)
                          //k = "*"+k;
                          String defName = typeCombo.getItems()[ind];
                          if (defName.startsWith(Attribute.ENUM_CHAR))
                            defName = defName.substring(Attribute.ENUM_CHAR.length());
                          Object val = a.getValue();
                          try {
                            AttributeType at2 = a.getType();
                            if(a.getType().equals(AttributeType.ENUM)){
                              AppliedEnumValue aev = a.getGValue();
                              if(!aev.getBaseKey().equals(defName)){
                                at2 = aev.getType();
                                val = aev.getValue();
                              }
                            }
                            val =
                                Attribute.convertValue(
                                    node, at2, newt, val, defName);
                          } catch (Exception e) {
                            System.err.println(e.getMessage());
                          }
                          //AttributeType.getDefaultValue(newt);
                          if (val == null) val = AttributeType.getDefaultValue(newt);
  
                          Attribute a2 = getEnumDefStorage().attrs().getAttribute(defName);
                          EnumDefinitionValue bval = null;
                          List<EnumValue> v = null;
                         // AttributeType et = a.getType();
                          if (a2 != null && (val == null || !(val instanceof AppliedEnumValue))) {
                            bval = a2.getGValue();
                            v = bval.getValues();
                          //Map<Object,String> v = null;
                            if (newt == AttributeType.ENUM) {
                              String comm = null;
                              Object vl = "";
                              if (v != null && v.size() > 0) {
                                EnumValue ev = v.iterator().next();
                                comm = ev.getComment();
                                vl = ev.getValue();
                              }
                              
                              val = new AppliedEnumValue(vl, comm, a2.getListType(), defName);
                            }
                          }
                          
                          AttributeDefinition ad = new AttributeDefinition(node.getUUId(),
                              k,replpar!=null?replpar:a.getAvailability(),AttributeDefinition.detectValueProvider(k, newt, val, TempFolderFilesProvider.getNewNotInited(newt)),
                                  AttributeDefinition.detectFlags(k));
                          //Attribute b = node.attrs().createAttribute(newt, k, val);
                          //if (replpar == null) replpar = a.getAvailability();
                          //b.setAvailability(replpar);
                          Attribute olda = node.attrs().setAttribute(ad);
                          if(olda!=null)
                            old = olda.getDefinition();
                          changed = true;
                        }
                      }
                      if (name.equals(VALUE)) {
                        changed = cneditordirty;
                        if (a.getType().equals(AttributeType.ENUM) && (value instanceof Integer)) {
                          AppliedEnumValue aev = a.getGValue();
                          Integer sel = (Integer) value;
                          if (sel == -1) changed = false;
                          else {
                            if (aev != null) {
                              Attribute b = getEnumDefStorage().attrs().getAttribute(aev.getBaseKey());
                              if (b != null && b.getType() == AttributeType.ENUM_DEFINITION) {
                                EnumDefinitionValue edv = b.getGValue();
                                if (edv != null) {
                                  if (edv.getValues().isEmpty()) changed = false;
                                  else {
                                    EnumValue ev = edv.getValues().get(sel);
                                    value =
                                        new AppliedEnumValue(
                                            ev.getValue(),
                                            ev.getComment(),
                                            aev.getType(),
                                            aev.getBaseKey());
                                  }
                                } else changed = false;
                              } else changed = false;
                            } else {
                              changed = false;
                            }
                          }
                        }
                        if (a.getType() == AttributeType.ENUM_DEFINITION
                            && (value instanceof EnumDefValueResult)) {
                          EnumDefValueResult res = (EnumDefValueResult) value;
                          EnumDefUsageRes useres = getEnumDefUsages(node, a.getKey());
                          value = new EnumDefinitionValue(res.type, res.values);
                          for (String s : useres.map.keySet()) {
                            if (res.renames.containsKey(s)) {
                              for (TreeNode nodea : useres.map.get(s).keySet())
                                if (nodea == null || nodea.isDisposed()) continue;
                                else
                                  for (String nm : useres.map.get(s).get(nodea)) {
                                    Attribute atr = nodea.attrs().getAttribute(nm);
                                    if (atr == null) //not from here
                                    continue;
                                    AppliedEnumValue aev = (AppliedEnumValue) atr.getValue();
                                    String comm = aev.getComment();
                                    for (EnumValue ev : res.values)
                                      if (ev.getValue().toString().equals(res.renames.get(s))) {
                                        comm = ev.getComment();
                                        break;
                                      }
                                    aev.updateCommentValue(res.renames.get(s), comm);
                                    //Object[] rw = (Object[]) atr.getRawValue();
                                    //rw[2] = res.renaes.get(s);
                                    AttributeAvailability pa = atr.getAvailability();
                                    AttributeDefinition ad = new AttributeDefinition(atr.getOrigin(), 
                                        atr.getKey(),pa,atr.getDefinition().getValueProvider(),atr.getDefinition().getFlags());
                                    node.attrs().setAttribute(ad);
                                    int sela = old!=null?find(provider.attributes,order,old):sel;
                                    provider.attributes[sela] = ad.toAttribute(nodea);
                                    d.syncExec(new Runnable(){
                                      
                                      @Override
                                      public void run() {
                                        if(attribTable.getControl().isDisposed())
                                          return;
                                        attribTable.setInput(provider.attributes);
                                        attribTable.refresh(true);
                                      }
                                    });
                                    nodea.commitAttributes();
                                  }
                            }
                          }
                          for (String s : res.remact.keySet()) {
                            EnumChangeAction act = res.remact.get(s);
                            for (TreeNode nodea : useres.map.get(s).keySet())
                              if (nodea == null || nodea.isDisposed()) continue;
                              else
                                for (String nm : useres.map.get(s).get(nodea)) {
                                  Attribute atr = nodea.attrs().removeAttribute(nm);
                                  if (atr == null) //not from here
                                    continue;
                                  AppliedEnumValue aev = (AppliedEnumValue) atr.getValue();
                                  //Object[]val = (Object[]) atr.getRawValue();
                                  if (act == EnumChangeAction.RETYPE) {
                                    AttributeDefinition ad = new AttributeDefinition(atr.getOrigin(),atr.getKey(), 
                                        atr.getAvailability(),new ConstValueProvider(aev.getType(), aev.getValue()),atr.getDefinition().getFlags());
                                    nodea.attrs().setAttribute(ad);
                                  }
                                  nodea.commitAttributes();
                                }
                          }
                        }
                        if (at != AttributeType.ENUM_DEFINITION
                            && at != AttributeType.ENUM
                            && !Attribute.checkValueByType(at, value)) changed = false;
                        if (changed) {
                          boolean isList = at == AttributeType.LIST,
                              isRef = at == AttributeType.REFERENCE;
                          if(isRef || isList){
                            ListValue lv = null;
                            if(isList)
                              lv = (ListValue)value;
                            if(isRef){
                              value = ((ReferenceValue)value).getRaw();
                            }else
                             if(lv!=null && lv.getType() == AttributeType.REFERENCE){
                              ArrayList<Object> nw = new ArrayList<>();
                              for(Object o : lv.getValues())
                                nw.add(((ReferenceValue)o).getRaw());
                              value = new ListValue(AttributeType.REFERENCE, nw);
                             }
                          }
                          AttributeDefinition ad = new AttributeDefinition(node.getUUId(), a.getKey(), 
                                replpar!=null?replpar:a.getAvailability(), 
                                    AttributeDefinition.detectValueProvider(a.getKey(), at, value,
                                      TempFolderFilesProvider.getNewNotInited(at)),
                                    AttributeDefinition.detectFlags(a.getKey()));
                          Attribute olda = node.attrs().setAttribute(ad);
                          if(olda!=null)
                            old = olda.getDefinition();
                        }
                      }
                      if (name.equals(SCOPE))
                        if ((Integer) value != a.getAvailability().ordinal()) {
                          AttributeDefinition ad = new AttributeDefinition(node.getUUId(), 
                              a.getKey(), AttributeAvailability.values()[(Integer) value],
                              a.getDefinition().getValueProvider(),
                              a.getDefinition().getFlags());
                          Attribute olda = node.attrs().setAttribute(ad);
                          if(olda!=null)
                            old = olda.getDefinition();
                          changed = true;
                        }
  
                      if (name.equals(GENERATOR))
                        if (a.getDefinition().isGenerated()) {
                          AttributeDefinition ad = new 
                              AttributeDefinition(node.getUUId(), 
                                  a.getKey(), replpar!=null?replpar:a.getAvailability(), new GeneratorValueProvider((AttributeGenerator) value),
                                      a.getDefinition().getFlags());//TODO flags?
  //                        Attribute b =
  //                            node.attrs()
  //                                .createAttribute(
  //                                    at,
  //                                    a.getKey(),
  //                                    a.getNewValue(node.attrs()),
  //                                    a.getRawValue(),
  //                                    (AttributeGenerator) value);
  //                        if (replpar == null) replpar = a.getAvailability();
  //                        b.setAvailability(replpar);
                          Attribute olda = node.attrs().setAttribute(ad);
                          if(olda!=null)
                            old = olda.getDefinition();
                          changed = true;
                        }
  
                      AttributeDefinition now = node.attrs().getWorkAttributeDefinition(key);
                      if (changed
                          && (old == null && now != null
                              || now == null && old != null
                              || now != null && (!now.equals(old))
                              || now != null && now.isGenerated())) {
                        dirty = true;
                        if (!a.getKey().equals("")) {
                          order.put(key, order.get(a.getKey()));
                          //order.remove(a.getKey());
                        } else order.put(key, order.size());
                        if (old != null
                            && !old.getKey().equals("")
                            && old.getType() == AttributeType.ENUM_DEFINITION
                            && !name.equals(VALUE)) {
                          long cnt = getEnumDefUsages(node, old.getKey()).summ;
                          if (cnt != 0) {
                            boolean changedType =
                                now!=null && now.getType() != AttributeType.ENUM_DEFINITION;
                            String msg = changedType ? MES_ENUM_DEF_DEL : MES_ENUM_DEF_ID;
                            int dres =
                                new EnumRetypeDeleteDialog(
                                        attribTable.getTable().getShell(),
                                        msg.replace("{cnt}", Long.toString(cnt)),
                                        !changedType)
                                    .open();
                            //for definitions we need to start trasaction earlier
                            started = transactionIgnore?started:started?started:node.getTreeDB().startTransaction("Update "+node.getTreeDB().getProjectName()+":" + node.getType(), true);
                            if (!applyEnumDefDialogResult(old.getKey(), dres, key)) {
                              node.commitAttributes();
                              if (started) {
                                changed = false;
                                node.getTreeDB().rollback();
                                started = false;
                              }
                              return Status.OK_STATUS;
                            }
                          }
                        }
                        if (!changed)
                          if (old == null) node.attrs().removeAttribute(a.getKey());
                          else node.attrs().setAttribute(old);
                        int sela = old!=null?find(provider.attributes,order,old):sel;
                        if(now!=null){
                          AttributeDefinition ad = now;
                          Attribute newa = null;
                          TypedValue raw = ad.getRawValue();
                          if(raw!=null)
                          {
                          newa = getRawValue(node, ad,key,raw);
                          }else
                            newa = new AttributeDefinition(ad.getOrigin(),key,null,
                                new ConstValueProvider(AttributeType.STRING, "loading"),
                                AttributeDefinition.detectFlags(key)).toAttribute(node);
                          Attribute fnewa = newa;
                          d.asyncExec(new Runnable(){
                            @Override
                            public void run() {
                              if(attribTable.getControl().isDisposed())
                                return;
                              //attribTable.setInput(provider.attributes);
                              attribTable.replace(fnewa, sela);
                            }
                          });
                        }
                        started = transactionIgnore?started:started?started:node.getTreeDB().startTransaction("Update "+node.getTreeDB().getProjectName()+":" + node.getType(), true);
                        node.commitAttributes();
                      } else if (started){
                        started = false;
                        changed = false;
                        node.getTreeDB().rollback();
                      }
                    }
                    if(changed)
                      EclipseAPI.getDisplay().asyncExec(new Runnable(){
                        @Override
                        public void run() {
                          if(attribTable.getControl().isDisposed())
                            return;
                          refreshTable(attribTable, null);
                          attribTable.getControl().setFocus();
                          panel.notifyListeners(SWT.Modify, new Event());
                        }
                      });
                  }finally{
                    if (started) {
                      if (changed)
                        node.getTreeDB().commit();
                      else node.getTreeDB().rollback();
                    }
                  }
                  return Status.OK_STATUS;
                }

                private Attribute getRawValue(TreeNode node,AttributeDefinition ad, String key, TypedValue raw) {
                  ConstValueProvider cv = null;
                  if(raw.getType() == AttributeType.LIST && raw.getValue()!=null && ((ListValue)raw.getValue()).getType()
                      == AttributeType.REFERENCE)
                  {
                    ListValue lv = (ListValue) raw.getValue();
                    List<Object> nw = new LinkedList<>();
                    for(Object o:lv)
                      if(!(o instanceof ReferenceValue))
                        nw.add(o);
                    if(!nw.isEmpty())
                      cv = new ConstValueProvider(raw.getType(), new ListValue(AttributeType.STRING, nw));
                  }
                  if(raw.getType() == AttributeType.REFERENCE){
                    Object val = raw.getValue();
                    if(val instanceof ReferenceValue)
                      cv = new ConstValueProvider(raw.getType(), raw.getValue());
                    else
                      cv = new ConstValueProvider(AttributeType.STRING, raw.getValue());
                  }
                  if(cv == null)
                    cv = new ConstValueProvider(raw.getType(), raw.getValue());
                  return new AttributeDefinition(ad.getOrigin(),key,null,
                            cv,
                            AttributeDefinition.detectFlags(key)).toAttribute(node);
                }

                private int find(
                    Object[] attributes, Map<String, Integer> order, AttributeDefinition old) {
                  Object[] ao = Arrays.copyOf(attributes, attributes.length);
                  Arrays.sort(ao,new Comparator<Object>(){
                    @Override
                    public int compare(Object o1, Object o2) {
                      Integer i1 = order.getOrDefault(((Attribute)o1).getKey(),0);
                      Integer i2 = order.getOrDefault(((Attribute)o2).getKey(),0);
                      return Integer.compare(i1 == null?0:i1,
                        i2==null?0:i2);
                    }
                    
                  });
                  for(int i = 0;i<ao.length;i++){
                    Attribute a = (Attribute) ao[i];
                    if(a.getDefinition()==old)
                      return i;
                  }
                  return -1;
                }
              };
              wj.schedule();
              
            }

            @Override
            protected Object getValue(Object element) {
              String title = column.getColumn().getText();
              if (element == null) return "";
              Attribute a = (Attribute) element;
              if (a.getKey().equals("")) return "";
              if (title.equals(NAME)) {
                return a.getKey();
              }
              if (title.equals(TYPE)) {
                String name = a.getType().name();
                if (a.getType() == AttributeType.ENUM) {
                  AppliedEnumValue aev = (AppliedEnumValue) a.getValue();
                  if (aev != null) name = aev.getBaseKey();
                }
                if(readOnly)
                  return name;
                for (int i = 0; i < typeCombo.getItems().length; i++)
                  if (typeCombo.getItems()[i].equals(Attribute.ENUM_CHAR + name)
                      || typeCombo.getItems()[i].equals(name)) return i;
                return a.getType().ordinal();
              }
              if (title.equals(VALUE)) {
                if (a.getType().equals(AttributeType.ENUM)) {
                  String[] vals = enumsel.getItems();
                  AppliedEnumValue aev = a.getGValue();
                  Object val = aev.getValue();
                  if(readOnly)
                    return val.toString();
                  for (int i = 0; i < vals.length; i++) {
                    if (vals[i].equals(val.toString())) return i;
                  }
                  //String[]rvals = Arrays.copyOf(vals, vals.length+1);
                  //rvals[rvals.length-1]="*"+val;
                  setEnumSelItems(vals);
                  return 0;
                }
                if (a.getType().equals(AttributeType.BOOL))
                  return a.getValue().toString().toLowerCase();
                if (a.getType() == AttributeType.LIST
                    || a.getType() == AttributeType.ENUM_DEFINITION)
                  return Attribute.valueToString(node.attrs(),a.getType(), a.getValue());
                Object o = a.getValue();
                if(a.getDefinition().getValueProvider() instanceof TemplValueProvider)
                  o = a.getDefinition().getRawValue().getGValue();
                if (o != null) return Attribute.valueToString(node.attrs(),a.getType(), o);
                return "null";
              }
              if (title.equals(SCOPE)){
                AttributeAvailability aa = a.getAvailability();
                if(aa == null)
                  return LOADING_S;
                else
                  return aa.ordinal();
              }
              return "";
            }

            @Override
            protected CellEditor getCellEditor(Object element) {
              String title = column.getColumn().getText();
              Attribute a = (Attribute) element;
              if (title.equals(TYPE)) {
                if (a.getType() == AttributeType.ENUM_DEFINITION) {
                  if(!readOnly)
                    updateTypeCombo(a.getKey());
                  else
                    return new TextCellEditor(attribTable.getTable(), readOnly?SWT.READ_ONLY:SWT.FULL_SELECTION);
                }
              }
              if (title.equals(VALUE)) {
                if (a.getType().equals(AttributeType.ENUM)) {
                  if(readOnly)
                    return new TextCellEditor(attribTable.getTable(), readOnly?SWT.READ_ONLY:SWT.FULL_SELECTION);
                  AppliedEnumValue aev = a.getGValue();
                  if (aev != null) {
                    Attribute b = getEnumDefStorage().attrs().getAttribute(aev.getBaseKey());
                    if (b != null && b.getType() == AttributeType.ENUM_DEFINITION) {
                      EnumDefinitionValue vals = b.getGValue();
                      String[] vret = new String[vals.getValues().size()];
                      int i = 0;
                      @SuppressWarnings("unused")
                      boolean find = false;
                      for (EnumValue ev : vals.getValues()) {
                        vret[i++] = ev.getValue().toString();
                        if (ev.equals(aev.getValue())) find = true;
                      }
                      setEnumSelItems(vret);
                      return enumsel;
                      //  enumsel.setValue(getIndex(a.getKey()));
                    }
                    setEnumSelItems(new String[] {}); //+setts[2].toString()});
                    return enumsel;
                  }
                }
                CellEditor ce = valueEditors.get(a.getType());
                return ce;
              }
              if(readOnly && a.getType() == AttributeType.ENUM)
                return new TextCellEditor(attribTable.getTable(), readOnly?SWT.READ_ONLY:SWT.FULL_SELECTION);
              return editors.get(title);
            }

            @Override
            protected boolean canEdit(Object element) {
              TreeDB db = node.getTreeDB();
              TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
              if(!transactionIgnore&&!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
                return false;
              String title = column.getColumn().getText();
              //if(readOnly)
                //return false;
              if (!enabled) return false;
              Attribute a = (Attribute) element;
              boolean ro = a.isReadOnly(node.getTreeDB());
              return !ro && !a.getKey().equals("") && (title.equals(NAME)||title.equals(TYPE)||title.equals(VALUE)
                  ||title.equals(GENERATOR)||title.equals(SCOPE))
                  || !ro &&title.equals(NAME); //||iv==2&&((Attribute)element).getRawType().equals(AttributeType.STRING);
            }
          });
      i++;
    }
    TableViewerColumn cgen = new TableViewerColumn(attribTable, readOnly?SWT.READ_ONLY:SWT.FULL_SELECTION);
    cgen.getColumn().setWidth(100);
    cgen.getColumn().setText(GENERATOR);
    cgen.getColumn()
        .addListener(
            SWT.Selection,
            new Listener() {
              @Override
              public void handleEvent(Event event) {
                updateSorting(sortingMethod.equals(GENERATOR) ? GENERATOR+"|" : GENERATOR, true);
                refreshTable(attribTable, null);
              }
            });
    cgen.setEditingSupport(
        new EditingSupport(attribTable) {
          @Override
          protected void setValue(Object element, Object value) {
            if(readOnly)
              return;
              
            CellEditor editor = getCellEditor(element);
            if(!editor.isDirty())
              return;
            final TreeNode node = AttributesPanel.this.node;
            final Composite panel = AttributesPanel.this.panel;
            final boolean transactionIgnore = AttributesPanel.this.transactionIgnore;
            final TableViewer attribTable = AttributesPanel.this.attribTable;
            if (value == null || !editor.isDirty()) return;
            WorkspaceJob j = new WorkspaceJob("Update attribute for "+node.getTreeDB().getProjectName()+":"+node.getUserVisibleName()){
              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {

                boolean cont = transactionIgnore; 
                boolean started = !cont && node.getTreeDB().startTransaction("Update "+node.getTreeDB().getProjectName()+":" + node.getType(), true);
                cont = started || cont;
                if (cont) {
                  try {
                    Attribute a = (Attribute) element;
                    TreeNode master =
                        node.getTreeDB().getNode(a.getOrigin());
                    //Object v = ((AttributeGenerator) value).getValue(master.attrs(),AttributeStorage.formPredDeps(((AttributeGenerator) value).getFormula(), master.attrs()));
                    AttributeAvailability pa = a.getAvailability();
                    AttributeDefinition ad = new AttributeDefinition(
                        node.getUUId(), a.getKey(), pa,
                        new GeneratorValueProvider(((AttributeGenerator) value).copy()), a.getDefinition().getFlags());
  //                  a =
  //                      node.attrs()
  //                          .createAttribute( //Fix?
  //                              ((AttributeGenerator) value).getAttributeType(),
  //                              a.getKey(),
  //                              v,
  //                              v,
  //                              ((AttributeGenerator) value).copy());
  //                  a.setAvailability(pa);
                    node.attrs().setAttribute(ad);
                    started = transactionIgnore?started:started?started:node.getTreeDB().startTransaction("Update "+node.getTreeDB().getProjectName()+":" + node.getType(), true);
                    node.commitAttributes();
                    if (started) {
                      node.getTreeDB().commit();
                      started = false;
                    }
                    EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    
                      @Override
                      public void run() {
                        if(getControl().isDisposed())
                          return;
                        refreshTable(attribTable, null);
                        attribTable.getControl().setFocus();
                        panel.notifyListeners(SWT.Modify, new Event());
                      }
                    });

                  }finally {
                    if(started) {
                      node.getTreeDB().rollback();
                    }
                  }
                }else
                  EclipseAPI.getDisplay().asyncExec(new Runnable(){
                  
                    @Override
                    public void run() {
                      if(getControl().isDisposed())
                        return;
                      CellEditor ce = getCellEditor(element);
                      if(ce!=null)
                        ce.deactivate(); 
                    }
                  });
                return Status.OK_STATUS;
              }              
            };
            j.schedule();
          }

          @Override
          protected Object getValue(Object element) {
            Attribute a = (Attribute) element;
            AttributeGenerator ag = a.getDefinition().getGenerator();
            if (ag != null && a.getValue() instanceof ListValue)
              ag.setRandValues((ListValue) a.getValue());
            return ag;
          }

          @Override
          protected CellEditor getCellEditor(Object element) {
            return generatorEditor;
          }

          @Override
          protected boolean canEdit(Object element) {
            if (!enabled) return false;
            TreeDB db = node.getTreeDB();
            TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
            if(!transactionIgnore && !tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
              return false;
            //if(readOnly)
              //return false;
            Attribute a= (Attribute) element;
            if(a.isReadOnly(node.getTreeDB())) return false;
            if (a.getKey().equals("")) return false;
            if (a.getType() == AttributeType.ENUM_DEFINITION) return false;
            //attribTable.remove(element);
            //removeNode(((Attribute)element).getKey(),true);
            return true;
          }
        });
    TableViewerColumn cdel = new TableViewerColumn(attribTable, readOnly?SWT.READ_ONLY:SWT.FULL_SELECTION);
    TableColumn delColumn = cdel.getColumn();
    delColumn.setResizable(false);
    delColumn.setWidth(25);
    attribTable.getTable().addMouseListener(new MouseListener() {
      @Override
      public void mouseUp(MouseEvent e) {
      }
      @Override
      public void mouseDown(MouseEvent e) {
        if(readOnly)
          return;
        Point point = new Point(e.x,e.y);
        ViewerCell cell = attribTable.getCell(point);
        if(cell != null){
          if (cell.getColumnIndex() == attribTable.getTable().indexOf(linkColumn)){
            TreeDB db = node.getTreeDB();
            TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
            if(!transactionIgnore && !tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
              return;
            if (!enabled) return;
            Attribute a = (Attribute) cell.getElement();
            if (a.getKey().equals("")) return;
            UUID uuid = a.getOrigin();
            if (uuid == null || uuid == node.getUUId()) return;
            TreeNode nde = node.getTreeDB().getNode(uuid);
            //if(nde.getCloneParent()==null)
            //nde = VirtualNode.getRootTree(nde.getTreeDB()).getNode(nde.getUUId());
            if(fsid!=null) {
              WorkspaceJob j =
                new WorkspaceJob("Go to origin node for attribute "+a.getKey()) {
                  @Override
                  public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
                    SelectionManager.getOne().updateSelection(SourcePriority.PROPERTIES, fsid, nde);
                    //SelectionManager.getOne().changeActiveSource(fsid.getEventSource(),fsid, true);
                    return Status.OK_STATUS;
                  }
                };
              j.schedule();
            }
          }
          if (cell.getColumnIndex() == attribTable.getTable().indexOf(delColumn)) {
            Attribute a = (Attribute) cell.getElement();
            if(a == null)
              return;
            TreeDB db = node.getTreeDB();
            TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
             if(!transactionIgnore && !tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
               return;
              if (!enabled) return;
              if(a.isReadOnly(node.getTreeDB())) return;
              if (a.getKey().equals("") || !enabled) return;
              //if(transactionIgnore || node.getTreeDB().startTransaction("Update "+node.getType(),true)){
              if (removeAttribute(
                 attribTable.getTable().getShell(),
                 new String[] {a.getKey()},
                 false)) {
               //  if(!transactionIgnore)
               //  node.getTreeDB().rollback();
               //}else{
               attribTable.remove(a);
               dirty = true;
            }
          }
        }
      }
      @Override
      public void mouseDoubleClick(MouseEvent e) {
      }
    });
    TableViewerColumn empty = new TableViewerColumn(attribTable, SWT.FULL_SELECTION);
    empty.getColumn().setWidth(10);

    CellEditor[] eds = new CellEditor[columnNames.length - (mode == APanelWorkMode.GLOBAL ? 1 : 0)];
    int j = 0;
    for(int im = 0;im<columnNames.length;im++)
    {
      CellEditor ed = editors.get(columnNames[im]);
      if(ed!=null)
        eds[j++] = ed;
    }
    
    attribTable.setColumnProperties(columnNames);
    attribTable.getTable().setHeaderVisible(true);
    attribTable.getTable().setLinesVisible(true);
    attribTable.setCellEditors(eds);

    attribTable.setLabelProvider(new TableLabelProvider(this));
    provider = new AttributeTableContentProvider();
    attribTable.setContentProvider(provider);
    /*     attribTable.addDoubleClickListener(new IDoubleClickListener() {
        @Override
        public void doubleClick(DoubleClickEvent event) {
            Attribute attribute = (Attribute) ((IStructuredSelection) event
                    .getSelection()).getFirstElement();
            StructuredSelection s = (StructuredSelection) event.getSelection();
            if(attribute.isGenerated())
              return;
            String savedOldName = (String) attribute.getKey();
            EditAttrDialog addAttrDialog = new EditAttrDialog(getControl()
                    .getShell(), attribute.getType() , (String) attribute.getKey(),
                    attribute.getValue(), makeAttrMap());
            if (addAttrDialog.open() == EditAttrDialog.OK) {
                node.getTreeDB().startTransaction("Update "+node.getType());
                if (!savedOldName.equals(addAttrDialog.getKey())) {
                    node.removeAttribute(savedOldName);
                }
                node.putAttribute(new Attribute(node, attribute.getType(),
                        addAttrDialog.getKey(), addAttrDialog.getValue()));
                node.saveAttributes();
                node.getTreeDB().commit();
                attribTable.refresh();
                attribTable.getControl().setFocus();
                panel.notifyListeners(SWT.Modify, new Event());
                attribTable.refresh();
            }
        }
    });*/
    getControl().addDisposeListener(new DisposeListener() {
      
      @Override
      public void widgetDisposed(DisposeEvent e) {
        if(im!=null)
          im.dispose();
      }
    });
  }

  @SuppressWarnings("unused")
  private CellEditor getEditor(int ind, AttributeType at) {
    switch (ind) {
      case 0:
      case 1:
        return attribTable.getCellEditors()[ind];
      case 2:
        return valueEditors.get(at);
      case 3:
        return mode == APanelWorkMode.GLOBAL ? generatorEditor : scopeEditor;
      case 4:
        return generatorEditor;
    }
    return null;
  }

  private int getEditorMax() {
    return mode == APanelWorkMode.GLOBAL ? 4 : 5;
  }

  private int getEditorIndex(CellEditor tr) {
    for (int i = 0; i < attribTable.getCellEditors().length; i++) {
      CellEditor ce = attribTable.getCellEditors()[i];
      if (ce != null && tr.equals(ce)) return i;
    }
    for (CellEditor ce : valueEditors.values()) if (ce != null && ce.equals(tr)) return 2;
    if (tr.equals(enumsel)) return 2;
    if (tr.equals(scopeEditor)) return 3;
    if (tr.equals(generatorEditor)) return 4;
    return -1;
  }

  protected CellEditor[] getEditors() {
    Set<CellEditor> editors = new HashSet<>();
    editors.addAll(Arrays.asList(attribTable.getCellEditors()));
    for (CellEditor ce : valueEditors.values()) editors.add(ce);
    editors.add(generatorEditor);
    editors.add(enumsel);
    editors.add(scopeEditor);
    return editors.toArray(new CellEditor[0]);
  }

  protected void changeEditor(int shift) {
    final TableViewer attribTable = AttributesPanel.this.attribTable;
    final StructuredSelection ss = (StructuredSelection) attribTable.getSelection();
    if (ss != null && !ss.isEmpty()) {
      final Attribute a = (Attribute) ss.getFirstElement();
      int[] ai = getActiveEditor();
      int j = ai[1];
      j = (j + shift + getEditorMax()) % getEditorMax();
      if (j == 3
          && mode == APanelWorkMode.GLOBAL
          && a.getType() == AttributeType.ENUM_DEFINITION)
        j = (j + shift + getEditorMax()) % getEditorMax();
      if (j < 0) j = 0;
      final int jsel = j + 1;
      final int sel = attribTable.getTable().getSelectionIndex();
      Display.getCurrent()
          .asyncExec(
              new Runnable() {
                @Override
                public void run() {
                  if(attribTable.getControl().isDisposed())
                    return;
                  attribTable.editElement(attribTable.getElementAt(sel), -1);
                  //attribTable.refresh(attribTable.getElementAt(sel));
                  attribTable.editElement(attribTable.getElementAt(sel), jsel);
                  //attribTable.editElement(a, jsel);
                  //attribTable.refresh(attribTable.getElementAt(sel));
                  //attribTable.editElement(attribTable.getElementAt(sel), jsel);
                  //eds[jsel].activate();
                  //eds[jsel].setFocus();
                  //refreshTable();
                }
              });
    }
  }

  protected int getIndex(String key) {
    Iterator<String> it = names.iterator();
    int i = 0;
    while (it.hasNext()) {
      String s = it.next();
      if (s.equals(key)) return i;
      i++;
    }
    if (i > names.size()) return -1;
    return i;
  }

  public Control getControl() {
    return panel;
  }

  private String sortingMethod = NAME;

  private void updateSorting(boolean force) {
    updateSorting(sortingMethod, force);
  }

  private Comparator<Attribute> attrSorter =
      new Comparator<Attribute>() {

        @Override
        public int compare(Attribute o1, Attribute o2) {
          String m = sortingMethod;
          boolean s2 = false;
          if(m.endsWith("|"))
          {
            m = m.substring(0,m.length()-1);
            s2 = true;
          }
          if (o1 == null) return -1;
          switch (m) {
            case NAME:
              return s2 ? o2.getKey().compareTo(o1.getKey()) : o1.getKey().compareTo(o2.getKey());
            case TYPE:
              return s2
                  ? o2.getType().compareTo(o1.getType())
                  : o1.getType().compareTo(o2.getType());
            case VALUE:
              return s2
                  ? Attribute.valueToString(node.attrs(),o2.getType(),o2.getValue())
                      .compareTo(Attribute.valueToString(node.attrs(),o1.getType(),o1.getValue()))
                  : Attribute.valueToString(node.attrs(),o1.getType(),o1.getValue())
                      .compareTo(Attribute.valueToString(node.attrs(),o2.getType(),o2.getValue()));
            case SCOPE:
              return s2
                  ? o2.getAvailability().compareTo(o1.getAvailability())
                  : o1.getAvailability().compareTo(o2.getAvailability());
            case GENERATOR:
              {
                AttributeGenerator a1 = o1.getDefinition().getGenerator();
                AttributeGenerator a2 = o2.getDefinition().getGenerator();
                if (a1 == null) return a2 == null ? 0 : s2 ? -1 : 1;
                else return a2 == null ? s2 ? 1 : 0 : a1.toString().compareTo(a2.toString());
              }
            default:
              break;
          }
          return 0;
        }
      };

  private void updateSorting(String sorting, boolean force) {
    if (!this.sortingMethod.equals(sorting) || force) order.clear();
    this.sortingMethod = sorting;
    if (this.node != null) {
      Attribute[] s = node.attrs().toArray();
      Arrays.sort(s, attrSorter);
      for (Attribute a : s) order.put(a.getKey(), order.size());
    }
  }
  public void setNode(TreeNode node, boolean duringDispose) {
    if (this.node != null) {
      this.node.removeGuiChangeListener(this);
    }
    boolean isDisposed = panel.isDisposed() || duringDispose;
    boolean b = node == null || !node.equals(this.node);
    this.node = node;
    if (b && !isDisposed) {
      dirty = false;
      order.clear();
      updateSorting(false);
    }
    if(node != null)
      node.addGuiChangeListener(this);
    if(valueEditors.containsKey(AttributeType.REFERENCE))
    {
      valueEditors.get(AttributeType.REFERENCE).dispose();
      valueEditors.get(AttributeType.LIST).dispose();
      valueEditors.get(AttributeType.ENUM_DEFINITION).dispose();
      generatorEditor.dispose();
    }
    //TODO temp;
    if(!isDisposed && node!=null) {
      valueEditors.put(
        AttributeType.REFERENCE,
        new ReferenceValueEditor(node, attribTable.getTable(), SWT.FULL_SELECTION));
      valueEditors.put(
        AttributeType.LIST,
        new ListValueEditor(node, attribTable.getTable(), SWT.FULL_SELECTION, true));
      valueEditors.put(
        AttributeType.ENUM_DEFINITION,
        new EnumDefinitionValueEditor(node, attribTable.getTable(), SWT.FULL_SELECTION));
      generatorEditor = new GeneratorValueEditor(node, attribTable.getTable(), SWT.FULL_SELECTION);
    }
    //valueEditors.put(AttributeType.GENERATED, );
    if(!isDisposed) {
      attribTable.setInput(node);
      updateTypeCombo(null);
    }
  }
  //    private void setEnabledRec(boolean b, Control c){

  private void updateTypeCombo(String ignore) {
    //TODO change to new ATTR method
    names = new HashSet<>();
    TreeNode es = getEnumDefStorage();
    if(es!=null)
      for (Attribute a : es.attrs()) {
        if (a.getType().equals(AttributeType.ENUM_DEFINITION)
            && ProjectSettingsMainSection.filter.filter(a)
            && Attribute.checkValueByType(AttributeType.ENUM_DEFINITION, a.getValue())
            && !((EnumDefinitionValue) a.getValue()).getValues().isEmpty())
          names.add(a.getKey());
      }
    String[] types = getAvailableTypes();
    String[] ret = new String[types.length + names.size()];
    int i = 0;
    for (String at : types) ret[i++] = at;

    for (String s : names)
      if (ignore == null || !ignore.equals(s)) ret[i++] = Attribute.ENUM_CHAR + s;
    i = ret.length - 1;
    while (ret[i] == null) i--;
    if(!panel.isDisposed()&&!typeCombo.getControl().isDisposed())
      typeCombo.setItems(Arrays.copyOf(ret, i + 1));
  }
  //      if(c instanceof Composite)
  //      for(Control cn:((Composite)c).getChildren())
  //      setEnabledRec(b, cn);;
  //  }
  public void refreshTable(TableViewer attribTable, Object object) {
    if (object != null) attribTable.refresh(object);
    else attribTable.refresh();
  }

  private int[] getActiveEditor() {
    final CellEditor[] eds = getEditors();
    for (int i = 0; i < eds.length; i++)
      if (eds[i] != null && eds[i].isActivated()) {
        return new int[] {i, getEditorIndex(eds[i])};
      }
    return new int[] {-1, -1};
  }

  public void setEnabled(TableViewer attribTable, boolean b) {
    //getControl().setEnabled(b);
    if (enabled != b) {
      enabled = b;
      refreshTable(attribTable, null);
    }
  }

  @Override
  public void attributeChange(final TreeNode source, final String what, Attribute old) {
    attributesChange(
        source,
        new ChangesSet<>(Collections.emptySet(),AttributeStorage.formSet(what),Collections.emptySet(),
            AttributeStorage.formAttributeMap(old),
            AttributeStorage.formAttributeMap(source.attrs().getAttribute(what)),AttributeStorage.formList(what))
    );
  }

  @Override
  public void attributesChange(final TreeNode source, ChangesSet<Attribute> swhat) {
    TableViewer attribTable = AttributesPanel.this.attribTable;
    if (attribTable.getControl().isDisposed()) source.removeChangeListener(AttributesPanel.this);
    else {
      boolean notsys = false;
      for(String s:swhat.getAll())
        if(!s.startsWith("_")||s.equals(TreeNode.ATTR_NAME)
                            ||s.equals(TreeNode.ATTR_TYPE)
                            ||s.equals(TreeNode.ATTR_ID)){
          notsys = true;
          break;
        }
      if(notsys)
        EclipseAPI.getDisplay()
          .asyncExec(
              new Runnable() {

                @Override
                public void run() {
                  if (attribTable.getControl().isDisposed()) return;
                  boolean f = false;//no updates
                  Set<String> toUp = new HashSet<>(swhat.getAll());
                  for (final TableItem ti : attribTable.getTable().getItems()) {
                    boolean of = false;
                    String targ = ((Attribute) ti.getData()).getKey();
                    //TODO tends to be moved
                    if (swhat.getAll().contains(targ)) {
                      of = true;//some update found
                      toUp.remove(targ);//mark as found
                    }
                    if(of)
                      f = true;
                  }
                  if(!f){
                    for(String s:toUp)//test more updated but not in panel attrs
                      if(!s.startsWith("_") || s.equals(TreeNode.ATTR_NAME))
                        f = true;
                  }
                  if (f)//if updates found
                  {
                    if (attribTable.getControl().isDisposed()) {//and we are not disposed
                      source.removeChangeListener(AttributesPanel.this);
                      return;
                    }
                    refreshTable(attribTable,//refresh panel
                      null);
                  }
                }
              });
    }
    //TODO new attrs?
  }

  private boolean dirty = false;

  @Override
  public void deleted(final TreeNode source) {
    new UIJob("refresh attribute panel") {
      @Override
      public IStatus runInUIThread(IProgressMonitor monitor) {
        if (attribTable.getControl().isDisposed())
          source.removeChangeListener(AttributesPanel.this);
        return Status.OK_STATUS;
      }
    }.schedule();
  }

  @Override
  public void moved(final TreeNode source, TreeNode oldParent) {
    new UIJob("refresh attribute panel") {
      @Override
      public IStatus runInUIThread(IProgressMonitor monitor) {
        if (attribTable.getControl().isDisposed())
          source.removeChangeListener(AttributesPanel.this);
        return Status.OK_STATUS;
      }
    }.schedule();
  }

  public void setTransactionIgnore(boolean ti) {
    transactionIgnore = ti;
  }

  public boolean isDirty() {
    return dirty;
  }

  @Override
  public void fireNodeResourceChange(TreeNode treeNode, String resName, Moment m) {
    // TODO Auto-generated method stub

  }

  public void setAttributeMarkColor(TableViewer attribTable,String key, Color color) {
    attributeMarkColors.put(key, color);
    refreshTable(attribTable,null);
  }
  
  public void resetColors(TableViewer attribTable) {
    attributeMarkColors.clear();
    refreshTable(attribTable,null);
  }

  public TableViewer getViewer() {
    return attribTable;
  }
}
