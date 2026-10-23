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
package com.unitesk.requality.eclipse.ui.cnf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IWorkbenchPreferenceConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.navigator.CommonNavigator;
import org.eclipse.ui.navigator.CommonViewer;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.ITabbedPropertySheetPageContributor;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;

import com.unitesk.requality.core.IFSIdProvider;
import com.unitesk.requality.core.SharedConsts;
import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.exception.NullParentException;
import com.unitesk.requality.core.filter.ITreeNodeFilter;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventFSId;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.IUIStateChangeListener;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.sorting.SortingSettings;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.editors.EditorUtils;
import com.unitesk.requality.eclipse.editors.browser.RequirementLocationConverter;
import com.unitesk.requality.eclipse.ui.cnf.filters.AbstractNodeFilter;
import com.unitesk.requality.eclipse.views.RequalityContentProvider;
import com.unitesk.requality.eclipse.views.RequalityPreferences;
import com.unitesk.requality.marker.RequalityDecorator;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.Const;
import com.unitesk.requality.tool.CoreUtils;

public class RequalityCNF extends CommonNavigator
    implements ITabbedPropertySheetPageContributor,  ISchedulingRule, IFSIdProvider {
  public static Long TIME_SKIP = 50L;
  public static boolean shiftPressed = false, altPressed = false;
  public static final String ID = "com.unitesk.requality.views.RequalityExplorer";
  public static RequalityCNF activeNavigator;
  private FSId fsid;
  private FSId lastSource = null;
  private SelectionRecord lastNodes = null;
  //private List<Object> lastObjects = null;
  
  private boolean fireSelectionChange = true;
  private boolean recAdd(TreeNode node, List<TreeNode> list) {
    if (list.contains(node)) return false;
    TreeNode nde = node;
    while (nde != null) {
      nde = nde.getShownParent();
      if (nde != null && list.contains(nde)) return false;
    }
    list.add(node);
    return true;
  }
  private static Set<String> shownNames = new HashSet<>();
  static{
    shownNames.add(TreeNode.ATTR_LABEL);
    shownNames.add(TreeDB.INDEX_ATTRIBUTE);
    shownNames.add(CheckerContainer.VATTR_ERRORS);
    shownNames.add(SortingSettings.SORT_METHOD);
    shownNames.add(TreeNode.ATTR_NAME);
    shownNames.add(TextNode.ATTR_TNTYPE);
    shownNames.add(TreeNode.ATTR_SHOWN_CHILDREN);
  }
  
  public static boolean isShown(String s) {
    if(shownNames.contains(s))
      return true;
    for(RequalityDecorator rd:RequalityDecorator.getAll())
      {
        Set<String> atrs = rd.getRelatedKeys();
        if(atrs!=null && atrs.contains(s))
          return true;
      }
    return false;
  }
  
  //private LinkedList<SelectionFromSource> selChanges = new LinkedList<>();
  private void setSelection(StructuredSelection sel, boolean intern) {
    disableTreeListener();
    try {
      if(sel instanceof SelectionFromSource) {
        SelectionFromSource sfs = (SelectionFromSource)sel;
        //selChanges.add(sfs);
        getCommonViewer().setSelection(sfs.toSelection(), intern);
      }else
        getCommonViewer().setSelection(sel, intern);
    }finally {
      enableTreeListener();
    }
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
  //private TreeDB db = null;
  
  private int selDisables = 0;
  private final IUIStateChangeListener uiListener = new IUIStateChangeListener() {
    
    
    @Override
    public void newActiveNodes(FSId source, SelectionRecord selr) {

      if(source.relatesToSource(EventSource.EDITOR)) {
        if(Const.NO_EXPLORER_EDITOR_LINK)
          return;
        //SelectionManager.getOne().changeActiveSource(fsid.sourceClass,fsid, true);//send change to another parts
        //return;
      }
      if(selectionRecordChanged(selr, lastNodes) || !source.getRootId().isSameAs(fsid))
      {
        schelduleSelectionUpdate(source, selr);
      }
    }

  };

  private boolean selectionRecordChanged(SelectionRecord nodes, SelectionRecord nodesNow) {
    return SelectionManager.selectionIsChanged(nodes, nodesNow);
  }
  @Override
  public void selectReveal(ISelection sel) {
    synchronized (this) {
      disableTreeListener();
      try {
        if(sel instanceof SelectionFromSource) {
            SelectionFromSource sfs = (SelectionFromSource) sel;
            boolean change = false;
            Collection<TreeNode> convNodes = Collections.emptyList();
            if(sfs.getBaseEvent() == null || !sfs.getBaseEvent().getRootId().isSameAs(fsid)) {
              Iterator<Object> it = sfs.iterator();
              if(it.hasNext()) {
                convNodes = new LinkedList<>();
                while(it.hasNext()) {
                  Object o = it.next();
                  if(o instanceof TreeNode) {
                    convNodes.add((TreeNode)o);
                  }
                }
                convNodes = SelectionManager.getOne().getConverted(sfs.getBaseEvent(), fsid, convNodes);
              }
              if(convNodes!=null)
                super.selectReveal(new SelectionFromSource(convNodes.toArray(), sfs.getBaseEvent()));
            }else
              super.selectReveal(sel);
          }else
            super.selectReveal(sel);
      }finally {
        enableTreeListener();
      }
    }
  }
  private RunnableWithMonitor selectionUpdateRunnable = null;
  protected void schelduleSelectionUpdate(FSId source, SelectionRecord selr) {
    synchronized (RequalityCNF.this) {
      lastSource = source;
      lastNodes = selr;//TODO clone
      if(selectionUpdateRunnable == null) {
        selectionUpdateRunnable = new RunnableWithMonitor() {
          @Override
          public boolean shouldRerun() {
            return true;
          }
          @Override
          public boolean shouldRun() {
            boolean sr = !CoreUtils.busyCondition(selr);
            return sr;
          }
          @Override
          public void run(com.unitesk.requality.core.operation.IProgressMonitor pm) {
            try {
              if ((selr == null || selr.fullSelection.isEmpty())) {
                EclipseAPI.getDisplay().syncExec(new Runnable() {
    
                  @Override
                  public void run() {
                    synchronized (RequalityCNF.this) {
                      if(selectionRecordChanged(lastNodes, selr) || source!=lastSource) {
                        //rescheldule
                        selectionUpdateRunnable = null;
                        schelduleSelectionUpdate(lastSource, lastNodes);//TODO check
                        return;
                      }
                    }
                    try {
                      fireSelectionChange = false;
                      disableTreeListener();
                      EclipseAPI.getDisplay().syncExec(new Runnable() {
                        @Override
                        public void run() {
                        try {
                          fireSelectionChange = false;
                          disableTreeListener();
                          if(selr==null || selr.fullSelectionObjects.isEmpty())
                            setSelection(SelectionFromSource.getEmpty(source), true);
                          else
                            setSelection(new SelectionFromSource(selr.fullSelectionObjects.toArray(new Object[0]), source), true);
                        }finally {
                          enableTreeListener();
                          fireSelectionChange = true;
                        }
                      }});
                    }finally {
                      enableTreeListener();
                      fireSelectionChange = true;
                    }
                }
              });
                
              return;
            } else {
              if (selr.fullSelection.isEmpty() && selr.fullSelectionObjects.isEmpty()) {
                  EclipseAPI.getDisplay().syncExec(new Runnable() {
                      @Override
                      public void run() {
                      try {
                        fireSelectionChange = false;
                        disableTreeListener();
                        setSelection(SelectionFromSource.getEmpty(source), true);
                      }finally {
                        enableTreeListener();
                        fireSelectionChange = true;
                      }
                  }});
                return;
              }
              TreeDB db = null;
              LinkedList<Object> sel = new LinkedList<>();
              for(TreeNode node:selr.fullSelection) {
                //db.addGUIListener(guiTreeChangeListener);
                List<TreeNode> updated = new ArrayList<>();
                TreeDB adb = node.getTreeDB();
                if(adb.isVirtual())
                  adb = Instantiator.getRootTree(adb);
                TreeNode parentElement = null;
                try{
                  parentElement = node.getShownParentExc();
                  if ((parentElement == null || !recAdd(parentElement, updated))&&!node.isRoot()) 
                    continue;
                }catch(NullParentException e) {
                  continue;
                }
                //refresh(node.getParent(true), true);
                sel.add(node);
              }
              if(selr.fullSelectionObjects!=null && selr.fullSelectionObjects.size()>0)
              {
                LinkedList<Object> lo = new LinkedList<>(selr.fullSelectionObjects);
                Collections.reverse(lo);
                for(Object o: lo)
                  sel.addFirst(o);
              }
              EclipseAPI.getDisplay().syncExec(new Runnable() {
                @Override
                public void run() {
                  synchronized (RequalityCNF.this) {
                    if(selectionRecordChanged(lastNodes, selr) || source!=lastSource) {
                    //rescheldule
                      selectionUpdateRunnable = null;
                      schelduleSelectionUpdate(lastSource, lastNodes);//TODO check
                      return;
                    }
                  }
                  if(!source.getRootId().isSameAs(fsid))
                    for(Object node:sel)
                      if(node instanceof TreeNode)
                        getCommonViewer().expandToLevel(
                          EditorUtils.formPathOrElement(
                            getCommonViewer().getContentProvider(), getCommonViewer().getInput(), (TreeNode)node), 1);
    //                    SelectionManager.getOne().removeSourceListener(fsid.getEventSource(),fsid, uiListener);
                  fireSelectionChange = false;
                  try {
                    disableTreeListener();
                    setSelection(new SelectionFromSource(sel, source), true);
                  }finally {
                    enableTreeListener();
    //                      FSId evid = fsid;
    //                      if(source != fsid && source!=null)
    //                        evid = EventFSId.create(evid, source);
                    fireSelectionChange = true;
                  }
                }
              });
            }
          }finally {
            synchronized (RequalityCNF.this) {
              if(selectionUpdateRunnable == this)
                selectionUpdateRunnable = null;
            }
          }
        }
      };
      JobManager.jm.startJob("update requality explorer selection ["+selr.toString()+"]", selectionUpdateRunnable, false);
     }
   }
  }
  private final ISelectionChangedListener treeSelection =
      new ISelectionChangedListener() {
        @Override
        public void selectionChanged(SelectionChangedEvent event) {
          SelectionManager sman = SelectionManager.getOne();
          Object sel = event.getSelection();
          boolean fireChange = fireSelectionChange;
          boolean handleStructuredSelection = sel instanceof IStructuredSelection;
          boolean handlesNewSelection = false;
          if (handleStructuredSelection) {
            SelectionRecord sr = null;
            synchronized (RequalityCNF.this) {
              sman.removeSourceListener(fsid.getEventSource(), fsid, uiListener);
            }
            try {
              FSId eventBase = fsid;
              if(sel instanceof SelectionFromSource) {
                SelectionFromSource sfs = (SelectionFromSource) sel;
                if(sfs.getBaseEvent() == null || !sfs.getBaseEvent().getRootId().isSameAs(fsid))
                  fireChange = false;
                eventBase = sfs.getBaseEvent();
              }
              Pair<List<Object>, List<TreeNode>> selectionToObjects = convertToObjects((IStructuredSelection)sel);
              if(selectionToObjects == null)
                return;
              List<Object> selObjs = selectionToObjects.getFirst();
              List<TreeNode> nodes = selectionToObjects.getSecond();
//              if(/**!selectionNodesChanged(nodes, lastNodes) && **/!selectionObjectChanged(selObjs, lastNodes.fullSelectionObjects))
//                return;
              FSId evid = fsid;
              if(eventBase != null)
                evid = EventFSId.create(evid, eventBase);
              sr = new SelectionRecord(selObjs.toArray(new Object[0]), nodes.toArray(new TreeNode[0]));
//              if(sr.isSameWith(lastNodes) && (lastSource.isSameAs(eventBase)))
//                return;
              if( fireChange ) {
                //sman.updateSelectionObjects(evid, selObjs);
                sman.updateSelection(SourcePriority.EDITOR, evid, sr);
                
                //TODO temp to sync selection
                SelectionManager.getOne().
                  activateSource(SourcePriority.SOURCE_CHANGE, 
                  fsid.getEventSource(),
                  fsid, isSelectionSource(), true);
                
              }
              final FSId fevid = evid;
//              sel = getCommonViewer().getStructuredSelection();
//              Pair<List<Object>, List<TreeNode>> selectionToObjects2 = convertToObjects((IStructuredSelection)sel);
//              {
//                try{
//                  disableTreeListener();
//                  getCommonViewer().setSelection(new StructuredSelection(selObjs.toArray()));
//                }finally{
//                  enableTreeListener();
//                }
//              }
              //sman.changeActiveSource(evid.getEventSource(),evid, true);
              //    }
            }finally {
              if(!handlesNewSelection) {
                //TODO check sync between local selection and selManager info
//                getCommonViewer().removeSelectionChangedListener(treeSelection);
//                sel = getCommonViewer().getSelection();
//                setSelection(null, handlesNewSelection);
//                
//                getCommonViewer().addSelectionChangedListener(treeSelection);
              }
              sman.addSourceListener(fsid.getEventSource(), fsid, uiListener);
              //SelectionRecord srec = sman.getSelection(fsid);
              if(sr == null)
                sr = SelectionRecord.EMPTY;
              if(selectionRecordChanged(sr, lastNodes) || lastSource == null || lastSource!=fsid)
                schelduleSelectionUpdate(fsid, sr);
            }
          }
        }
      };
  private static boolean shown = false;
  public static Runnable activateFunction = null;

//  private boolean selectionObjectChanged(List<Object> selObjs, List<Object> first) {
//    if(selObjs == null)
//      return first!=null;
//    if(first == null)
//      return true;
//    if(selObjs.size()!=first.size())
//      return true;
//    Iterator<Object> it1 = selObjs.iterator(), it2 = first.iterator();
//    int i = 0;
//    while(it1.hasNext() && it1.next() == it2.next()) i++;
//    return i!=selObjs.size();
//  }
//  private boolean selectionNodesChanged(List<TreeNode> selObjs, List<TreeNode> first) {
//    if(selObjs.size()!=first.size())
//      return true;
//    Iterator<TreeNode> it1 = selObjs.iterator(), it2 = first.iterator();
//    int i = 0;
//    while(it1.hasNext() && it1.next() == it2.next()) i++;
//    return i!=selObjs.size();
//  }
  public RequalityCNF() {
    super();
    fsid = SelectionManager.getOne().registerNewFSId(EventSource.EXPLORER, this, true);
    SelectionManager.getOne().registerSelectionConverter(fsid, EventSource.EDITOR, RequirementLocationConverter.getOne(true));
//    if(activeNavigator!=null)
//      throw new RuntimeException("Only one reqcnf will exist");
    activeNavigator = this;
    //check engines
    if (!shown
        && Activator.getDefault()
            .getPreferenceStore()
            .getBoolean(RequalityPreferences.OPEN_NO_ENGINES_DIALOGS)) {
      shown = true;
    }
    if(activateFunction!=null)
      activateFunction.run();
  }

  protected Pair<List<Object>, List<TreeNode>> convertToObjects(IStructuredSelection sel) {
    IStructuredSelection ssel = (IStructuredSelection) sel;
    Collection<TreeNode> convNodes = null;
    if(sel instanceof SelectionFromSource) {
      SelectionFromSource sfs = (SelectionFromSource) sel;
      //handleStructuredSelection = false;
      Iterator<Object> it = sfs.iterator();
      convNodes = null;
      if(it.hasNext()) {
        convNodes = new LinkedList<>();
        while(it.hasNext()) {
          Object o = it.next();
          if(o instanceof TreeNode) {
            convNodes.add((TreeNode)o);
          }
        }
        convNodes = SelectionManager.getOne().getConverted(sfs.getBaseEvent(), fsid, convNodes);
        //handlesNewSelection = true;
      }
    }               
    if(convNodes==null && ssel.isEmpty())
      return null;
    //handlesNewSelection = true;
    Iterator it = null;
    if(convNodes!=null)
      it = convNodes.iterator();
    else {
      it = ssel.iterator();
    }
  
    TreeDB db2 = null;
    List<TreeNode> nodes = new LinkedList<>();
    List<Object> selObjs = new LinkedList<>();
    while (it.hasNext()) {
      Object obj = it.next();
      //not sure that it is possible
      if (obj == null) continue;
      if(obj instanceof IProject){
        TreeDB db = getTreeDB(obj);
        if(db!=null) {
          TreeNode node = db.getRootNode();
          if(node!=null)
            nodes.add(db.getRootNode());
        }
        selObjs.add(obj);
      }else 
	      if (obj instanceof TreeNode) {
	        TreeNode node = (TreeNode) obj;
	        if (db2 == null){
	          db2 = node.getTreeDB();
	          if(db2.isVirtual())
	            db2 = Instantiator.getRootTree(db2);
	        }
	        //possible missunderstood here
	        if (db2 != Instantiator.getRootTree(node.getTreeDB())) continue;
	        //if (db != null) db.removeGUIListener(guiTreeChangeListener);
	        //db = Instantiator.getRootTree(node.getTreeDB());
	        //db.addGUIListener(guiTreeChangeListener);
	        if(node == null)
	          RLogger.logError("null node");
	        nodes.add(node); 
	      }else {
            selObjs.add(obj);
	      }
    }
    return new Pair<>(selObjs, nodes);
  }

  @Override
  public void dispose() {
    super.dispose();
    activeNavigator = null;
    getCommonViewer().removeSelectionChangedListener(treeSelection);
    synchronized (RequalityCNF.this) {
      SelectionManager.getOne().removeSourceListener(fsid.getEventSource(),fsid, uiListener);
    }
    SelectionManager.getOne().unregisterInstance(fsid);
    //if (db != null) db.removeGUIListener(guiTreeChangeListener);
  }

  KeyListener kl =
      new KeyListener() {

        @Override
        public void keyReleased(KeyEvent e) {
          shiftPressed = shiftPressed ? (e.keyCode & SWT.SHIFT) == 0 : false;
          altPressed = altPressed ? (e.keyCode & SWT.CTRL) == 0 : false;
        }

        @Override
        public void keyPressed(KeyEvent e) {
          shiftPressed = shiftPressed | (e.keyCode & SWT.SHIFT) != 0;
          altPressed = altPressed | (e.keyCode & SWT.CTRL) != 0;
        }
      };

  @Override
  public void createPartControl(Composite aParent) {
    super.createPartControl(aParent);
    synchronized (RequalityCNF.this) {
      SelectionManager.getOne().addSourceListener(fsid.getEventSource(), fsid, uiListener);
    }
    
    enableTreeListener();
    getCommonViewer().setComparator(new RequalitySorter());

    ILabelDecorator decorator = PlatformUI.getWorkbench().getDecoratorManager().getLabelDecorator();
    getCommonViewer().setLabelProvider(new CNFDecorator((ILabelProvider) getCommonViewer().getLabelProvider(), decorator));
    updateStaticFilter(getCommonViewer());
    Display display = Display.getCurrent();
    display.addFilter(
        SWT.KeyUp,
        new Listener() {
          @Override
          public void handleEvent(org.eclipse.swt.widgets.Event e) {
            shiftPressed = shiftPressed ? (e.keyCode & SWT.SHIFT) == 0 : false;
            altPressed = altPressed ? (e.keyCode & SWT.CTRL) == 0 : false;
            //RLogger.logInfo("GKR "+(shiftPressed?"S":"NS")+" "+(altPressed?"A":"NA"));

          }
        });
    getCommonViewer().getControl().addKeyListener(kl);
    SharedConsts.isNavigatorLinkingEnabled = false;
    if (!PlatformUI.getPreferenceStore()
        .getBoolean(IWorkbenchPreferenceConstants.LINK_NAVIGATOR_TO_EDITOR)){
      SharedConsts.isNavigatorLinkingEnabled = true;
      this.setLinkingEnabled(true);
    }
//    if(SelectionManager.getOne().getActiveSource() == null)//on init
//      SelectionManager.getOne().changeActiveSource(SourcePriority.EDITOR, fsid.getEventSource(), fsid, isSelectionSource());
  }

  private void updateStaticFilter(CommonViewer cv) {
    final SortedSet<String> tps = new TreeSet<>();
    for (ViewerFilter o : cv.getFilters())
      if (o instanceof AbstractNodeFilter) {
        tps.addAll(Arrays.asList(((AbstractNodeFilter) o).getFilterTypes()));
      }
    RequalityContentProvider.setStaticFilter(
        new ITreeNodeFilter() {

          @Override
          public boolean select(TreeNode node) {
            if (node == null) return false;
            String type = node.getType();
            if (type == null) return false;
            return !(tps.contains(type));
          }
        });
  }

  // This is danger hack
  @Override
  protected CommonViewer createCommonViewerObject(Composite aParent) {
    CommonViewer cv =
        new CommonViewer(getViewSite().getId(), aParent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI) {
          @Override
          public void refresh() {
            if (isBusy()) //silently do nothing :)
            return;
            updateStaticFilter(this);
            super.refresh();
          }
          @Override
          public void add(Object parentElement, Object[] childElements) {
            try {
              TreeDB db = getTreeDB(parentElement);
              TreeStateManager tsm = db!=null?TreeStateManager.getOne(db.getTracker()):null;
              if (db!=null&&(tsm==null||tsm.isBusy(db.getLocalName(), tsm.getRevisionInfo(db)))) //silently do nothing :)
                return;
              super.add(parentElement, childElements);
            } catch (RuntimeException re) {
              RLogger.logError(re);
            }
          }

          @Override
          public void remove(Object[] elements) {
            try {
              for(Object o: elements) {
                TreeDB db = getTreeDB(o);
                TreeStateManager tsm = db!=null?TreeStateManager.getOne(db.getTracker()):null;
                RevisionInfo rev = db == null ? null : tsm.getRevisionInfo(db);
                if (db!=null&&(tsm==null||!tsm.isDisposed(db.getLocalName(), rev) && tsm.isBusy(db.getLocalName(), rev))) //silently do nothing :)
                  return;
              }
              super.remove(elements);
            } catch (RuntimeException re) {
            }
          }
        };
    return cv;
  }

  protected TreeDB getTreeDB(Object el) {
    if(el instanceof IProject) {
      IProject p = (IProject) el;
      EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
      try {
        return et.getTreeDB(p, RevisionInfo.NO_REV, null);
      } catch (IOException e) {
        RLogger.logError(e);
      }
    }else if (el instanceof TreeNode) {
      return ((TreeNode)el).getTreeDB();
    }
    return null;
  }

@Override
  public String getContributorId() {
    return "com.unitesk.requality.views.RequalityPropertyContributor";
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object getAdapter(@SuppressWarnings("rawtypes") Class adapter) {
    if (adapter == IPropertySheetPage.class) {
      //TODO null pointer cause??
      return new TabbedPropertySheetPage(this);
    }
    return super.getAdapter(adapter);
  }

  public static Map<String, TreeNode[]> getSelectedNodes() {
    if (activeNavigator == null) return null;
    ISelection selection =
        activeNavigator
            .getCommonViewer()
            .getSelection(); //TODO test//activeWorkbenchWindow.getSelectionService()
    //.getSelection();

    if (!(selection instanceof IStructuredSelection)) {
      return Collections.emptyMap();
    }
    IStructuredSelection selected = (IStructuredSelection) selection;

    if (selected.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, TreeNode[]> nodes = new LinkedHashMap<>();
    @SuppressWarnings("unchecked")
    List<Object> objList = selected.toList();
    Map<String, Integer> cnt = new HashMap<>();
    for (Object obj : objList) {
      TreeNode node = null;
      if (obj instanceof TreeNode) {
        node = (TreeNode) obj;
      } else if (obj instanceof IFile) {
        node = EclipseTracker.getNodeByResource(DBNamesManager.DEFAULT,(IFile) obj);
      }
      if(node!=null) {
        String type = node.getType();
        if(!nodes.containsKey(type)) 
          nodes.put(type, new TreeNode[0]);
        cnt.put(type, cnt.getOrDefault(type, 0)+1);
      }
    }
    for (Object obj : objList) {
      TreeNode node = null;
      if (obj instanceof TreeNode) {
        node = (TreeNode) obj;
      } else if (obj instanceof IFile) {
        node = EclipseTracker.getNodeByResource(DBNamesManager.DEFAULT,(IFile) obj);
      }
      if(node!=null) {
        String type = node.getType();
        TreeNode[]nds = nodes.get(type);
        int num = 0;
        if(nds.length == 0) {
          int cnta = cnt.get(type);
          nds = new TreeNode[cnta];
          nodes.put(type, nds);
          cnt.put(type, 0);
        }else
          num = cnt.get(type);
        nds[num] = node;
        cnt.put(type, num+1);
      }
    }
    if (nodes.size() > 0) return nodes;
    return Collections.emptyMap();
  }
  
  @SuppressWarnings("unchecked")
  public static List<Object> getSelectedObj() {
    ISelection selection = activeNavigator.getCommonViewer().getSelection();

    if (!(selection instanceof IStructuredSelection)) {
      return Collections.emptyList();
    }
    IStructuredSelection selected = (IStructuredSelection) selection;

    if (selected.isEmpty()) {
      return Collections.emptyList();
    }
    return selected.toList();
  }

  private Map<UUID, Long> lastUpdate = new HashMap<>();

  private static UUID root = UUID.randomUUID();
  public void refreshAll() {
    if (this.getCommonViewer() != null) {
      if (!this.getCommonViewer().isBusy())
        try {
          //TreeDB db = tn.getTreeDB();
          //db.getStorage().getParent(tn.getUUId());
          //lastUpdate.getOrDefault(tn.getUUId(),0L);
          //  while(par!=null){
          //                Long n = lastUpdate.getOrDefault(par,0L);
          //                if(n>max)
          //                  max = n;
          //                par = db.getStorage().getParent(par);
          //              }
          Long now = System.currentTimeMillis();
          //if(now-max<TIME_SKIP){
          lastUpdate.put(root, now);
          this.getCommonViewer().refresh(null);
          //  }
        } catch (Exception e) {
          //TODO fix unnecessary refresh
        }
      }
  }
  public void refresh(TreeNode tn) {
    if (tn == null) return;
    if (this.getCommonViewer() != null) {
      if (!this.getCommonViewer().isBusy())
        try {
          Long now = System.currentTimeMillis();
          //if(now-max<TIME_SKIP){
          lastUpdate.put(tn.getUUId(), now);
          this.getCommonViewer().refresh(tn);
          //  }
        } catch (Exception e) {
          //TODO fix unnecessary refresh
        }
    }
  }

  @Override
  protected Object getInitialInput() {
    if (super.getInitialInput() == null) {
      return ResourcesPlugin.getWorkspace().getRoot();
    }
    return super.getInitialInput();
  }
  
  @Override
  public boolean contains(ISchedulingRule rule) {
    return false;
  }

  @Override
  public boolean isConflicting(ISchedulingRule rule) {
    return rule == this;
  }

  @Override
  public FSId getFSId() {
    return fsid;
  }

  @Override
  public boolean isSelectionSource() {
    return true;
  }
}
