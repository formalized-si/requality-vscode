package com.unitesk.requality.eclipse.views.projectSettings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPropertyPage;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.SystemNames;
import com.unitesk.requality.core.attribute.reference.ReferenceRenames;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.report.TraceabilityDataSource;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class ProjectReferenceProperties extends RequalityPropertyPage implements IWorkbenchPropertyPage {
  private TreeNode nodeToStore;
  private IProject p;
  //private Text helpLabel;
  private Text defRefName;
  //text on top of preference page
  private static final String PREFERENCE_REPLS = "_com.unitesk.requality.reference_replaces";

  private Set<String> names = new HashSet<>();
  private Map<String, ReplPair> replacementMap = new HashMap<>();
  //text on top of preference page
  private org.eclipse.swt.widgets.Text torename, replacement;
  private org.eclipse.swt.widgets.List listname;
  private TableViewer repl;
  private Button button;
  private String defText=null;

  private class ReplPair {
    private String name, repl;

    public ReplPair(String name, String repl) {
      this.name = name;
      this.repl = repl;
    }

    @Override
    public String toString() {
      return "'" + name + "' -> '" + repl + "'";
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof ReplPair) return name.equals(((ReplPair) obj).name);
      return super.equals(obj);
    }
  }

  public ProjectReferenceProperties() {}

  @Override
  protected Control createContents(Composite parent) {

    Composite c = new Composite(parent, SWT.NONE);
    c.setLayout(new GridLayout(1, true));
    c.setLayoutData(new GridData(GridData.FILL_BOTH));

    final Group container3 = new Group(c, SWT.NONE);
    container3.setText("References antonyms:");
    container3.setLayout(new GridLayout(2, false));
    container3.setLayoutData(new GridData(GridData.FILL_BOTH));

    initReferenceRenameComposite(container3);

    final Group container4 = new Group(c, SWT.NONE);
    container4.setText("References settings:");
    container4.setLayout(new GridLayout(2, false));
    container4.setLayoutData(new GridData(GridData.FILL_BOTH));

    Label l = new Label(container4, SWT.NONE);
    l.setText("Default reference name:");

    defRefName = new Text(container4, SWT.NONE);
    defRefName.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    defText = nodeToStore.getTreeDB().getDefReferenceName();
    defRefName.setText(nodeToStore.getTreeDB().getDefReferenceName());
    defRefName.addModifyListener(new ModifyListener(){
    
      @Override
      public void modifyText(ModifyEvent e) {
        if(defRefName.isDisposed())
          return;
        defText = defRefName.getText();
      }
    });
    return c;
  }

  Composite isc;
  ScrolledComposite sc;

  private void initReferenceRenameComposite(Group container3) {
    Composite desccompose = new Composite(container3, SWT.FILL);
    desccompose.setLayoutData(new GridData(GridData.FILL_BOTH));
    desccompose.setLayout(new GridLayout(2, true));
    Label descl = new Label(desccompose, SWT.HORIZONTAL);
    descl.setText("Select reference name:");
    descl = new Label(desccompose, SWT.NONE);
    descl.setText("Default renames:");

    sc = new ScrolledComposite(desccompose, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
    sc.setAlwaysShowScrollBars(true);
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.horizontalSpan = 2;
    sc.setLayoutData(gd);
    sc.setLayout(new GridLayout(1, true));

    isc = new Composite(sc, SWT.NONE);
    isc.setLayout(new GridLayout(2, true));
    gd = new GridData(GridData.FILL_BOTH);
    gd.widthHint = sc.getSize().x;
    isc.setLayoutData(gd);

    sc.setContent(isc);
    sc.setExpandHorizontal(true);
    sc.setExpandVertical(true);

    Button b = new Button(isc, SWT.NONE);
    b.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    repl = new TableViewer(isc, SWT.BORDER | SWT.RESIZE | SWT.FULL_SELECTION);
    gd = new GridData(GridData.FILL_BOTH);
    gd.verticalSpan = 2;
    repl.getTable().setLayoutData(gd);

    listname = new List(isc, SWT.NONE);
    listname.setLayoutData(new GridData(GridData.FILL_BOTH));

    final TableViewerColumn column = new TableViewerColumn(repl, SWT.FULL_SELECTION);
    column.getColumn().setText("Replacement");
    column.getColumn().setWidth(200);
    column.getColumn().setResizable(true);
    column.getColumn().setMoveable(true);

    repl.getControl()
        .addControlListener(
            new ControlListener() {
              @Override
              public void controlResized(ControlEvent e) {
                if(repl.getControl().isDisposed())
                  return;
                column.getColumn().setWidth(repl.getControl().getSize().x - 25);
              }

              @Override
              public void controlMoved(ControlEvent e) {
                // TODO Auto-generated method stub

              }
            });

    TableViewerColumn cdel = new TableViewerColumn(repl, SWT.FULL_SELECTION);
    cdel.getColumn().setResizable(false);
    cdel.getColumn().setWidth(25);
    cdel.setEditingSupport(
        new EditingSupport(repl) {
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
            if(cdel.getViewer().getControl().isDisposed())
              return false;
            ReplPair pair = (ReplPair) element;
            String name = pair.name;
            boolean glob = ReferenceRenames.globalRenames.containsKey(name);
            if (glob && ReferenceRenames.globalRenames.get(name).equals(pair.repl)) return false;
            replacementMap.remove(name);
            repl.remove(element);
            if (glob) addAcronym(name, ReferenceRenames.globalRenames.get(name));
            updateComposite();
            return false;
          }
        });

    repl.setLabelProvider(
        new ITableLabelProvider() {
          @Override
          public void removeListener(ILabelProviderListener listener) {}

          @Override
          public boolean isLabelProperty(Object element, String property) {
            return false;
          }

          @Override
          public void dispose() {}

          @Override
          public void addListener(ILabelProviderListener listener) {}

          @Override
          public Image getColumnImage(Object element, int columnIndex) {
            ReplPair pair = (ReplPair) element;
            String name = pair.name;
            boolean glob = ReferenceRenames.globalRenames.containsKey(name);
            if (columnIndex == 1)
              if (!glob || !ReferenceRenames.globalRenames.get(name).equals(pair.repl))
                return PlatformUI.getWorkbench()
                    .getSharedImages()
                    .getImage(ISharedImages.IMG_ELCL_REMOVE);
            return null;
          }

          @Override
          public String getColumnText(Object element, int columnIndex) {
            if (columnIndex == 1) return null;
            return element.toString();
          }
        });
    initCompositePairs();

    Composite edit = new Composite(desccompose, SWT.NONE);
    gd = new GridData(GridData.FILL_BOTH);
    gd.horizontalSpan = 2;
    edit.setLayoutData(gd);
    edit.setLayout(new GridLayout(3, false));

    descl = new Label(edit, SWT.NONE);
    descl.setText("Reference name");

    descl = new Label(edit, SWT.NONE);
    descl.setText("Backward reference name");
    gd = new GridData();
    gd.horizontalSpan = 2;
    descl.setLayoutData(gd);

    b.setText("Load reference names");

    ModifyListener ml =
        new ModifyListener() {
          @Override
          public void modifyText(ModifyEvent e) {
            if(button.isDisposed())
              return;
            button.setEnabled(!torename.getText().isEmpty() && !replacement.getText().isEmpty());
          }
        };

    torename = new org.eclipse.swt.widgets.Text(edit, SWT.NONE);
    torename.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    torename.addModifyListener(ml);

    replacement = new org.eclipse.swt.widgets.Text(edit, SWT.NONE);
    replacement.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    replacement.addModifyListener(ml);

    button = new Button(edit, SWT.NONE);
    button.setText("Set");
    button.setEnabled(false);
    button.addMouseListener(
        new MouseListener() {
          @Override
          public void mouseUp(MouseEvent e) {}

          @Override
          public void mouseDown(MouseEvent e) {
            if(torename.isDisposed())
              return;
            addAcronym(torename.getText(), replacement.getText());
          }

          @Override
          public void mouseDoubleClick(MouseEvent e) {}
        });

    //loadNames();
    listname.setItems(names.toArray(new String[0]));

    listname.addSelectionListener(
        new SelectionListener() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            if(button.isDisposed())
              return;
            button.setEnabled(true);
            if (listname.getSelectionCount() < 1) return;
            repl.getTable().deselectAll();
            String sel = listname.getSelection()[0];
            replacement.setText(sel);
            if (replacementMap.containsKey(sel)) replacement.setText(replacementMap.get(sel).repl);
            torename.setText(sel);
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {
            // TODO Auto-generated method stub

          }
        });

    b.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
      if(b.isDisposed())
          return;
        final Button button = ProjectReferenceProperties.this.button;
        final Text torename = ProjectReferenceProperties.this.torename;
        final Text replacement = ProjectReferenceProperties.this.replacement;
        final List listname = ProjectReferenceProperties.this.listname;
        WorkspaceJob j = new WorkspaceJob("load names for project"){

          @Override
          public IStatus runInWorkspace(IProgressMonitor monitor) {
            loadNames();
            final Set<String> names = ProjectReferenceProperties.this.names;
            EclipseAPI.getDisplay().syncExec(new Runnable(){

              @Override
              public void run() {
                if(button.isDisposed())
                  return;
                button.setEnabled(false);
                torename.setText("");
                replacement.setText("");
                listname.setItems(names.toArray(new String[0]));
              }
            });
            
            return Status.OK_STATUS;
          }
        };
        j.schedule();
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        // TODO Auto-generated method stub
        
      }
    });

    repl.getTable()
        .addSelectionListener(
            new SelectionListener() {

              @Override
              public void widgetSelected(SelectionEvent e) {
                if(repl.getTable().isDisposed())
                  return;
                ReplPair rp =
                    (ReplPair) (((StructuredSelection) repl.getSelection()).getFirstElement());
                if (rp != null) {
                  listname.deselectAll();
                  torename.setText(rp.name);
                  replacement.setText(rp.repl);
                  button.setEnabled(true);
                }
              }

              @Override
              public void widgetDefaultSelected(SelectionEvent e) {}
            });
    updateComposite();
  }

  private void initCompositePairs(){
    if (!replacementMap.isEmpty()) for (ReplPair rp : replacementMap.values()) repl.add(rp);
  }

  private void updateComposite() {
    sc
        .setMinSize(isc.getSize().x, isc.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).y);
    sc.layout(true, true);
  }

  protected void addAcronym(String text, String text2) {
    if (text.equals("")) button.setEnabled(false);
    if (replacementMap.containsKey(text)) repl.remove(replacementMap.get(text));
    ReplPair rp = new ReplPair(text, text2);
    repl.add(rp);
    replacementMap.put(text, rp);
    updateComposite();
  }

  @Override
  public void setElement(org.eclipse.core.runtime.IAdaptable element) {
    if (element instanceof IProject) {
      p = (IProject) element;
      TreeDB db = null;
      try {
        EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        db = et.getTreeDB(p, null, et.getLocalName(p));
      } catch (IOException e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
      }
      if (db != null) {
        nodeToStore = db.getRootNode();
        Map<String, String> acr = ReferenceRenames.getNamesAcronyms(db);
        for (String s : acr.keySet()) replacementMap.put(s, new ReplPair(s, acr.get(s)));
      }
    }
  };

  private void loadNames() {
    names = new HashSet<>();
    if (nodeToStore == null) return;
    //for(TreeDB db:TreesTracker.getTracker().getTrees()){
    try {
      TreeNode node = nodeToStore;
      DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(node, true, true, false);
      Iterator<TreeNode> it = dftw.iterator();
      while (it.hasNext()) {
        TreeNode nde = it.next();
        for (Attribute a : nde.attrs()) {
            if(a.isReference() )
                if ((a.isUserVisible() || SystemNames.getOne(node.getTreeDB()).getVisSysName(a.getKey())!=null))
                  names.add(a.getKey());
        }
      }
    } catch (Exception e) {
      //TODO show error on names
    }
    //}
    java.util.List<Object> l = new ArrayList<>();
    for (String s : names) {
      l.add(s);
    }
//    nodeToStore.attrs().setConstAttribute(PREFERENCE_NAMES, new ListValue(AttributeType.STRING, l),
//        Flag.HIDDEN);
//    nodeToStore.commitAttributes();
  }

  private void fireOutClear(TreeNode nodeToStore2) {
    nodeToStore2.clearShownChildren();
    for (UUID uid : nodeToStore2.getActiveWorkChildrenUUIDs()) {
      TreeNode node = nodeToStore2.getTreeDB().getCachedNodeOrVirt(uid);
      if (node != null) fireOutClear(node);
    }
  }

  //Changing settings to default
  @Override
  protected void performDefaults() {
    if(inParallelUpdate())
      return;
//    if (nodeToStore != null) {
//      synchronized (wj) {
//        wj = new WorkspaceJob("Set project refs settings to default for "+nodeToStore.getTreeDB().getSemiResName()){
//          @Override
//          public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
//            synchronized (ProjectReferenceProperties.this) {
//              try{
//                nodeToStore.attrs().removeAttribute(SortingSettings.SORT_BY_INDEX);
//                nodeToStore.attrs().removeAttribute(SortingSettings.SORT_BY_LOCATIONS);
//                nodeToStore.attrs().removeAttribute(SortingSettings.SORT_SEP_TYPES);
//                nodeToStore
//                    .attrs()
//                    .setConstAttribute(
//                        PREFERENCE_REPLS, new ListValue(AttributeType.STRING, new ArrayList<>()),
//                        Flag.HIDDEN);
//                nodeToStore.attrs().removeAttribute(TreeDB.DEFAULT_REFERENCE_NAME);
//                nodeToStore.commitAttributes();
//                TraceabilityDataSource.setRevertNames(
//                    nodeToStore.getTreeDB(), getNamesAcronyms(nodeToStore.getTreeDB()));
//              }finally{
//                wj = null;
//              }
//            }
//            return Status.OK_STATUS;
//          }
//        };
//        PropertiesJobManager.registerJob(wj, nodeToStore.getTreeDB());
//      }
//      
    //}
    repl.getTable().clearAll();
    repl.refresh();
    torename.setText("");
    replacement.setText("");
    defRefName.setText(nodeToStore.getTreeDB().getDefReferenceName());
    button.setEnabled(false);
    replacementMap.clear();
    super.performDefaults();
    //setElement(p);
    initCompositePairs();
    updateComposite();
    //fireOutClear(nodeToStore);needs to be called on ok operation
  }


  @Override
  public boolean isOkOperationInTransaction() {
    return false;
  }

  @Override
  public void prepareVars() {
  }
  @Override
  public IStatus okPressedAction(IProgressMonitor monitor) {
    if(actionsStarted)
      return Status.CANCEL_STATUS;
    actionsStarted = true;
    try {
      java.util.List<Object> list = new ArrayList<>();
      for (ReplPair rp : replacementMap.values()) {
        if (!ReferenceRenames.globalRenames.containsKey(rp.name) || !ReferenceRenames.globalRenames.get(rp.name).equals(rp.repl)) {
          list.add(rp.name);
          list.add(rp.repl);
        }
      }
      if(nodeToStore == null)
        return Status.CANCEL_STATUS;
      //TODO move settings to cache storage
      nodeToStore.attrs().setConstAttribute(PREFERENCE_REPLS, new ListValue(AttributeType.STRING, list),
          Flag.HIDDEN);
      if (!defText.equals(nodeToStore.getTreeDB().getDefReferenceName()))
        nodeToStore.attrs().setConstAttribute(TreeDB.DEFAULT_REFERENCE_NAME, defText,
            Flag.HIDDEN);
      nodeToStore.commitAttributes();
      TraceabilityDataSource.setRevertNames(
          nodeToStore.getTreeDB(), ReferenceRenames.getNamesAcronyms(nodeToStore.getTreeDB()));
      fireOutClear(nodeToStore);
      return Status.OK_STATUS;
    }finally {
      actionsStarted = false;
    }
  }

  @Override
  public boolean settingsChanged() {
    java.util.List<Object> list = new ArrayList<>();
    for (ReplPair rp : replacementMap.values()) {
      if (!ReferenceRenames.globalRenames.containsKey(rp.name) || !ReferenceRenames.globalRenames.get(rp.name).equals(rp.repl)) {
        list.add(rp.name);
        list.add(rp.repl);
      }
    }
    Map<String, String> namesAcronyms = ReferenceRenames.getNamesAcronyms(nodeToStore.getTreeDB());
    String defText = defRefName.getText();
    java.util.List<Object> lstored = nodeToStore.attrs().getListAttributeValue(PREFERENCE_REPLS);
    if(lstored ==null!=list.isEmpty() || lstored!=null && !lstored.equals(list))
      return true;
    String repl = nodeToStore.attrs().getStringAttributeValue(TreeDB.DEFAULT_REFERENCE_NAME);
    if(repl == null ^ defText.equals(TreeDB.getDefValueOfDefReferenceName()) || repl!=null && !repl.equals(defText) )
      return true;
    if(TraceabilityDataSource.changes(nodeToStore.getTreeDB(),namesAcronyms))
      return true;
    return false;
  }

  @Override
  public TreeDB getTreeDB() {
    return nodeToStore.getTreeDB();
  }

  @Override
  public boolean inputCorrect() {
    return true;
  }
}
