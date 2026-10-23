package com.unitesk.requality.vscode.port.eclipse.views.properties;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.resource.ColorRegistry;
import com.unitesk.requality.vscode.ui.jface.viewers.DoubleClickEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.IDoubleClickListener;
import com.unitesk.requality.vscode.ui.jface.viewers.IFontProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Font;
import com.unitesk.requality.vscode.ui.swt.graphics.FontData;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Listener;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;
import com.unitesk.requality.vscode.ui.workbench.ITheme;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.filter.ITreeNodeFilter;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalitySorter;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.EditElementDialog;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.SelectNodeFromRequirementsTree;
import com.unitesk.requality.vscode.port.eclipse.views.panels.ElementViewPanel;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;

public class TemplatesPanel implements ITreeChangeListener {
  private Font savedFont = null;
  private class DecorationProvider extends LabelProvider
      implements /*IColorProvider,*/ IFontProvider {
    @Override
    public Image getImage(Object element) {
      if (element == null) return null;
      if (element instanceof Requirement) {
        Requirement req = (Requirement) element;
        if (req.getQualifiedId().equals("/Requirements")) {
          try {
            return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider()
                .getImage(EclipseTracker.getResourceByNode(req, LockType.NO_LOCK).getProject());
          } catch (LockIsBusy e) {
            RLogger.logDebug(CoreUtils.getTrace(e),7);
          }
        }
      }
      return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider().getImage(element);
    }

    @Override
    public String getText(Object element) {
      if (element instanceof String) return "Empty(Default)";
      if (element instanceof TreeNode) {
        TreeNode nd = (TreeNode) element;
        return nd.getId();
      }
      return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider().getText(element);
    }

    //		@Override
    //		public Color getForeground(Object element) {
    //			// TODO Auto-generated method stub
    //			return null;
    //		}
    //
    //		@Override
    //		public Color getBackground(Object element) {
    //			String id = null,templ=null;
    //			StructuredSelection ss = (StructuredSelection) nodeTree.getSelection();
    //			if(ss!=null && !ss.isEmpty()){
    //				Object o = ss.getFirstElement();
    //				if(o instanceof TreeNode)
    //					templ = ((TreeNode)o).getId();
    //			}
    //			if(element instanceof TreeNode)
    //				id = ((TreeNode) element).getId();
    //			//if(id == null && templ == null || id!=null && id.equals(templ))
    //				//return new Color(nodeTree.getControl().getDisplay(), new RGB(120,250,120));
    //			return null;
    //		}
    @Override
    public Font getFont(Object element) {
      String id = null, templ = null;
      TreeNode node = db.getActiveTemplate(filteredType);
      if (node != null) templ = node.getId();
      if (element instanceof TreeNode) id = ((TreeNode) element).getId();
      if (id == null && templ == null || id != null && id.equals(templ)) {
        Font f = nodeTree.getControl().getFont();
        FontData fd = new FontData(f.getFontData()[0].toString());
        fd.setStyle(fd.getStyle() | SWT.BOLD);
        if(savedFont!=null)
          savedFont.dispose();
        savedFont = new Font(f.getDevice(), fd);
        return savedFont;
      }
      return null;
    }
  }

  protected TreeViewer nodeTree = null;

  private static int TREE_WIDTH = 250;
  private java.util.List<String> types = null;
  //private ElementViewPanel viewp;
  private Composite newPar;
  private Button setsel, createt, editt;
  private Combo templlist;
  private Button removeB;
  private TreeDB db;
  private DecorationProvider lprovider = new DecorationProvider();
  private boolean isDisposed = false;//TODO mb reuse treeNodeProps status by provider
  private String filteredType = Requirement.TYPE_NAME;
  //protected void setTemplate(TreeNode node){
  //    	viewp.setTreeNode(node);
  //  }
  protected ITreeContentProvider getContentProvider() {
    return new ITreeContentProvider() {
      private TreeDB db;

      @Override
      public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        if (newInput == null) return;
        if (newInput instanceof TreeDB) this.db = (TreeDB) newInput;
        else this.db = ((TreeNode) newInput).getTreeDB();
        final TreeDB db = this.db;
        WorkspaceJob j = new WorkspaceJob("Preload templates for "+db.getVisibleName()){
          @Override
          public IStatus runInWorkspace(IProgressMonitor monitor) {
            if(db == null || isDisposed)
              return Status.CANCEL_STATUS;
            synchronized (TemplatesPanel.this) {
              db.getTemplates(); 
            }
            return Status.OK_STATUS;
          }
            
        };
        j.schedule();
      }

      @Override
      public void dispose() {
        if(savedFont!=null)
          savedFont.dispose();
        isDisposed = true;
        db.removeListener(TemplatesPanel.this);
      }

      @Override
      public boolean hasChildren(Object element) {
        return getElements(element).length > 0;
      }

      @Override
      public Object getParent(Object element) {
        return db;
      }

      @Override
      public Object[] getElements(Object inputElement) {
        if (db != null
            && (inputElement instanceof TreeDB || db.getRootNode().equals(inputElement))) {
          List<Object> filteredTemplates = new ArrayList<>();
          for (TreeNode node : db.getTemplates())
            if (filteredType.equals(node.getType())) filteredTemplates.add(node);
          filteredTemplates.add("Empty(Default)");
          return filteredTemplates.toArray();
        }
        return new Object[0];
      }

      @Override
      public Object[] getChildren(Object parentElement) {
        return getElements(parentElement);
      }
    };
  }

  public TemplatesPanel(final Composite parent) {
    //parent.setLayout(new GridLayout(1,true));
    if (parent.getParent().getLayout() instanceof GridLayout)
      parent.setLayoutData(new GridData(GridData.FILL_BOTH));

    newPar = new Composite(parent, SWT.NONE);

    GridLayout gl = new GridLayout(1, false);
    gl.marginHeight = gl.marginWidth = 0;
    newPar.setLayout(gl);
    if (parent.getLayout() instanceof GridLayout)
      newPar.setLayoutData(new GridData(GridData.FILL_BOTH));

    Composite header = new Composite(newPar, SWT.NONE);
    header.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    header.setLayout(new GridLayout(2, false));

    (new Label(header, SWT.NONE)).setText("Select Node Type");
    ITheme theme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
    ColorRegistry colors = theme.getColorRegistry();
    templlist = new Combo(header, SWT.READ_ONLY);
    templlist.setBackground(colors.get("org.eclipse.ui.editors.backgroundColor"));
    templlist.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    //GridData gd = new GridData(GridData.FILL_BOTH);
    //gd.horizontalSpan = 3;
    //templlist.setLayoutData(gd);
    templlist.addSelectionListener(
        new SelectionListener() {

          @Override
          public void widgetSelected(SelectionEvent e) {
    	    if(templlist.isDisposed())
              return;
            int sel = templlist.getSelectionIndex();
            if (sel > -1) {
              String s = getTypesList(db).get(sel);
              filteredType = s;
              nodeTree.refresh();
            }
            correctButtonsEnabledState();
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {
            // TODO Auto-generated method stub

          }
        });

    Group mainG = new Group(newPar, SWT.NONE);
    mainG.setLayoutData(new GridData(GridData.FILL_BOTH));
    gl = new GridLayout(1, false);
    gl.marginHeight = gl.marginWidth = 0;
    mainG.setLayout(gl);

    Composite tbuttons = new Composite(mainG, SWT.NONE);
    GridLayout l = new GridLayout(2, false);
    l.marginWidth = l.marginHeight = 0;
    tbuttons.setLayout(l);
    tbuttons.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    createt = new Button(tbuttons, SWT.NONE);
    createt.setText("Create template");
    createt.setEnabled(true);
    createt.addListener(
        SWT.Selection,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
    	    if(db == null || templlist.isDisposed())
              return;
            int sel = templlist.getSelectionIndex();
            if(sel==-1)
              return;
            String type = getTypesList(db).get(sel);
            //hack to init templates folder before transaction
            final TreeDB db = TemplatesPanel.this.db;
            final TreeViewer nodeTree = TemplatesPanel.this.nodeTree;
            WorkspaceJob j = new WorkspaceJob("Create template for "+db.getVisibleName()){

              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                if(db == null)
                  return Status.CANCEL_STATUS;
                db.getOrCreateTemplatesFolder();
                AtomicBoolean started = new AtomicBoolean(db.startTransaction(
                    "Create template of '" + CoreUtils.splitWords(type) + "' type."));
                if (started.get()) {
                  //TreeNode act = db.getActiveTemplate(type);
                  TreeNode node = db.createTemplate(type);
                  EclipseAPI.getDisplay().asyncExec(new Runnable(){
                  
                    @Override
                    public void run() {
                      if(isDisposed || parent.isDisposed() || node == null) {
                        if(started.get()) {
                          db.rollback();
                          started.set(false);
                        }
                        return;
                      }
                      EditElementDialog eed =
                          new EditElementDialog(
                              parent.getShell(),
                              node,
                              "Create " + db.getNodeType(type).getVisibleClassName() + " template",
                              true);
                      if (eed.open() == Window.OK) {
                	    WorkspaceJob j = new WorkspaceJob("Commit templates changes for "+db.getVisibleName()){
                           @Override
                           public IStatus runInWorkspace(IProgressMonitor monitor) {
                             try {
                                if(node == null)
                                  return Status.CANCEL_STATUS;
                                node.commitAttributes();
                                if(started.get()) {
                                  db.commit();
                                  started.set(false);
                                }
                                return Status.OK_STATUS;
                             }finally {
                               if(started.get()){
                                 db.rollback();
                                 started.set(false);
                               }
                             }
                          }
                          
                        };
                        j.schedule();
                      } else{
                        if(started.get()) {
                          db.rollback();
                          started.set(false);
                        }
                        nodeTree.refresh();
                      }
                    
                    }
                  });
                }
                return Status.OK_STATUS;
              }
              
            };
            j.schedule();
          }
        });

    removeB = new Button(tbuttons, SWT.NONE);
    removeB.setText("Extract template");
    removeB.addListener(
        SWT.Selection,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
    	    if(parent.isDisposed() || db == null)
              return;
            SelectNodeFromRequirementsTree sn =
                new SelectNodeFromRequirementsTree(parent.getShell(), db, false) {};
            sn.setContentFilter(
                new ITreeNodeFilter() {
                  private Map<TreeNode, Boolean> enTree = new HashMap<>();

                  @Override
                  public boolean select(TreeNode node) {
                    if (node.isRestricted()) return false;
                    if (enTree.containsKey(node)) return enTree.get(node);
                    if (node.getType().equals(filteredType)) {
                      enTree.put(node, true);
                      return true;
                    }
                    for (TreeNode node2 : node.getShownChildren()) //TODO check
                    if (select(node2)) {
                        enTree.put(node, true);
                        return true;
                      }
                    enTree.put(node, false);
                    return false;
                  }
                });
            sn.setFilter(
                new ITreeNodeFilter() {
                  @Override
                  public boolean select(TreeNode node) {
                    return (node.getType().equals(filteredType)
                        || node.getTreeDB()
                            .getTreeLogic()
                            .canBeChildOf(filteredType, node.getType()));
                    //return node.getTreeDB().getTreeLogic().canBeTemplate(node.getType());
                  }
                });
            sn.setEnablementFilter(
                new ITreeNodeFilter() {
                  @Override
                  public boolean select(TreeNode node) {
                    return (node.getType().equals(filteredType));
                    //return node.getTreeDB().getTreeLogic().canBeTemplate(node.getType());
                  }
                });
            int res = sn.open();
            if (res == Window.OK) {
              TreeNode node = sn.getResult();
              //hack to init templates not in transaction
              final TreeDB db = TemplatesPanel.this.db;
              WorkspaceJob j = new WorkspaceJob("Extract template for "+db.getVisibleName()){
                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) {
                  if(db == null)
                    return Status.CANCEL_STATUS;
                  node.getTreeDB().getOrCreateTemplatesFolder();
                  boolean started = node.getTreeDB()
                      .startTransaction("Extract Template '" + node.getNameOrId() + "'");
                  if (started) {
                    try {
                      node.getTreeDB().extractTemplate(node, node.getNameOrId());
                      if(started) {
                        node.getTreeDB().commit();
                        started = false;
                      }
                    }finally {
                      if(started)
                        node.getTreeDB().rollback();
                    }
                  }
                  return Status.OK_STATUS;
                }
              };
              j.schedule();
            }
            nodeTree.refresh(true);
          }
        });
    removeB.setEnabled(true);
    //newPar.setLayoutData(new GridData(GridData.FILL_BOTH));

    new Label(mainG, SWT.NONE).setText("List of templates:");

    Composite list = new Composite(mainG, SWT.NONE);
    list.setLayoutData(new GridData(GridData.FILL_BOTH));
    l = new GridLayout(2, false);
    l.marginWidth = l.marginHeight = 0;
    list.setLayout(l);

    nodeTree = new TreeViewer(list);
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.widthHint = TREE_WIDTH;
    gd.horizontalSpan = 2;
    nodeTree.getControl().setLayoutData(gd);
    nodeTree.setLabelProvider(getLabelProvider());
    nodeTree.setAutoExpandLevel(2);
    nodeTree.setComparator(
        new RequalitySorter() {
          @Override
          public int compare(Viewer viewer, Object e1, Object e2) {
            if (e1 instanceof String) return -1;
            if (e2 instanceof String) return -1;
            return super.compare(viewer, e1, e2);
          }
        });
    if (db != null) nodeTree.setInput(db);

    nodeTree.setContentProvider(getContentProvider());
    nodeTree.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));

    nodeTree.addSelectionChangedListener(
        new ISelectionChangedListener() {

          @Override
          public void selectionChanged(SelectionChangedEvent event) {
            updateTemplListSel();
            correctButtonsEnabledState();
          }
        });

    nodeTree.addDoubleClickListener(
        new IDoubleClickListener() {

          @Override
          public void doubleClick(DoubleClickEvent event) {
            callEditTemplate();
          }
        });

    Composite buttons2 = new Composite(list, SWT.NONE);
    l = new GridLayout(1, false);
    l.marginLeft = l.marginHeight = 0;
    buttons2.setLayout(l);
    buttons2.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, true));

    gd = new GridData(SWT.LEFT, SWT.CENTER, true, false);
    gd.widthHint = 100;

    editt = new Button(buttons2, SWT.NONE);
    editt.setText("Edit");
    editt.setEnabled(false);
    editt.setLayoutData(gd);
    editt.addListener(
        SWT.Selection,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
            callEditTemplate();
          }
        });

    gd = new GridData(SWT.LEFT, SWT.CENTER, true, false);
    gd.widthHint = 100;

    removeB = new Button(buttons2, SWT.NONE);
    removeB.setText("Remove");
    removeB.setLayoutData(gd);
    removeB.addListener(
        SWT.Selection,
        new Listener() {

          @Override
          public void handleEvent(Event event) {
            StructuredSelection ss = (StructuredSelection) nodeTree.getSelection();
            if (ss != null && !ss.isEmpty()) {
              TreeNode node = (TreeNode) ss.getFirstElement();
              final TreeViewer nodeTree = TemplatesPanel.this.nodeTree;
              final Button setsel = TemplatesPanel.this.setsel;
              WorkspaceJob j = new WorkspaceJob("Remove template "+node.getUserVisibleName()){
              
                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) {
                   node.getTreeDB().getOrCreateTemplatesFolder();
                   boolean started = node.getTreeDB().startTransaction("Remove template " + node.getId());
                    if (started) {
                      try {
                        node.delete();
                        //TODO can be iterations problems?
                        for (Entry<String, String> en : node.getTreeDB().getActiveTemplates().entrySet())
                          if (en.getValue()!=null
                              && en.getValue().equals(node.getId()))
                            node.getTreeDB().setActiveTemplate(node.getType(), null);
                        if(started) {
                          node.getTreeDB().commit();
                          started = false;
                        }
                      }finally {
                        if(started)
                          node.getTreeDB().rollback();
                      }
                    }
                    EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    
                      @Override
                      public void run() {
                        if(isDisposed || nodeTree.getControl().isDisposed())
                          return;
                        nodeTree.refresh(true);
                        clearSelection(setsel);
                        correctButtonsEnabledState();
                      }
                    });
                    return Status.OK_STATUS;
                  }
                };
                j.schedule();
            }
          }
        });

    gd = new GridData(SWT.LEFT, SWT.CENTER, true, false);
    gd.widthHint = 100;

    setsel = new Button(buttons2, SWT.NONE);
    setsel.setText("Set as active");
    setsel.setLayoutData(gd);
    setsel.addListener(
        SWT.Selection,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
            TreeSelection ts = (TreeSelection) nodeTree.getSelection();
            if (ts != null) {
              String id = null;
              Object o = ts.getFirstElement();
              removeB.setEnabled(false);
              if (o != null) {
                if (o instanceof TreeNode) {
                  removeB.setEnabled(true);
                  id = ((TreeNode) o).getId();
                  ;
                }
                final TreeViewer nodeTree = TemplatesPanel.this.nodeTree;
                final TreeDB db = TemplatesPanel.this.db;
                String fid = id;
                WorkspaceJob j = new WorkspaceJob("Set active template"){

                  @Override
                  public IStatus runInWorkspace(IProgressMonitor monitor) {
                    db.getOrCreateTemplatesFolder();
                    boolean started = db.startTransaction(
                        "Change '"
                            + CoreUtils.splitWords(filteredType)
                            + "' template 'to "
                            + (fid == null ? "Default" : fid)
                            + "'");
                    if (started) {
                      try {
                        db.setActiveTemplate(filteredType, fid);
                        if(started) {
                          db.commit();
                          started = false;
                        }
                      }finally {
                        if(started)
                          db.rollback();
                      }
                    }
                    EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    
                      @Override
                      public void run() {
                        if(isDisposed || nodeTree.getControl().isDisposed()||db == null)
                          return;
                        nodeTree.refresh();
                        updateTemplatesList(db);//TODO called twice
                      }
                    });
                    return Status.OK_STATUS;
                  }
                  
                };
                j.schedule();
              }
              nodeTree.refresh();
            }
            updateTemplatesList(db);
          }
        });

    //        nodeTree.addSelectionChangedListener(new NodeSelectionListener());

    //      nodeTree.getTree().addMouseListener(mouseList);
    //nodeTree.getTree().addKeyListener(keyList);

    //TODO tree

    if (db != null) {
      updateTemplatesList(db);
      templlist.select(getTypesList(db).indexOf(filteredType));
    }
    clearSelection(setsel);
    //if(seltp!=null)
    //setSelectedType(seltp);
    //viewp = new ElementViewPanel(details, 2, labels,node);

  };

  protected void callEditTemplate() {
    if(nodeTree == null || nodeTree.getControl().isDisposed())
      return;
    TreeSelection sel = (TreeSelection) nodeTree.getSelection();
    if (sel != null && !sel.isEmpty() && sel.getFirstElement() instanceof TreeNode) {
      TreeNode node = (TreeNode) sel.getFirstElement();
      if (node != null) openEditor(node);
    }
  }

  protected void correctButtonsEnabledState() {
    setsel.setEnabled(false);
    createt.setEnabled(true);
    String tpe = filteredType;
    StructuredSelection s = (StructuredSelection) nodeTree.getSelection();
    editt.setEnabled(false);
    if (s != null && !s.isEmpty() && s.getFirstElement() instanceof TreeNode) {
      TreeNode selcd = (TreeNode) s.getFirstElement();
      if (selcd != null && selcd.getType().equals(tpe)) editt.setEnabled(true);
    }
    setsel.setEnabled(true);
  }

  protected boolean updateTemplListSel() {
    TreeSelection ts = (TreeSelection) nodeTree.getSelection();
    editt.setEnabled(false);
    removeB.setEnabled(false);
    if (ts != null && !ts.isEmpty() && ts.getFirstElement() instanceof TreeNode) {
      TreeNode node = (TreeNode) ts.getFirstElement();
      if (node != null) {
        removeB.setEnabled(true);
        editt.setEnabled(true);
        return true;
        //java.util.List<String> vals = getTypesList(db);
        //for(int i = 0;i<vals.size();i++)
        //if(vals.get(i).equals(node.getType())){
        //templlist.select(i);
        //updateSelectedTemplateLabel(getSelectedTemplType());
        //return true;
        //}
      }
    }
    return false;
  }

  public void clearSelection(Button setsel) {
    //clearsel.setEnabled(false);
    setsel.setEnabled(false);
    //templlist.select(-1);
  }

  protected String getSelectedTemplType() {
    TreeSelection ts = (TreeSelection) nodeTree.getSelection();
    if (ts != null && !ts.isEmpty() && ts.getFirstElement() instanceof TreeNode) {
      TreeNode node = (TreeNode) ts.getFirstElement();
      if (node != null) return node.getType();
    }
    return null;
  }

  private java.util.List<String> getTypesList(TreeDB db2) {
	//TODO adds load on job
    if (types == null) {
      Set<String> st = new TreeSet<>();
      for (String s : db2.getTypes().keySet())
        if (db2.getTreeLogic().canBeTemplate(s))
          try {
            TreeNode node =
                db2.getTypes().get(s)
                    .getTypeClass()
                    .getConstructor(TreeDB.class, UUID.class)
                    .newInstance(db2, UUID.randomUUID());
            if (node != null
                && !node.isRestricted()
                && !(node.getClass().equals(TreeNode.class))
                && node.getTreeDB().getTreeLogic().canBeTemplate(s)) st.add(s);
          } catch (InstantiationException e) {
            e.printStackTrace();
          } catch (IllegalAccessException e) {
            e.printStackTrace();
          } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          } catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
      types = new ArrayList<>();
      types.addAll(st);
    }
    return types;
  }

  private void updateTemplatesList(TreeDB db2) {
    templlist.removeAll();
    for (String s : getTypesList(db2)) templlist.add(s);
    templlist.select(getTypesList(db).indexOf(filteredType));
    updateTemplListSel();
  }

  protected void openEditor(TreeNode node) {
    if(node == null)
      return;
    final TreeViewer nodeTree = TemplatesPanel.this.nodeTree; 
    WorkspaceJob j = new WorkspaceJob("Edit template "+node.getUserVisibleName()){
      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        AtomicBoolean started = new AtomicBoolean(node.getTreeDB()
            .startTransaction("Change template '" + node.getId() + "'(" + node.getType() + ")"));
        if (started.get()) {
          Map<String, String> labels = new HashMap<>(ElementViewPanel.getDefaultLabels());
          labels.put("idLabel", "Template Id:");
          EclipseAPI.getDisplay().asyncExec(new Runnable(){
          
            @Override
            public void run() {
              if(isDisposed || nodeTree.getControl().isDisposed()) {
                if(started.get()) {
                  node.getTreeDB().rollback();
                  started.set(false);
                }
                return;
              }
              EditElementDialog eed =
                  new EditElementDialog(
                      nodeTree.getTree().getShell(),
                      node,
                      "Edit template '" + node.getId() + "'(" + node.getType() + ")",
                      true,
                      labels);
              eed.setTransactionIgnore(true);
              boolean isCancel = EditElementDialog.CANCEL == eed.open();
              WorkspaceJob ja = new WorkspaceJob("Handle template edit results "+node.getUserVisibleName()){

                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) {
                  try {
                    if(node == null || isCancel) {
                      return Status.CANCEL_STATUS;
                    }
                    else {
                      if(started.get())
                      {
                        started.set(false);;
                        node.getTreeDB().commit();
                      }
                      EclipseAPI.getDisplay().asyncExec(new Runnable(){
                      
                        @Override
                        public void run() {
                          if(isDisposed || nodeTree.getControl().isDisposed()||node == null)
                            return;
                          nodeTree.refresh(node);
                        }
                      });
                    }
                    return Status.CANCEL_STATUS;
                  }finally {
                    if(started.get())
                      node.getTreeDB().rollback();
                  }
                }
              };
              if(!isCancel)
                ja.schedule();
              else
              {
                if(started.get()) {
                  node.getTreeDB().rollback();
                  started.set(false);
                }
              }
            }
          });
        }
        return Status.OK_STATUS;
      }
    };
    j.schedule();
  }

  protected ILabelProvider getLabelProvider() {
    return lprovider;
  }

  public void setTreeDB(TreeDB db2) {
    if (db != null) db.removeListener(this);
    db = db2;
    if (db != null && !db.getListeners().contains(this)) db.addListener(this);
    final TreeViewer nodeTree = TemplatesPanel.this.nodeTree;
    
    EclipseAPI.getDisplay().asyncExec(new Runnable(){
        
        @Override
        public void run() {
          if( isDisposed || nodeTree.getControl().isDisposed() || db2 == null)
            return;
          nodeTree.setInput(db2);
          nodeTree.refresh(true);
          if (db2 != null) updateTemplatesList(db2); 
        }
      });
  }

  @Override
  public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent, Moment m, byte flag) {}

  @Override
  public void deleted(TreeNode nodeDesc, byte flag, Moment m) {
    if(m == Moment.BEFORE)
      return;
    if (db != null
        && nodeDesc.getWorkParentUUID().equals(db.getOrCreateTemplatesFolder().getUUId())) {
        final TreeViewer nodeTree = TemplatesPanel.this.nodeTree;
        EclipseAPI.getDisplay().asyncExec(new Runnable(){
        
          @Override
          public void run() {
            if(isDisposed || nodeTree.getControl().isDisposed())
              return;
            nodeTree.refresh();
            clearSelection(createt);
          }
        });
    }
  }
  @Override
  public void beforeCreated(NodeDesc node, byte flag) {
  
  }
  @Override
  public void created(TreeNode node, byte flag) {
    if (db != null && node.getWorkParentUUID().equals(db.getOrCreateTemplatesFolder().getUUId())) {
      final TreeViewer nodeTree = TemplatesPanel.this.nodeTree;
      final Button setsel = TemplatesPanel.this.setsel; 
      EclipseAPI.getDisplay().asyncExec(new Runnable(){
        @Override
        public void run() {
          if(isDisposed || nodeTree.getControl().isDisposed())
            return;
          nodeTree.refresh();
          clearSelection(setsel);
        }
      });
    }
  }

  public void setSelectedType(String tp) {
    filteredType = tp;
    if(getTypesList(db)!=null)
	    for (int i = 0; i < types.size(); i++)
	      if (types.get(i).equals(filteredType)) {
	        templlist.select(i);
	        nodeTree.refresh();
	        break;
	      }
  }

  public Composite getComposite() {
    return newPar;
  }

  @Override
  public void changed(
      TreeNode node, ChangesSet<Attribute> params, Moment m, byte flag) {
    if (m == Moment.AFTER && node.getUUId().equals(node.getTreeDB().getOrCreateTemplatesFolder().getUUId())) {
      if (params.contains(TreeDB.ACTIVE_TEMPLATES_ATTRIBUTE)) {
        setTreeDB(node.getTreeDB());
        //updateTemplatesList(node.getTreeDB());
        //nodeTree.refresh();
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void changed(List<TreeNodeChangeEvent> changes) {
    //TODO remade
    for (TreeNodeChangeEvent ce : changes) {
      byte flag = ce.getFlag();
      switch (ce.getType()) {
        case DELETE:
          if(ce.getMoment() == Moment.AFTER)
            deleted(ce.getTreeNodeTarget(), flag, ce.getMoment());
          break;
        case CREATE:
          if(ce.getMoment() == Moment.AFTER)
            created(ce.getTreeNodeTarget(), flag);
          break;

        case MOVE:
          if(ce.getMoment() == Moment.AFTER)
            moved(
              ce.getTreeNodeTarget(),
              (TreeNode) ce.getArguments()[0],
              (TreeNode) ce.getArguments()[1],
              Moment.AFTER, flag);
          break;
        case CHANGE:
          if(ce.getMoment() == Moment.AFTER)
            changed(
              ce.getTreeNodeTarget(),
              (ChangesSet<Attribute>) ce.getArguments()[0],
              Moment.AFTER,
              flag);
          break;
        default:
          break;
      }
    }
  }

  @Override
  public void childrenOrderChanged(TreeNode node, byte flag) {}
}
