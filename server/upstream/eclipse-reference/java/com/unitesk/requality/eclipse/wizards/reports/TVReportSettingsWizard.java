package com.unitesk.requality.eclipse.wizards.reports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ICellEditorValidator;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.SystemNames;
import com.unitesk.requality.core.fid.FIDUpdater;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.ui.ImageContainer;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.DeepFirstTreeWalker;
public class TVReportSettingsWizard extends Dialog {
  private static int FIRST_W=150, SECOND_W=70, THIRD_W = 100;
  public static Double max_ind = 1d;
  Composite p;
  Text size;
  boolean changed = false;
  String def = "";
  List avail;
  TableViewer selected; 
  Set<String> sel = new HashSet<>();
  Map<String, VarProc> setts = new HashMap<>();
  ImageContainer im = new ImageContainer();
  Button add, remove;
  //Label cname;
  @Override
  protected Control createDialogArea(Composite parent) {
    getShell().setText("Настройки отчета о чтении требований");
    parent.setLayout(new GridLayout(1, false));
    parent.setLayoutData(new GridData(GridData.FILL_BOTH));
    p = new Composite(parent, SWT.NONE);
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.widthHint = 500;
    gd.heightHint = 600;
    p.setLayoutData(gd);
    p.setLayout(new GridLayout(3, false));
    Button b = new Button (p, SWT.NONE);
    b.setText("Load attributes names");
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 3;
    b.setLayoutData(gd);
    b.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
	    if(b.isDisposed())
          return;
        Set<String> av = new HashSet<>(Arrays.asList(avail.getItems()));
        final ReportSettings rep = TVReportSettingsWizard.this.rep;
        final Set<String> sel = TVReportSettingsWizard.this.sel;
        final List avail = TVReportSettingsWizard.this.avail;
        WorkspaceJob j = new WorkspaceJob("Fill attribute names"){

          @Override
          public IStatus runInWorkspace(IProgressMonitor monitor) {
            if(rep == null)
              return Status.CANCEL_STATUS;
            DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(rep.getTreeDB().getNode(Requirement.getTypeRootQId()));
            Iterator<TreeNode> it = dftw.iterator();
            LinkedList<String> avala = new LinkedList<>();
            while(it.hasNext()){
              TreeNode node = it.next();
              Map<String, Attribute> m = node.attrs().getAvailableAttributes();
              for(Entry<String, Attribute> en:m.entrySet()){
                if(en.getValue()!=null && en.getValue().isUserVisible() && isShown(node.getTreeDB(), en.getKey(), en.getValue()))
                {
                  String k = rep.getRevservnames().get(en.getKey());
                  if(k==null)
                   k = en.getKey();
                  if(!sel.contains(k)&&!av.contains(k)){
                    avala.add(k);
                    av.add(k);
                  }
                }
              }
            }
            EclipseAPI.getDisplay().asyncExec(new Runnable(){
              @Override
              public void run() {
                if(avail.isDisposed())
                  return;
                for(String s:avala)
                  avail.add(s);
              }
            });
            return Status.OK_STATUS;
          }

		private boolean isShown(TreeDB db, String key, Attribute value) {
            return !key.equals(FIDUpdater.getFIDMaxName(db));
		}
          
        };
        j.schedule();
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    
    Label l = new Label(p, SWT.NONE);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    l.setLayoutData(gd);
    l.setText("Availiable attributes:");
    l = new Label(p, SWT.NONE);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    l.setLayoutData(gd);
    l.setText("Selected attributes:\nYou can edit the width and specify the units\n of measurement. Examples: 200px, 10%. The\n default unit is \"px\".");
    avail = new List(p, SWT.H_SCROLL|SWT.V_SCROLL);
    avail.setLayoutData(new GridData(GridData.FILL_BOTH));
    
    Composite ca = new Composite(p, SWT.NONE);
    ca.setLayout(new GridLayout(1,true));
    ca.setLayoutData(new GridData(GridData.FILL_VERTICAL));
    
    remove = new Button(ca, SWT.NONE);
    remove.setText("←");
    add = new Button(ca, SWT.NONE);
    add.setText("→");
    
    add.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event event) {
	    if(add.isDisposed())
          return;
        int ind = avail.getSelectionIndex();
        if(ind>-1){
          String val = avail.getItem(ind);
          avail.remove(ind);
          sel.add(val);
          selected.setInput(toObj(sel));
          selected.refresh();
        }
      }
    });
    remove.addListener(SWT.Selection, new Listener(){
      @Override
      public void handleEvent(Event event) {
	    if(remove.isDisposed())
          return;
        StructuredSelection sa = (StructuredSelection) selected.getSelection();
        if(!sa.isEmpty()){
          Object sl = (sa).getFirstElement();
          VarProc vp = (VarProc) sl;
          String val = vp.var;
          sel.remove(val);
          selected.setInput(toObj(sel));
          selected.refresh();
          avail.add(val);
        }
      }
    });
    
    selected = new TableViewer(p, SWT.H_SCROLL|SWT.V_SCROLL | SWT.RESIZE);
    selected.getTable().setHeaderVisible(true);
    selected.getTable().setLayoutData(new GridData(GridData.FILL_BOTH));
    
    selected.getControl().getParent().addControlListener(new ControlAdapter() {
      public void controlResized(ControlEvent e) {
	    if(selected.getControl().isDisposed())
          return;
        Rectangle area = selected.getTable().getClientArea();
        Point preferredSize = selected.getTable().computeSize(SWT.DEFAULT, SWT.DEFAULT);
        int width = area.width - 2*selected.getTable().getBorderWidth();
        if (preferredSize.y > area.height + selected.getTable().getHeaderHeight()) {
          // Subtract the scrollbar width from the total column width
          // if a vertical scrollbar will be required
          Point vBarSize = selected.getTable().getVerticalBar().getSize();
          width -= vBarSize.x;
        }
        Point oldSize = selected.getTable().getSize();
        TableColumn tc = selected.getTable().getColumns()[0];
        TableColumn tc2 = selected.getTable().getColumns()[1];
        
        tc.setWidth((width-50)*FIRST_W/(SECOND_W+FIRST_W));
        tc2.setWidth((width-50)*SECOND_W/(SECOND_W+FIRST_W));
        selected.getTable().setSize(area.width, area.height);
      }
    });
    
    TableLayout tl = new TableLayout();
    Composite par = selected.getTable().getParent();
    //double cx = selected.getControl().getSize().x/par.getSize().x;
    selected.getTable().setLayout(tl);
    TableViewerColumn column =
        new TableViewerColumn(selected, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION |  SWT.RESIZE);
    column.getColumn().setText("Value");
    //column.getColumn().setWidth(150);
    column.getColumn().setResizable(true);
    column.getColumn().setMoveable(true);
    ColumnWeightData cwd = new ColumnWeightData(FIRST_W, true);
    column.getColumn().setData(cwd);
    tl.addColumnData(cwd);
    column =
        new TableViewerColumn(selected, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.RESIZE);
    
    column.getColumn().setText("Width");
    //column.getColumn().setWidth(70);
    column.getColumn().setResizable(true);
    column.getColumn().setMoveable(true);
    
    cwd = new ColumnWeightData(SECOND_W, true);
    column.getColumn().setData(cwd);
    tl.addColumnData(cwd);
    column.setEditingSupport(
        new EditingSupport(selected) {
          TextCellEditor tce;
          {
          tce = new TextCellEditor(selected.getTable());
            tce.setValidator(new ICellEditorValidator(){
            
              @Override
              public String isValid(Object value) {
        	    if(tce.getControl().isDisposed())
                  return "";
                String sv = (String)value;
                if(sv == null)
                  sv = "";
                sv = sv.trim();
                sv = sv.toLowerCase();
                if(sv.endsWith("px")||sv.endsWith("in")||sv.endsWith("pt"))
                  return !isDigit(sv.substring(0, sv.length()-2))?"incorrect value!":null;
                if(sv.endsWith("%"))
                  return !isDigit(sv.substring(0, sv.length()-1))?"incorrect value!":null;
                return !isDigit(sv)?"incorrect value!":null;
              }

              private boolean isDigit(String substring) {
                for(int i = 0;i<substring.length();i++)
                {
                  char c = substring.charAt(i);
                  if(!Character.isDigit(c))
                    return false;
                }
                return true;
              }
            });
          }
          @Override
          protected void setValue(Object element, Object value) {
            VarProc vp = (VarProc) element;
            if(tce.getValidator().isValid(value) != null){
              selected.refresh(vp);
              return;
            }
            String val = value.toString().toLowerCase().trim();
            if(val.length()>0)
              if(Character.isDigit(val.charAt(val.length()-1)))
                val = val+"px";
            vp.proc = val;
            setts.put(vp.var, vp);
            selected.refresh(vp);
          }

          @Override
          protected Object getValue(Object element) {
            VarProc vp = (VarProc) element;
            return vp.proc!=null?vp.proc:"";
          }

          @Override
          protected CellEditor getCellEditor(Object element) {
            
            return tce;
          }

          @Override
          protected boolean canEdit(Object element) {
            if (((VarProc) element).getId() == Double.MAX_VALUE) return false;
            return true;
          }
        });
    column =
        new TableViewerColumn(selected, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION |  SWT.RESIZE);
    column.getColumn().setText("Formula");
    //column.getColumn().setWidth(150);
    column.getColumn().setResizable(true);
    column.getColumn().setMoveable(true);
    cwd = new ColumnWeightData(THIRD_W, true);
    column.getColumn().setData(cwd);
    column.setEditingSupport(
        new EditingSupport(selected) {
          JSFormulaCellEditor tce;
          {
            tce = new JSFormulaCellEditor(selected.getTable());
          }
          @Override
          protected void setValue(Object element, Object value) {
    	    if(selected.getControl().isDisposed() || !tce.isDirty())
              return;
            VarProc vp = (VarProc) element;
            vp.form = value.toString();
            setts.put(vp.var, vp);
            selected.refresh(vp);
          }

          @Override
          protected Object getValue(Object element) {
            VarProc vp = (VarProc) element;
            return vp.form!=null?vp.form:"";
          }

          @Override
          protected CellEditor getCellEditor(Object element) {
            return tce;
          }

          @Override
          protected boolean canEdit(Object element) {
            if (((VarProc) element).getId() == Double.MAX_VALUE) return false;
            return true;
          }
        });
    tl.addColumnData(cwd);
    
    TableViewerColumn cup = new TableViewerColumn(selected, SWT.FULL_SELECTION);
    cup.getColumn().setResizable(false);
    //cup.getColumn().setWidth(25);
    cwd = new ColumnWeightData(25, false);
    column.getColumn().setData(cwd);
    tl.addColumnData(cwd);
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
            if (((VarProc) element).getId() == Double.MAX_VALUE) return false;
            moveUp(element);
            return false;
          }
        });

    TableViewerColumn cdown = new TableViewerColumn(selected, SWT.FULL_SELECTION);
    cdown.getColumn().setResizable(false);
    //cdown.getColumn().setWidth(25);
    cwd = new ColumnWeightData(25, false);
    column.getColumn().setData(cwd);
    tl.addColumnData(cwd);
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
    	    if(cdown.getViewer().getControl().isDisposed())
              return false;
            if (((VarProc) element).getId() == Double.MAX_VALUE) return false;
            moveDown(element);
            return false;
          }
        });
    selected.setContentProvider(new IStructuredContentProvider() {
      @Override
      public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

      @Override
      public void dispose() {}

      @Override
      public Object[] getElements(Object inputElement) {
        //List<?> l = (List<?>) inputElement;
        Object[] vals = (Object[])inputElement;
//        int i = 0;
//        for (Object o : l)
//          if (o instanceof ValueItem) vals[i++] = o;
//          else vals[i++] = new ValueItem(o, i);
//        vals[vals.length - 1] = new ValueItem(null, Double.MAX_VALUE);
        return vals;
      }
    });
    selected.setComparator(new ViewerComparator(){
      @Override
      public int compare(Viewer viewer, Object e1, Object e2) {
        return ((VarProc)e1).d.compareTo(((VarProc)e2).d);
      }
    });;
    selected.setLabelProvider( 
        new ITableLabelProvider(){
        
          @Override
          public void removeListener(ILabelProviderListener listener) {
            // TODO Auto-generated method stub
            
          }
        
          @Override
          public boolean isLabelProperty(Object element, String property) {
            return true;
          }
        
          @Override
          public void dispose() {
            // TODO Auto-generated method stub
            
          }
        
          @Override
          public void addListener(ILabelProviderListener listener) {
            // TODO Auto-generated method stub
            
          }
          @Override
          public String getColumnText(Object element, int columnIndex) {
            if (columnIndex == 0) {
              String val = ((VarProc)element).var;
              return val!=null ? val:"";
            }
            if (columnIndex == 1) {
              String val = ((VarProc)element).proc;
              return val!=null ? val:"";
            }
            if (columnIndex == 2) {
              String val = ((VarProc)element).form;
              return val!=null ? val:"";
            }
            return null;
          }

          @Override
          public Image getColumnImage(Object element, int columnIndex) {
            Display d = Display.getCurrent();
            if (columnIndex == 5)
              return PlatformUI.getWorkbench()
                  .getSharedImages()
                  .getImage(ISharedImages.IMG_ELCL_REMOVE);
            if (columnIndex == 3)
              return im.getImage(d,"icons/uparrow.png");
            if (columnIndex == 4)
              return im.getImage(d,"icons/downarrow.png");
            return null;
          }
        });
    selected.getControl().addDisposeListener(new DisposeListener() {
      
      @Override
      public void widgetDisposed(DisposeEvent e) {
        im.dispose();
      }
    });
    //selected.setFilter("*");
    
    Set<String> sern = new HashSet<>(rep.getSernames().keySet());
    String[] sva = rep.getVisSelectedAttrs(), wt = rep.getSelectedAttrsWidth(),
             frml = rep.getSelectedFormula();
    for(int i = 0;i<sva.length;i++){
      String s = sva[i], wi = wt[i], f = frml[i];
      sel.add(s);
      sern.remove(s);
      setts.put(s, new VarProc(s, wi, f));
    }
    selected.setInput(toObj(sel));
    for(String s:sern)
      avail.add(s);
    return p;
  }
  protected Object[] toObj(Set<String> sel2) {
    Object[]ret = new Object[sel.size()];
    int i = 0;
    for(String s:sel){
      ret[i++] = setts.get(s);
      if(ret[i-1] == null)
        ret[i-1] = new VarProc(s, null, null);
    }
    return ret;
  }
  private Shell shell;
  ReportSettings rep;

  public TVReportSettingsWizard(Shell shell, TreeNode rep) {
    super(shell);
    this.shell = shell;
    this.rep = (ReportSettings) rep;
    setShellStyle(SWT.CLOSE | SWT.MODELESS | SWT.RESIZE | SWT.BORDER | SWT.TITLE);
  }
  @Override
  protected boolean isResizable() {
    return true;
  }
  @Override
  protected void okPressed() {
    try {
      ArrayList<Object>nw = new ArrayList<>(),
                        nww = new ArrayList<>(),
                        nwf = new ArrayList<>();
      Set<VarProc>inds = new TreeSet<>(new Comparator<VarProc>(){
        @Override
        public int compare(VarProc o1, VarProc o2) {
          return ((Double)o1.getId()).compareTo(o2.getId());
        }
      });
      for(String s:sel)
      {
        String bs = s;
        VarProc vp = setts.getOrDefault(bs,new VarProc(s, null, null));
        String proc = vp.proc;
        if(proc == null)
          proc = "";
        inds.add(vp);
//        inds.add(vp.getId());
//        nww.add(proc);
      }
      final ReportSettings rep = this.rep;
      WorkspaceJob j = new WorkspaceJob("Change settings for TV report"){

        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) {
          if(rep == null)
            return Status.CANCEL_STATUS;
          for(VarProc vp:inds){
            String s = vp.var;
            if(s.equals("SeqID"))
              s = SystemNames.SEQUENTIAL_ID_REF_NAME;
            else{
              String alt = rep.getSernames().get(s);
              if(alt!=null)
                s = alt;
              }
            nw.add(s);
            String proc = vp.proc;
            if(proc == null)
              proc = "";
            nww.add(proc);
            String form = vp.form;
            if(form == null)
              form = "";
            nwf.add(form);
          }
          if(settingsChanges(rep, nw,nww,nwf)){
            boolean started = rep.getTreeDB().startTransaction("Change report settings");
            if (!started) return Status.CANCEL_STATUS;
            try {
              rep.attrs().setConstAttribute(ReportSettings.ATTR_SEL_ATTR, 
                  new ListValue(AttributeType.STRING, nw), 0);
              rep.attrs().setConstAttribute(ReportSettings.ATTR_WIDTH_ATTR, 
                  new ListValue(AttributeType.STRING, nww), 0);
              rep.attrs().setConstAttribute(ReportSettings.ATTR_SEL_FORMULA, 
                  new ListValue(AttributeType.STRING, nwf), 0);
              rep.commitAttributes();
              if(started) {
                rep.getTreeDB().commit();
                started = false;
              }
            }finally {
              if(started)
                rep.getTreeDB().rollback();
            }
          }
          return Status.OK_STATUS;
        }
        
      };
      j.schedule();
      super.okPressed();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  private boolean listChanges(String[]def, ArrayList<Object> list){
    int i=0;
    if(def.length!=list.size())
      return true;
    while(i<def.length&&def[i].equals(list.get(i)))
      i++;
    if(i>=def.length)
      return false;
    return true;
  }
  private boolean settingsChanges(
      ReportSettings rep2, ArrayList<Object> sela, ArrayList<Object> selw, ArrayList<Object> self) {
    String[]ssela = rep2.getDefSelectedAttrs(),
        sselw = rep2.getDefAttrsWidth(),
        sself = rep2.getDefSelectedFormula();
    return listChanges(ssela,sela)||listChanges(sselw,selw)||listChanges(sself,self);
  }
  protected void moveDown(Object element) {
    VarProc vi = (VarProc) element;
    vi.setId(getNextId(vi));
    selected.refresh(vi);
    SortedMap<Double, Object> v = new TreeMap<>();
    for (TableItem ti : selected.getTable().getItems()) {
      VarProc vi2 = (VarProc) ti.getData();
      //v.put(vi2.getId(), vi2.getValue());
    }
    //v.remove(Double.MAX_VALUE);
    //markDirty();
//    table.setInput(vals.get(lasttype));
    selected.refresh();
  }

  protected void moveUp(Object element) {
    VarProc vi = (VarProc) element;
    vi.setId(getPreviousId(vi));
    selected.refresh(vi);
    SortedMap<Double, Object> v = new TreeMap<>();
    for (TableItem ti : selected.getTable().getItems()) {
      VarProc vi2 = (VarProc) ti.getData();
      //v.put(vi2.getId(), vi2.getValue());
    }
    //v.remove(Double.MAX_VALUE);
//    vals.put(lasttype, new ArrayList<>(v.values()));
//    markDirty();
    //table.setInput(vals.get(lasttype));
    selected.refresh(); // TODO Auto-generated method stub
  }

  protected double getPreviousId(VarProc vi) {
    TableItem[] tis = selected.getTable().getItems();
    if (tis.length > 0) {
      if (tis[0].getData().equals(vi)) return vi.getId();

      if (tis.length > 1 && tis[1].getData().equals(vi))
        return ((VarProc) tis[0].getData()).getId() - 1;
      for (int i = 2; i < tis.length; i++)
        if (tis[i].getData().equals(vi))
          return (((VarProc) tis[i - 2].getData()).getId()
                  + ((VarProc) tis[i - 1].getData()).getId())
              / 2;
    }
    return vi.getId();
  }

  protected double getNextId(VarProc vi) {
    TableItem[] tis = selected.getTable().getItems();
    for (int i = 0; i < tis.length; i++)
      if (tis[i].getData().equals(vi)) {
        if ((i + 1) >= tis.length) return vi.getId();
        if ((i + 2) >= tis.length) return ((VarProc) tis[i + 1].getData()).getId() + 1;
        return (((VarProc) tis[i + 1].getData()).getId()
                + ((VarProc) tis[i + 2].getData()).getId())
            / 2;
      }
    return vi.getId();
  }
}
