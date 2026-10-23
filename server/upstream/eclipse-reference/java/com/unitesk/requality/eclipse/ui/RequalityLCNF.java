package com.unitesk.requality.eclipse.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.navigator.CommonNavigator;
import org.eclipse.ui.navigator.CommonViewer;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.ITabbedPropertySheetPageContributor;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;

import com.unitesk.requality.core.IFSIdProvider;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.ITreeNodeProvider;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventFSId;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.ISelectionAdapter;
import com.unitesk.requality.core.selection.ISelectionComparer;
import com.unitesk.requality.core.selection.IUIStateChangeListener;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.tree.AAllTreeStateChangeHandler;
import com.unitesk.requality.core.tree.IStateChangeHandler;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.editors.browser.LocationRequirementConverter;
import com.unitesk.requality.eclipse.editors.browser.RequirementLocationConverter;
import com.unitesk.requality.eclipse.handlers.LightLinks;
import com.unitesk.requality.eclipse.ui.cnf.RequalitySorter;
import com.unitesk.requality.eclipse.ui.cnf.SelectionFromSource;
import com.unitesk.requality.eclipse.views.GRoot;
import com.unitesk.requality.eclipse.views.PrefixAndTreeNode;
import com.unitesk.requality.eclipse.views.RequalityContentLinksProvider;
import com.unitesk.requality.eclipse.views.RequalityContentLinksProvider.TestNodeLinks;
import com.unitesk.requality.eclipse.views.RequalityContentLinksProvider.TestNodeLinksGroup;
import com.unitesk.requality.marker.LinksDecorator;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.CoreUtils;

public class RequalityLCNF extends CommonNavigator implements ITabbedPropertySheetPageContributor,
  ISelectionProvider, IFSIdProvider, ISelectionAdapter, ISelectionComparer{
  public static final String ID = "com.unitesk.requality.views.RequalityLinksExplorer";
  private static byte refdir = 3;
  private boolean updating = false;
  private CommonViewer cv = null;
  private FSId fsid;
  private LinksDecorator decorator;
  private ImageContainer ih = new ImageContainer();
  private static Map<Long, RequalityLCNF> activeNaivgators = new ConcurrentHashMap<>();
  
  //TODO memleak, needs to be cleared or removed on treedb listener and actual node delete
  public Pair<Map<String, TreeNode[]>, Map<String, TreeNode[]>> colorNodes = null;

  public RequalityLCNF() {
    super();
    fsid = SelectionManager.getOne().registerNewFSId(EventSource.LCNF, this, true);
    SelectionManager.getOne().registerSelectionConverter(fsid, EventSource.EDITOR, RequirementLocationConverter.getOne(false));
    activeNaivgators.put(fsid.getId(), this);
  }

  public static Map<Long, RequalityLCNF> getActiveNaivgators() {
    return activeNaivgators;
  }

  private synchronized void enableTreeListener() {
    if(selDisables>0)
      selDisables--;
    if(selDisables==0)
      getCommonViewer().addSelectionChangedListener(treeSelection);
  }
  private synchronized void disableTreeListener() {
    if(selDisables==0)
      getCommonViewer().removeSelectionChangedListener(treeSelection);
    selDisables++;  
  }
  
  private int selDisables = 0;
  @Override
  protected CommonViewer createCommonViewerObject(Composite aParent) {
    cv =
        new CommonViewer(getViewSite().getId(), aParent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI);
    cv.setAutoExpandLevel(3);
    cv.setContentProvider(new RequalityContentLinksProvider());
    final ILabelProvider base1 = WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider();
    final ILabelProvider base = new ILabelProvider(){
    
      @Override
      public void removeListener(ILabelProviderListener listener) {
        base1.removeListener(listener);
      }
    
      @Override
      public boolean isLabelProperty(Object element, String property) {
        return base1.isLabelProperty(element, property);
      }
    
      @Override
      public void dispose() {
        base1.dispose();
      }
    
      @Override
      public void addListener(ILabelProviderListener listener) {
        base1.addListener(listener);
      }
    
      @Override
      public String getText(Object element) { 
        if (element instanceof PrefixAndTreeNode)
          return ((PrefixAndTreeNode)element).toString();
        return base1.getText(element);
      }
    
      @Override
      public Image getImage(Object element) {
        if (element instanceof PrefixAndTreeNode)
          return getImage(((PrefixAndTreeNode)element).getNode());
        return base1.getImage(element);
      }
    };
    cv.addDoubleClickListener(
        new IDoubleClickListener() {

          @Override
          public void doubleClick(DoubleClickEvent event) {
            if (updating) return;
            //SelectionManager.getOne().removeGenericListener(fsid, list);
            try {
              updating = true;
              StructuredSelection s = (StructuredSelection) event.getSelection();
              if (s != null && !s.isEmpty()) {
                Object o = s.getFirstElement();
                TreeNode node = null;
                if(o instanceof PrefixAndTreeNode)
                  node = ((PrefixAndTreeNode)o).getNode();
                if (o instanceof TreeNode) {
                  node = (TreeNode) o;
                } else if (o instanceof TestNodeLinks) node = ((TestNodeLinks) o).getTarget();
                TreeNode[]sel = null;
                if (node != null) {
                  sel = new TreeNode[] {node};
                }else
                  sel = new TreeNode[] {};
                FSId fs = SelectionManager.getOne().getFirstActiveSource(EventSource.EXPLORER);
                if(fs!=null){
                  if(fs instanceof EventFSId){
                      fs = ((EventFSId)fs).getBaseId();
                  }
                  SelectionManager.getOne().updateSelection(SourcePriority.EDITOR, fs,  sel);
//                  SelectionManager.getOne().changeActiveSource(EventSource.EXPLORER, fs, true);
                }else {
                  SelectionManager.getOne().updateSelection(SourcePriority.EDITOR, fsid, sel);
  //                SelectionManager.getOne().changeActiveSource(EventSource.EXPLORER, this, true);
                }
              }
            }finally {
              updating = false;
              //SelectionManager.getOne().addGenericListener(fsid, list);
            }
          }
        });
    decorator = new LinksDecorator(this, node);

    cv.setLabelProvider(
        new DecoratingLabelProvider(base, decorator) {

          @Override
          public void removeListener(ILabelProviderListener listener) {
            base.removeListener(listener);
          }

          @Override
          public boolean isLabelProperty(Object element, String property) {
            return true;
          }

          @Override
          public void dispose() {
            base.dispose();
          }

          @Override
          public void addListener(ILabelProviderListener listener) {
            base.addListener(listener);
          }

          @Override
          public String getText(Object element) {
            TreeNode node = null;
            boolean hasPrefix = (element instanceof PrefixAndTreeNode)&&!((PrefixAndTreeNode)element).getNotNullPrefix().isEmpty();
            if(element instanceof TreeNode)
              node = (TreeNode) element;
            else
              if(element instanceof ITreeNodeProvider)
                node = ((ITreeNodeProvider)element).getNode();
            String ext = "";
            if(!hasPrefix&&node!=null&&!node.isSameTree(RequalityLCNF.this.getRootTreeNode()))
              ext = node.getTreeDB().getProjectName()+"::";
            String baseText = base.getText(element);
            if (!baseText.equals("")) return ext+baseText;
            if(element == null)
              return "NULL";
            String els = element.toString();
            if(els.contains("[_prefix]"))
              els = els.replace("[_prefix]", ext);
            else
              if(!els.startsWith(ext))
                return ext+els;
            return els;
          }

          @Override
          public Image getImage(Object element) {
            if (element instanceof PrefixAndTreeNode)
              return base.getImage(((PrefixAndTreeNode)element).getNode());
            if (element instanceof TestNodeLinks) return null;
            if (element instanceof TestNodeLinksGroup) {
              String imgup = "icons/uparrow.png", imgdown = "icons/downarrow.png";
              TestNodeLinksGroup link = (TestNodeLinksGroup) element;
              return ih.getImage(Display.getCurrent(),link.isUp() ? imgup : imgdown);
            }
            return base.getImage(element);
          }
        });
    SelectionManager.getOne().registerSelectionAdapter(fsid, this);
    SelectionManager.getOne().registerSelectionComparer(fsid, this);
    SelectionManager.getOne()
        .addSourceListener(fsid.getEventSource(),
          fsid, 
            selfListener);
    // cv.setLabelProvider(base);

    return cv;
  }
  
  private Random r = new Random();
  
  private RunnableWithMonitor focusUpdateJob = null;
  
  private TreeNode newFocus = null;
  private SelectionRecord newSelection = null;
  private FSId fsidSource = null;
  
  //private SelectionRecord lastFocusCall = null;
  private boolean jobCanceled = false;
  IUIStateChangeListener selfListener = new IUIStateChangeListener() {
    @Override
    public void newActiveNodes(FSId source, final SelectionRecord record) {

      jobCanceled = false;
      boolean externalFocusNull = false;
      TreeNode nextFocus = null;
      SelectionRecord nextRecord = newSelection;
      if(source.getRootId().isSameAs(fsid))//only change selection
      {
        nextRecord = record;
        fsidSource = source;
        nextFocus = node;
      } else 
      {
        if(!record.isEmpty())
          nextFocus = record.getFirstNode();
        externalFocusNull = nextFocus == null;
        nextRecord = SelectionRecord.EMPTY;
        fsidSource = null;
      }
      if(nextFocus!=null) {
        initNewFocusCleaner(nextFocus);
      }
      synchronized(RequalityLCNF.this) {
        newFocus = nextFocus;//update target tree node for job
        newSelection = nextRecord;
        if(focusUpdateJob == null) {
          if(nextFocus!=null)
            focusUpdateJob = new RunnableWithMonitor() {
              @Override
              public boolean shouldRerun() {
                if(jobCanceled)
                {
                  focusUpdateJob = null;
                  return false;
                }
                synchronized(RequalityLCNF.this) {
                  if(newFocus!=null && newFocus.getTreeDB().isDisposed())
                    return false;
                }
                return true;
              }
              @Override
              public long getTimeToReshedule() {
                return 500;
              };
              @Override
              public boolean shouldRun() {
                if(newSelection!=null)
                  return !CoreUtils.busyCondition(newSelection);
                synchronized(RequalityLCNF.this) {
                  if(newFocus!=null)
                    return !CoreUtils.busyCondition(newFocus.getTreeDB());
                }
                jobCanceled = true;
                focusUpdateJob = null;
                return false;
              };
              @Override
              public void run(com.unitesk.requality.core.operation.IProgressMonitor pm) {
                
                EclipseAPI.getDisplay().asyncExec(new Runnable() {
                  
                  @Override
                  public void run() {
                    synchronized(RequalityLCNF.this) {
                      SelectionManager.getOne()
                        .removeSourceListener(fsid.getEventSource(),
                          fsid, 
                          selfListener);
                      disableTreeListener();
                      try {
                        try {
                          if (cv.getControl().isDisposed()) {
                            return;
                          }
                          updating = true;
                          try {
                            boolean noNew = false;
                            RequalityLCNF.this.node = newFocus;
                            if (newFocus!=null && newFocus!=getRootTreeNode()){
                              cv.setInput(new GRoot(newFocus));
                              decorator.setNode(newFocus);
                              RequalityLCNF.this.node = newFocus;
                              RequalityLCNF.this.nodes = nodes;
                              setSelection(new StructuredSelection(new SelectionFromSource(newFocus, source)));
                              if(lightLinks!=null)
                                lightLinks.updateNodeColoring(RequalityLCNF.this);
                              else
                                RLogger.logDebug("Info::No light links were availiable. LCNF was restared");
                              //SelectionManager.getOne().setActiveNodes(Collections.EMPTY_MAP,Collections.EMPTY_MAP, EventSource.LCNF);
                            }
                            else {
                              if(newSelection == null || newSelection.fullSelection.isEmpty()) {
                                if(newFocus == null)
                                  cv.setInput(null);
                                setSelection(StructuredSelection.EMPTY);
                              }else {
                                Collection<TreeNode> nodes = newSelection.fullSelection;
                                //TODO init on target change or update
                                Map<TreeNode, PrefixAndTreeNode> prefixes = new HashMap<>();
                                fillPrefixes(cv, prefixes);
                                List<Object> newSel = new LinkedList<>();
                                for(TreeNode node:nodes)
                                {
                                  PrefixAndTreeNode pren = prefixes.get(node);
                                  if(pren!=null)
                                    newSel.add(pren);
                                }
                                setSelection(new SelectionFromSource(newSel.toArray(), fsidSource));
                              }
                            }
        //                      if(noNew){
        //                        cv.setInput(null);
        //                        decorator.setNode(null);
        //                        RequalityLCNF.this.node = null;
        //                        RequalityLCNF.this.nodes = null;
        //                        setSelection(SelectionFromSource.getEmpty(source));
        //                        if(lightLinks!=null)
        //                          lightLinks.updateNodeColoring(RequalityLCNF.this);
        //                        else
        //                          RLogger.logDebug("Info::No light links were availiable. LCNF was restared");
        //                      }
                          }finally{
                            updating = false;
                          }
                        }finally {
                        } 
                      }finally {
                        focusUpdateJob = null;
                        SelectionManager.getOne()
                        .addSourceListener(fsid.getEventSource(),
                          fsid, 
                            selfListener);
                        enableTreeListener();
                      }
                    }
                  }
                private void fillPrefixes(TreeItem ti, Map<TreeNode, PrefixAndTreeNode> prefixes) {
                  Object tid = ti.getData();
                  if(tid instanceof PrefixAndTreeNode) {
                    PrefixAndTreeNode pref = (PrefixAndTreeNode) tid;
                    prefixes.put(pref.getNode(), pref);
                  }
                  for(TreeItem chi: ti.getItems())
                    fillPrefixes(chi, prefixes);
                }
                private void fillPrefixes(
                    CommonViewer cv, Map<TreeNode, PrefixAndTreeNode> prefixes) {
                  for(TreeItem ti:cv.getTree().getItems())
                    fillPrefixes(ti, prefixes);
                  }
                });
              }
            };
          if(nextFocus == null && externalFocusNull) {
            EclipseAPI.getDisplay().asyncExec(new Runnable() {
              @Override
              public void run() {
                if(cv == null || cv.getControl().isDisposed())
                   return;
                if(newFocus == null)
                  cv.setInput(null);
                if(newSelection.fullSelection.isEmpty())//TODO more checks?
                  cv.setSelection(StructuredSelection.EMPTY);
              }
            });
          }else
            JobManager.jm.startJob("update requality link cnf "+(newFocus!=null?newFocus.getUserVisibleName():nodesToString(newSelection)), focusUpdateJob, false);
        }
      }
    }
    private String nodesToString(SelectionRecord mnodes) {
      if(mnodes == null)
        return "";
      StringBuilder sb = new StringBuilder();
      for(Entry<String, Collection<TreeNode>> en:mnodes.selectionByType.entrySet()) {
        sb.append(en.getKey());
        sb.append(":");
        sb.append(Arrays.toString(en.getValue().toArray()));
      }
      return sb.toString();
    }

  };
  private static TreeState[]STATE_FOCUS_CLEAR = new TreeState[] {TreeState.HALTED, TreeState.RESTART};
  private IStateChangeHandler scha = null;
  private TreeStateManager tsm = null;
  private void initNewFocusCleaner(TreeNode node) {
    if(scha == null) {
      scha = new AAllTreeStateChangeHandler() {
        @Override
        public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState,
          TreeState state) {
          if(newFocus!=null && lname.equals(newFocus.getTreeDB().getLocalName()))
            newFocus = null;//TODO clear also?
            selfListener.newActiveNodes(FSId.UNKNOWN, SelectionRecord.EMPTY);//force component reload
        }
      };
      tsm = node.getTreeDB().getTracker().getTreeStateManager();//multiple trackers?
      tsm.addStateHook("*", STATE_FOCUS_CLEAR, scha);
    }
  }
  //old
  //return !newSelection.isSameWith(oldRec) || !fsid.isSameAs(this.fsid);
  @Override
  public boolean selectionChanged(FSId source, SelectionRecord record, SelectionRecord oldRecord) {
    if(source.getRootId().isSameAs(fsid))
      return oldRecord==null&&record!=null || oldRecord!=null && !oldRecord.isSameWith(record);
    return record==null || record.isEmpty() || node == null || record.getFirstNode()!=null && !record.getFirstNode().equals(node) ||  !oldRecord.isSameWith(record); 
  }
  @Override
  public Pair<SelectionRecord, SelectionRecord> adaptChange(FSId selectionSource, SelectionRecord visRecord, SelectionRecord oldVisRecord, SelectionRecord storedSel, SelectionRecord oldStoredSel){
    return new Pair<>(adaptChange(selectionSource, visRecord, oldVisRecord),
                       adaptChange(selectionSource, storedSel, oldStoredSel));
  }
  public SelectionRecord adaptChange(FSId selectionSource, SelectionRecord record, SelectionRecord oldRecord) {
    if(record == null)
      return null;
    if(selectionSource.isSameAs(fsid)) 
      return record;
    SelectionRecord retSel = adoptSelection(record);
    if(retSel == null || retSel.selectionByType.size() != 1) {
      return oldRecord;
    }
    final Map<String, Collection<TreeNode>> mnodes = retSel.selectionByType;
    if(mnodes == null || mnodes.size() == 0)
      return oldRecord;
    Map<String, Collection<TreeNode>> nodes = mnodes;
    if(nodes.size() > 0) {
      Collection<TreeNode>nds = nodes.values().iterator().next();
      if(nds != null && nds.size() > 0) {
        Object oldInp = cv.getInput();
    //    newFocus = nds.iterator().next();
        if(oldInp!=null && oldInp instanceof GRoot) {
          GRoot oldR = (GRoot) oldInp;
        }
      }
    }
    return record;
  }
  //TODO rewrite to less memory-consuming one
  private SelectionRecord adoptSelection(SelectionRecord mnodes) {
   //TODO check selection adoptation on lcnf
    return LocationRequirementConverter.convertMapSelection(mnodes);
  }
  @Override
  public String getContributorId() {
    return "com.unitesk.requality.views.RequalityPropertyContributor";
  }

  @Override
  public void createPartControl(Composite aParent) {
    super.createPartControl(aParent);
    getCommonViewer().setComparator(new RequalitySorter());
    enableTreeListener();
  }

  @Override
  public void dispose() {
    ih.dispose();
    super.dispose();
    SelectionManager.getOne()
        .removeSourceListener(fsid.getEventSource(), fsid, 
            selfListener);
    SelectionManager.getOne().unregisterInstance(fsid);
    disableTreeListener();
    activeNaivgators.remove(fsid.getId());
    if(scha!=null) {
      tsm.removeStateHook("*", STATE_FOCUS_CLEAR, scha);
      tsm = null;
    }
  };

  @SuppressWarnings("unchecked")
  @Override
  public Object getAdapter(@SuppressWarnings("rawtypes") Class adapter) {
    if (adapter == IPropertySheetPage.class) {
      // TODO null pointer cause??
      return new TabbedPropertySheetPage(this);
    }else if (adapter == IShowInSource.class){
      return new IShowInSource(){
      
        @Override
        public ShowInContext getShowInContext() {
          return new ShowInContext(getInitialInput(), getSelection());
        }
      };
    }
    return super.getAdapter(adapter);
  }
  @Override
  protected Object getInitialInput() {
    if (Activator.getDefault() != null) {
      SelectionRecord sel = SelectionManager.getOne().getSelection();
      if(sel!=null && sel.selectionByType.size() == 1) {//TODO check agains prev
        return sel.fullSelection;
      }
    }
    return super.getInitialInput();
  }

  public static void toggleReferenceDirection() {
    refdir++;
    if (refdir == 4) refdir = 1;
    for (RequalityLCNF cnf : activeNaivgators.values()) {
      cnf.getCommonViewer().refresh(true);
    }
  }

  public static int getReferenceDirection() {
    return refdir;
  }

//  private static Map<TreeDB, Set<String>> filter = new HashMap<>();
  private static String META_LINKS_FILTER="LINKS_EXPLORER_FILTER";
  public static Set<String> getFilters(TreeDB treeDB) {
    return treeDB.getMetaObject(META_LINKS_FILTER);
  }

  public static void setFilter(TreeDB treeDB, HashSet<String> hashSet) {
    treeDB.registerMetaObject(META_LINKS_FILTER, hashSet);
    for (RequalityLCNF c : activeNaivgators.values()) {
      c.getCommonViewer().refresh(true);
      c.getCommonViewer().refresh(c.getCommonViewer().getInput());
    }
  }

  public TreeNode getTreeSelectedNode(){
    Object o = this.getCommonViewer().getInput();
    if(o != null){
      if(PrefixAndTreeNode.class.isInstance(o))
        o = ((PrefixAndTreeNode)o).getNode();
      if(o instanceof TreeNode){
        TreeNode node = (TreeNode) o;
        return node;
      }
    }
    if(!(this.getInitialInput() instanceof TreeNode))
      return null;
    return (TreeNode) this.getInitialInput();
  }
  
  public static TreeNode getActiveTreeSelectedNode(){
    FSId fs = SelectionManager.getOne().getFirstActiveSource(EventSource.LCNF);
    if(fs!=null) {
        RequalityLCNF cnf = getActiveNaivgators().get(fs.getRootId().getId());
        if(cnf!=null)
          return cnf.getSelectedTreeNode();
      }
    return null;
  }
  public static TreeNode getActiveTreeRootNode(){
    FSId fs = SelectionManager.getOne().getFirstActiveSource(EventSource.LCNF);
    if(fs!=null) {
      RequalityLCNF cnf = getActiveNaivgators().get(fs.getRootId().getId());
      if(cnf!=null)
        return cnf.getRootTreeNode();
    }
    return null;
  }
  

  public TreeNode getSelectedTreeNode() {
    IWorkbenchWindow activeWorkbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    if (activeWorkbenchWindow == null) {
      return null;
    }

    ISelection selection = null;
    selection = this.getCommonViewer().getSelection();
    
    if (!(selection instanceof IStructuredSelection)) {
      return null;
    }

    IStructuredSelection selected = (IStructuredSelection) selection;
    if (selected.isEmpty()) {
      return null;
    }

    List<TreeNode> nodes = new ArrayList<>();
    @SuppressWarnings("unchecked")
    List<Object> objList = selected.toList();
    for (Object obj : objList) {
      if (obj instanceof PrefixAndTreeNode)
        obj = ((PrefixAndTreeNode)obj).getNode();
      if (obj instanceof TreeNode) {
        TreeNode node = (TreeNode) obj;
        nodes.add(node);
      }
      if (nodes.size() > 0) {
        return nodes.get(0);
      }
    }
    return null;
  }
  public TreeNode getSelectedOrRootTreeNode() {
    IWorkbenchWindow activeWorkbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    if (activeWorkbenchWindow == null) {
      return null;
    }
    TreeNode sel = getSelectedTreeNode();
    if(sel == null)
    {
      sel = getRootTreeNode(); 
    }
    return sel;
  }

  public TreeNode getRootTreeNode() {
    Object inp = cv.getInput();
    TreeNode sel = null;
    if(inp instanceof TreeNode)
      sel = (TreeNode) inp;
    if(inp instanceof GRoot)
      sel = ((GRoot)inp).getNode();
    return sel;
  }

//  public Map<String, TreeNode[]> getSelectedNodes() {
//    return SelectionManager.getOne().getSelection(fsid);
//  }
  private static void adaptObject(Object obj, List<TreeNode> nodes) {
    if (obj instanceof TestNodeLinksGroup)
    {
      TestNodeLinksGroup tgn = (TestNodeLinksGroup) obj;
      for(Object o:tgn.getLinks())
        adaptObject(o, nodes);
    }else
      if (obj instanceof TestNodeLinks)
      {
        TestNodeLinks tgn = (TestNodeLinks) obj;
        for(Object o:tgn.getChildren())
          adaptObject(o, nodes);
      }else{
        if (obj instanceof PrefixAndTreeNode) obj = ((PrefixAndTreeNode) obj).getNode();
        if (obj instanceof TreeNode) {
          TreeNode node = (TreeNode) obj;
          nodes.add(node);
        }
      }
  }
  Map<String, Collection<TreeNode>> nodes = null;
  TreeNode node = null;
  Map<String,TreeNode[]> oldNodes = null;
  private final ISelectionChangedListener treeSelection =
      new ISelectionChangedListener() {
        @Override
        public void selectionChanged(SelectionChangedEvent event) {
          Object esel = event.getSelection();
          if (esel instanceof IStructuredSelection) {
            SelectionManager.getOne().removeSourceListener(fsid.getEventSource(),fsid, selfListener);
            try {
              FSId base = null;

              IStructuredSelection ssel = (IStructuredSelection) esel;
              
//              ListIterator<SelectionFromSource> lit = selChanges.listIterator(selChanges.size());
              SelectionFromSource ssfs = null;
//              while(lit.hasPrevious()) {
//                SelectionFromSource sfs = lit.previous();
//                if(sfs==null)
//                  lit.remove();
//                else
                  if(ssel instanceof SelectionFromSource) {
                    ssfs = (SelectionFromSource) ssel;
                    base = ssfs.getBaseEvent();
                  }
//              }
              Iterator<Object> it = null;
              if(ssfs!=null) {
                it = ssfs.iterator();
              }else
                it = ssel.iterator();
              TreeDB db2 = null;
              List<TreeNode> nodes = new LinkedList<>();
              while (it.hasNext()) {
                Object obj = it.next();
                if (obj == null) continue;
                if(obj instanceof PrefixAndTreeNode)
                  obj = ((PrefixAndTreeNode)obj).getNode();
                if (obj instanceof TreeNode) {
                  TreeNode node = (TreeNode) obj;
                  if (db2 == null){
                    db2 = node.getTreeDB();
                    if(db2.isVirtual())
                      db2 = Instantiator.getRootTree(db2);
                  }
                  if (db2 != Instantiator.getRootTree(node.getTreeDB())) continue;
                  nodes.add(node); 
                }
              }
              FSId evid = fsid;
              if(base!=null)
                evid = EventFSId.create(evid, base);
              SelectionRecord sr = null;
              if(nodes.size()>0) {
                SelectionManager.getOne().updateSelection(SourcePriority.EDITOR, evid, nodes.toArray(new TreeNode[0]));
                sr = new SelectionRecord(nodes.toArray(new TreeNode[0]));
              }else {
                SelectionManager.getOne().updateSelection(SourcePriority.EDITOR, evid, SelectionManager.NO_NODES);
                sr = new SelectionRecord(SelectionManager.NO_NODES);
              }
              selfListener.newActiveNodes(fsid, sr);
            }finally {
              SelectionManager.getOne().addSourceListener(fsid.getEventSource(), fsid, selfListener);
            }
          }

        }
      };


  @Override
  public ISelection getSelection() {
    TreeNode tn = getSelectedTreeNode();
    if(tn!=null)
      return new StructuredSelection(tn);
    return StructuredSelection.EMPTY;
  }

 // private LinkedList<SelectionFromSource> selChanges = new LinkedList<>();
  public LightLinks lightLinks;
  @Override
  public void setSelection(ISelection selection) {
    disableTreeListener();
    try {
      if(selection instanceof SelectionFromSource) {
        SelectionFromSource sfs = (SelectionFromSource)selection;
      //  selChanges.add(sfs);
        cv.setSelection(sfs.toSelection(), true);
      }else
        cv.setSelection(selection, true);
      //cv.setSelection(selection);
    }finally {
      enableTreeListener();
    }
  }

  @Override
  public FSId getFSId() {
    return fsid;
  }

  @Override
  public boolean isSelectionSource() {
    return true;
  }

  @Override
  public void addSelectionChangedListener(ISelectionChangedListener listener) {
    getCommonViewer().addSelectionChangedListener(listener);
  }

  @Override
  public void removeSelectionChangedListener(ISelectionChangedListener listener) {
    getCommonViewer().removeSelectionChangedListener(listener);
  }

}