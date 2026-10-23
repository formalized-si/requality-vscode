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

package com.unitesk.requality.views.documents;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.e4.ui.model.application.ui.MElementContainer;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.basic.MPartSashContainerElement;
import org.eclipse.e4.ui.model.application.ui.basic.impl.PartStackImpl;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.IContributedContentsView;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.ITabbedPropertySheetPageContributor;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;

import com.unitesk.requality.core.IFSIdProvider;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.document.ITaskListViewer;
import com.unitesk.requality.core.operation.document.Task;
import com.unitesk.requality.core.operation.document.TaskList;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.IUIStateChangeListener;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.service.ResourcesProvider;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ITreeStateListener;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.eclipse.editors.browser.ReqMarker;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.ToDoItem;
import com.unitesk.requality.tool.CoreUtils;

/** A class for processing a view of tasks list after update processor */
@SuppressWarnings("restriction")
public class ToDoList extends ViewPart
    implements ITreeChangeListener, ITreeStateListener, ITabbedPropertySheetPageContributor, IFSIdProvider {
  volatile TableViewer tableViewer;
  Table table;
  private Button closeButton;
  private boolean isDisposed = false;
  private FSId fsid;

  private static boolean hideCompleted = false;

  private static List<ToDoList> activeToDoViews = new ArrayList<>();

  public static boolean isHideCompleted() {
    return hideCompleted;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object getAdapter(@SuppressWarnings("rawtypes") Class adapter) {
    if (adapter.equals(IPropertySheetPage.class)) {
      // TODO null pointer cause??
      return new TabbedPropertySheetPage(
          (ITabbedPropertySheetPageContributor) PlatformUI.getWorkbench().getActiveWorkbenchWindow()
              .getActivePage().findView("com.unitesk.requality.views.RequalityExplorer"));
    }

    if (adapter.equals(IContributedContentsView.class)) {
      return new IContributedContentsView() {
        public IWorkbenchPart getContributingPart() {
          return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
              .findView("com.unitesk.requality.views.RequalityExplorer");
        }
      };
    }
    return super.getAdapter(adapter);
  }

  public static void setHideCompleted(boolean hideCompleted) {
    ToDoList.hideCompleted = hideCompleted;

    for (ToDoList toDoView : activeToDoViews) {
      toDoView.refresh(true);
    }
  }

  private TreeDB db;

  private boolean isModified = false;

  static ToDoItem selectedItem = null;

  Document oldDoc;
  Document newDoc;
  private volatile TaskList taskList = null;
  private IUIStateChangeListener list;

  public static final String ID = "com.unitesk.requality.views.ToDoList";

  public ToDoList() {
    activeToDoViews.add(this);
    isDisposed = false;
    fsid = SelectionManager.getOne().registerNewFSId(EventSource.PROPERTIES, ToDoList.this, true);
  }

  public void init(IViewSite site) throws PartInitException {
    super.init(site);
  }

  private void initElements(SelectionRecord nodes) {
    if (isDisposed) {
      return;
    }
    boolean ok = false;
    if (nodes.fullSelection.size() > 0) {
      // taskList = null;
      try {
        if (db != null) {
          db.removeGUIListener(this);
        }
        TreeNode node = nodes.getFirstNode();
        if (node == null)
          return;
        this.db = node.getTreeDB();
        TreeDB db = node.getTreeDB();
        ok = true;
        if (taskList == null)
          taskList = new TaskList();
        if (taskList != null)
          taskList.initData(db, refreshTableRunnableDisplay);
      } catch (NullPointerException e) {
      } finally {
        if (!ok) {
          if (taskList == null)
            taskList = new TaskList();
          if (taskList != null)
            taskList.initData(this.db, refreshTableRunnableDisplay);
        }
        if(this.db!=null)
          this.db.addGUIListener(this);
      }
    }
  }

  public void createPartControl(Composite p1) {
    Composite parent = new Composite(p1, SWT.NONE);
    parent.setLayout(new GridLayout(1, true));
    Display d = Display.getCurrent();
    list = new IUIStateChangeListener() {

      @Override
      public void newActiveNodes(FSId fsida, SelectionRecord nodes) {
        // TODO listener of CNF
        // List<Object> ar = RequalityCNF.getSelectedObj();
        // if(ar.size()>0)
        // ar.get(0);
        SelectionManager.getOne().removeSourceListener(EventSource.EXPLORER, fsid, list);
        try {
          int len = 0;

          initElements(nodes);

          d.asyncExec(new Runnable() {
            @Override
            public void run() {
              tableViewer.refresh();
            }
          });
        } finally {
          SelectionManager.getOne().addSourceListener(EventSource.EXPLORER, fsid, list);
        }
      }
    };
    SelectionManager.getOne().addSourceListener(EventSource.EXPLORER, fsid, list);

    int style = SWT.SINGLE | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.HIDE_SELECTION;

    // parent.setLayout(new GridLayout(1, true));
    table = new Table(parent, style);

    GridData gridData = new GridData(GridData.FILL_BOTH);
    gridData.grabExcessVerticalSpace = true;
    gridData.horizontalSpan = 3;
    table.setLayoutData(gridData);

    table.setLinesVisible(true);
    table.setHeaderVisible(true);

    tableViewer = new TableViewer(table);

    tableViewer.setComparator(new TaskSorter());

    TableColumn column = new TableColumn(table, SWT.CENTER, 0);
    column.setText(" ");
    column.setWidth(20);
    // by clicking on cell above picture - sort by locations
    column.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        ((TaskSorter) tableViewer.getComparator()).doSort(TaskSorter.PICTURE);
        tableViewer.refresh();
      }
    });

    column = new TableColumn(table, SWT.LEFT, 1);
    column.setText("Requirement Description");
    column.setWidth(400);
    column.addSelectionListener(new SelectionAdapter() {

      public void widgetSelected(SelectionEvent e) {
        ((TaskSorter) tableViewer.getComparator()).doSort(TaskSorter.DESCRIPTION);
        tableViewer.refresh();
      }
    });

    column = new TableColumn(table, SWT.LEFT, 2);
    column.setText("Action");
    column.setWidth(100);
    column.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        ((TaskSorter) tableViewer.getComparator()).doSort(TaskSorter.ACTION);
        tableViewer.refresh();
      }
    });

    column = new TableColumn(table, SWT.LEFT, 3);
    column.setText("Status");
    column.setWidth(100);
    column.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        ((TaskSorter) tableViewer.getComparator()).doSort(TaskSorter.STATUS);
        tableViewer.refresh();
      }
    });

    tableViewer.setUseHashlookup(true);

    tableViewer.setContentProvider(new ToDoListContentProvider());
    tableViewer.setLabelProvider(new TaskLabelProvider());

    try {
      SelectionRecord sel = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
      if (taskList == null) {
        taskList = new TaskList();
      }
      if (!sel.fullSelection.isEmpty()) {
        if(this.db!=null)
          this.db.removeGUIListener(this);
        TreeNode node = sel.fullSelection.iterator().next();
        {
          try {
            taskList.initData(node.getTreeDB(), refreshTableRunnableDisplay);
          }finally {
            if(this.db!=null)
              this.db.removeGUIListener(this);
          }
        }
      }
    } catch (NullPointerException e) {
      // = new TaskList();
    }

    Composite buttons = new Composite(parent, SWT.NO_SCROLL);
    buttons.setLayout(new GridLayout(5, true));

    GridData gridDataButtons = new GridData(GridData.FILL_HORIZONTAL);
    buttons.setLayoutData(gridDataButtons);

    createButtons(buttons);

    tableViewer.addDoubleClickListener(new IDoubleClickListener() {

      @SuppressWarnings("unchecked")
      @Override
      public void doubleClick(DoubleClickEvent event) {

        SelectionManager.getOne().removeSourceListener(EventSource.EXPLORER, fsid, list);
        try {
          IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();

          if (selection.getFirstElement() instanceof Task) {

            Task task = (Task) selection.getFirstElement();

            Requirement req = task.getReq();

            Location loc = null;

            String oldDocID = task.getOldDocQID();

            NodeEditorInput input = null;

            for (Location oldLoc : req.getLocations(false)) {
              if (oldLoc.getWorkParent().getQualifiedId().equals(oldDocID)) {
                loc = oldLoc;

                input = new NodeEditorInput(loc.getWorkParent(), loc, ReqMarker.ID, fsid);
                input.setData(loc);
                break;
              }
            }

            Location locInNewDoc = null;
            String newDocID = task.getNewDocQID();

            NodeEditorInput inputInNewDoc = null;

            for (Location newLoc : req.getLocations(false)) {
              if (newLoc.getWorkParent().getQualifiedId().equals(newDocID)) {
                locInNewDoc = newLoc;

                inputInNewDoc = new NodeEditorInput(locInNewDoc.getWorkParent(), locInNewDoc,
                    ReqMarker.ID, fsid);
                inputInNewDoc.setData(locInNewDoc);
                break;
              }
            }

            if (locInNewDoc == null) {
              // locInNewDoc = (Location) db.getNode(task.getNewDocQID());
              inputInNewDoc = new NodeEditorInput(db.getNode(task.getNewDocQID(), Path.QID), ReqMarker.ID,
                  fsid);
              // inputInNewDoc.setData(locInNewDoc);
            }

            SelectionManager sman = SelectionManager.getOne();
            FSId id1 = null, id2 = null;
            try {

              if ((db.getNode(oldDocID, Path.QID | Path.UV) == null)
                  || (db.getNode(newDocID, Path.QID | Path.UV) == null)) {
                refresh(true);
                showMessageBox("Update Processor Tasks",
                    "One of the documents has been removed or deleted.");
                return;
              }

              // if (inputInNewDoc == null && (db.getNode(task.getNewDocQID()) != null) ) {
              // inputInNewDoc = new NodeEditorInput(db.getNode(task.getNewDocQID()));
              // }

              if ((input == null) || (inputInNewDoc == null)) {
                refresh(true);
                showMessageBox("Update Processor Tasks",
                    "One of the documents has been removed or deleted.");
                return;
              }

              IWorkbenchPage p = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
              IEditorPart ip = IDE.openEditor(p, input, ReqMarker.ID);
              boolean changed = false;
              MPartSashContainerElement cn = null;
              MPartSashContainerElement cn2 = null;

              EModelService modelService = null;
              MPart mpartService = null;
              if (ip != null && ip.getEditorSite() != null && ip.getEditorSite().getPart() != null) {
                id1 = sman.detectSource(ip.getEditorSite().getPart());
                // ((ReqMarker) ip).setTargetLocation(time, loc, true);
                // SelectionManager.getOne().updateSelection(SourcePriority.PROPERTIES, fsid,
                // loc);
                // SelectionManager.getOne().changeActiveSource(EventSource.EXPLORER,
                // ToDoList.this, true);

                modelService = ip.getSite().getService(EModelService.class);
                mpartService = ip.getSite().getService(MPart.class);
                cn = (MPartSashContainerElement) mpartService.getParent();
                for (MUIElement muie : cn.getParent().getChildren())
                  if (muie instanceof MElementContainer<?>) {
                    for (MUIElement mu : ((MElementContainer<MUIElement>) muie).getChildren())
                      if (mu.getTags().contains("Editor") && !cn.equals(muie))
                        cn2 = (MPartSashContainerElement) muie;
                  }
                if (cn2 == null) {
                  changed = true;
                  cn2 = (MPartSashContainerElement) modelService.cloneElement(cn, null);
                  cn2.setParent(cn.getParent());
                  try {
                    ((PartStackImpl) cn2).getChildren().clear();
                  }catch(Exception e) {
                    RLogger.logDebug(CoreUtils.getTrace(e));
                    //lowers message priority
                  }
                }
                if (id1 != null)
                  sman.updateSelection(SourcePriority.PROPERTIES, id1, loc);
              } else
                changed = true;
              // IEditorPart ip2 = IDE.openEditor(PlatformUI
              // .getWorkbench().getActiveWorkbenchWindow()
              // .getActivePage(),
              // inputInNewDoc, ReqMarker.ID);

              IEditorPart ip2 = IDE.openEditor(p, inputInNewDoc, ReqMarker.ID);

              // if locInNewDoc is null this will clear the highlighting
              // ((ReqMarker) ip2).setTargetLocation(time, locInNewDoc, true);
              if (modelService == null) {
                modelService = ip2.getSite().getService(EModelService.class);
              }
              mpartService = ip2.getSite().getService(MPart.class);

              boolean containsMpartService = false;
              if (ip2.getEditorSite() != null && ip2.getEditorSite().getPart() != null)
                id2 = sman.detectSource(ip2.getEditorSite().getPart());
              if (cn2 != null) {
                containsMpartService = false;
                if(((PartStackImpl) cn2).getChildren().contains(mpartService))
                  containsMpartService = true;
                if (!containsMpartService) {
                  ((PartStackImpl) cn2).getChildren().add((MPart) mpartService);
                  modelService.bringToTop(ip2.getSite().getService(MPart.class));
                }
              }

              if (changed) {
                modelService.insert(cn2, cn, EModelService.RIGHT_OF, 0.5f);
              }
              if (!mpartService.isVisible())
                modelService.bringToTop(mpartService);
              if (ip != null && !ip.getSite().getService(MPart.class).isVisible())
                modelService.bringToTop(ip.getSite().getService(MPart.class));

            } catch (PartInitException e) {
              e.printStackTrace();
            }
            if (id1 != null) {
              if (locInNewDoc != null)
                sman.updateSelection(SourcePriority.PROPERTIES, id1, locInNewDoc, loc);
              else
                sman.updateSelection(SourcePriority.PROPERTIES, id1, loc);
            }
            if (id2 != null) {
              if (locInNewDoc != null)
                sman.updateSelection(SourcePriority.PROPERTIES, id2, locInNewDoc, loc);
              else
                sman.updateSelection(SourcePriority.PROPERTIES, id2, loc);
            }
          } else {
            return;
          }
        } finally {
          SelectionManager.getOne().addSourceListener(EventSource.EXPLORER, fsid, list);
        }
      }
    });

    tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {

      @Override
      public void selectionChanged(SelectionChangedEvent arg0) {

        if (isModified) {
          refresh(false);
          isModified = false;
        }

        Task currentNode = (Task) ((StructuredSelection) tableViewer.getSelection()).getFirstElement();
        if (currentNode != null) {
          if (db == null) {
            db = currentNode.getReq().getTreeDB();
          } else {
            db.removeGUIListener(ToDoList.this);
            db = currentNode.getReq().getTreeDB();
          }
          db.addGUIListener(ToDoList.this);
          TreeNode treeNode = db.getNode(currentNode.getToDoItemUUID());
          selectedItem = (ToDoItem) treeNode;
        }
      }
    });
    taskList = new TaskList();
    setInputInNewThread();
    EclipseTracker.getTracker(DBNamesManager.DEFAULT).addListener(Moment.AFTER, this);
    SelectionManager.getOne().activateSource(SourcePriority.SOURCE_CHANGE, fsid.getEventSource(), fsid,
        isSelectionSource(), false);
  }

  public void setFocus() {
    tableViewer.getControl().setFocus();
  }

  @Override
  public void dispose() {
    tableViewer.getLabelProvider().dispose();
    SelectionManager.getOne().removeSourceListener(EventSource.EXPLORER, fsid, list);
    SelectionManager.getOne().unregisterInstance(fsid);
    isDisposed = true;
    activeToDoViews.remove(this);
    EclipseTracker.getTracker(DBNamesManager.DEFAULT).removeListener(Moment.AFTER, this);
    SelectionManager.getOne().deActivateSource(SourcePriority.SOURCE_CHANGE, fsid.getEventSource(), fsid,
        isSelectionSource());
  }

  class ToDoListContentProvider implements IStructuredContentProvider, ITaskListViewer {

    public void inputChanged(Viewer v, Object oldInput, Object newInput) {
      if (newInput != null)
        ((TaskList) newInput).addChangeListener(this);
      if (oldInput != null)
        ((TaskList) oldInput).removeChangeListener(this);
    }

    public void dispose() {
    }

    public Object[] getElements(Object parentElement) {

      if (hideCompleted) {
        ArrayList<Task> allTasks = ((TaskList) parentElement).getTasks();
        ArrayList<Task> tasksToCheck = new ArrayList<>();
        for (Task task : allTasks) {
          if (task.getStatus().equals(Task.Status.CHECK)) {
            tasksToCheck.add(task);
          }
        }
        return tasksToCheck.toArray();
      } else {
        return ((TaskList) parentElement).getTasks().toArray();
      }
    }

    public void addTask(Task task) {
      tableViewer.add(task);
    }

    public void removeTask(Task task) {
      tableViewer.remove(task);
    }

    public void updateTask(Task task) {
      tableViewer.update(task, null);
    }
  }

  private void createButtons(Composite parent) {
    Button check = new Button(parent, SWT.PUSH);
    check.setText("Check");
    GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
    gridData.widthHint = 80;
    check.setLayoutData(gridData);

    check.addSelectionListener(new SelectionAdapter() {

      public void widgetSelected(SelectionEvent e) {
        Task task = (Task) ((IStructuredSelection) tableViewer.getSelection()).getFirstElement();
        if (task != null) {
          task.setStatus(Task.Status.CHECK);
          TreeNode item = db.getNode(task.getToDoItemUUID());
          item.attrs().setConstAttribute(ToDoItem.ATTR_STATUS, Task.Status.CHECK.toString(), Flag.HIDDEN);
          item.commitAttributes();
          tableViewer.refresh();
        }
      }
    });

    Button complete = new Button(parent, SWT.PUSH);
    complete.setText("Complete");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
    gridData.widthHint = 80;
    complete.setLayoutData(gridData);

    complete.addSelectionListener(new SelectionAdapter() {

      public void widgetSelected(SelectionEvent e) {
        Task task = (Task) ((IStructuredSelection) tableViewer.getSelection()).getFirstElement();
        if (task != null) {
          task.setStatus(Task.Status.COMPLETED);
          TreeNode item = db.getNode(task.getToDoItemUUID());
          item.attrs().setConstAttribute(ToDoItem.ATTR_STATUS, Task.Status.COMPLETED.toString(), Flag.HIDDEN);
          item.commitAttributes();
          tableViewer.refresh();
        }
      }
    });
  }

  public void refresh(TreeDB db) {
    boolean isOk = false;
    try {
      if(this.db!=null)
        this.db.removeGUIListener(this);
      if (db != null) {
        isOk = true;
        if (taskList == null)
          taskList = new TaskList();
        try {
          taskList.initData(db, refreshTableRunnableDisplay);
        }finally {
          if(this.db!=null)
            this.db.addGUIListener(this);
        }
      }
    } catch (NullPointerException e) {
    } finally {
      if (!isOk) {
        if (taskList == null)
          taskList = new TaskList();
        taskList.initData(this.db, refreshTableRunnableDisplay);
      }
    }
  }

  public ISelection getSelection() {
    return tableViewer.getSelection();
  }

  public Control getControl() {
    return table.getParent();
  }

  public Button getCloseButton() {
    return closeButton;
  }

  public static ToDoItem getSelectedNode() {
    return selectedItem;
  }

  @Override
  public void beforeCreated(NodeDesc node, byte flag) {

  }

  AtomicBoolean setRefresh = new AtomicBoolean(false);
  private static int MILIS_TIMER_TIMEOUT = 500;
  private long upTimer = -1;
  private Thread upThread = null;

  @Override
  public void created(TreeNode node, byte flag) {
    isModified = true;

    if (((Document.TYPE_NAME.equals(node.getType())) && taskList != null
        && taskList.getListOfDocQIds().contains(node.getQualifiedId())) || ToDoItem.TYPE_NAME.equals(node.getType())&&node.getTreeDB().equals(db)) {
      schelduleRefresh();
    }
  }
  public void resetRefresh() {
    if(upThread!=null)
    {
      synchronized(upThread) {
        Thread called = upThread;
        upThread = null;
        upTimer = -1;
        called.notifyAll();
      }
    }
  }
  public void schelduleRefresh() {
    setRefresh.set(true);
    upTimer = System.currentTimeMillis()+MILIS_TIMER_TIMEOUT;
    if(upThread == null) {
      upThread = new Thread(new Runnable() {
        @Override
        public void run() {
          while ((upTimer == -1 || System.currentTimeMillis()<upTimer) && Thread.currentThread() == upThread) {
            try {
              synchronized (Thread.currentThread()) {
                if(upTimer>-1)
                  Thread.currentThread().wait(upTimer-System.currentTimeMillis());
                else
                  Thread.currentThread().wait(MILIS_TIMER_TIMEOUT);
              }
            } catch (InterruptedException e) {
            }
          }
          if(Thread.currentThread() == upThread && upThread!=null)
            refresh(true);	
        }
      });
    }
  }
  
  @Override
  public void deleted(final TreeNode nodeDesc, byte flag, Moment m) {
    if (m == Moment.AFTER)
      return;
    final TableViewer tableViewer = this.tableViewer;
    final TaskList taskList = this.taskList;
    if (taskList != null) {
      if((nodeDesc.getType().equals(Document.TYPE_NAME) || nodeDesc.getType().equals(ToDoItem.TYPE_NAME))&&
          nodeDesc.getTreeDB().equals(db)) {
        UIJob job = new UIJob("update requality cnf (deleted)") {
          @Override
          public IStatus runInUIThread(IProgressMonitor monitor) {
            if (tableViewer.getTable().isDisposed())
              return Status.CANCEL_STATUS;
            boolean callRefresh = false;
            if(nodeDesc.getType().equals(ToDoItem.TYPE_NAME))
              for (TableItem ti : tableViewer.getTable().getItems()) {
                Task task = ((Task) ti.getData());
                if (task.getToDoItemUUID().equals(nodeDesc.getUUId())) {
                  tableViewer.remove(task);
                  taskList.getTasks().remove(task);
                  callRefresh = true;
                }
              }
  
            if (Document.TYPE_NAME.equals(nodeDesc.getType()) && taskList.getListOfDocQIds()
                .contains(nodeDesc.getQualifiedId())) { 
              callRefresh = false;
              schelduleRefresh();
            }
            
            if(callRefresh)
              EclipseAPI.getDisplay().asyncExec(refreshTableRunnableDisplay);
            return Status.OK_STATUS;
          }
        };
        job.schedule();
      }
    }
  }

  @Override
  public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent, Moment m, byte flag) {
    // TODO Auto-generated method stub

  }
  private Runnable refreshTableRunnable = new Runnable() {
    @Override
    public void run() {
      if (tableViewer.getTable().isDisposed())
        return;
      tableViewer.refresh();
    }
  };

  private Runnable refreshTableRunnableDisplay = new Runnable() {
    @Override
    public void run() {
      EclipseAPI.getDisplay().asyncExec(refreshTableRunnable);
    }
  };

  private void refresh(boolean forced) {
    if (isDisposed) {
      return;
    }
    boolean isOk = false;
    TreeDB dbToSet = db;
    if (db != null) {
      if (taskList == null)
        taskList = new TaskList();
      db.removeGUIListener(this);
      try {
        isOk = true;
        if(forced)
          this.db = null;
        taskList.initData(dbToSet, refreshTableRunnableDisplay);
      } catch (NullPointerException e) {
      } finally {
        if (!isOk) {
          taskList.initData(this.db, refreshTableRunnableDisplay);
        }else
          dbToSet.addGUIListener(this);
      }

    } else {
      if (!isOk) {
        if (taskList == null)
          taskList = new TaskList();
        taskList.initData(this.db, refreshTableRunnableDisplay);
      }
    }
  }

  private void setInputInNewThread() {
    final TableViewer tableViewer = this.tableViewer;
    final TaskList taskList = this.taskList;
    JobManager.jm.startJob("Init input on TODOList", new RunnableWithMonitor() {
      @Override
      public boolean shouldRun() {
        return ResourcesProvider.getOne().initiated;
      }

      @Override
      public boolean shouldRerun() {
        return true;
      }

      @Override
      public void run(com.unitesk.requality.core.operation.IProgressMonitor pm) {
        EclipseAPI.getDisplay().asyncExec(new Runnable() {
          public void run() {
            if (tableViewer.getControl().isDisposed())
              return;
            tableViewer.setInput(taskList);
          }
        });
      }
    }, false);
  }

  protected void showMessageBox(final String messageBoxTitle, final String messageBoxMessage) {
    IWorkbench w = PlatformUI.getWorkbench();
    w.getDisplay().syncExec(new Runnable() {
      public void run() {
        Shell activeShell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
        MessageDialog.openInformation(activeShell, messageBoxTitle, messageBoxMessage);
      }
    });
  }

  @Override
  public void open(TreeDB db) {
    if(this.db!=null && db!=null && this.db.getLocalName().equals(db.getLocalName()))
      refresh(db);
  }

  @Override
  public void close(TreeDB db) {
    if (this.db == db) {
      this.db.removeGUIListener(this);
      this.db = null;
      refresh(false);
    }
  }

  @Override
  public void delete(String lname, String pname, TreeDB db) {
    if (this.db == db) {
      this.db.removeGUIListener(this);
      this.db = null;
      refresh(false);
    }
  }

  @Override
  public void changed(TreeNode node, ChangesSet<Attribute> params, Moment m, byte flag) {
    // TODO Auto-generated method stub

  }

  @Override
  public void changed(List<TreeNodeChangeEvent> changes) {
    setRefresh.set(false);
    for (TreeNodeChangeEvent ev : changes) {
      byte flag = ev.getFlag();
      switch (ev.getType()) {
      case CREATE:
        if (ev.getMoment() == Moment.AFTER)
          created(ev.getTreeNodeTarget(), flag);
        break;

      case DELETE:
        if (ev.getMoment() == Moment.BEFORE)
          deleted(ev.getTreeNodeTarget(), flag, ev.getMoment());
        break;
      }
      if(setRefresh.get())
        break;//already refresh
    }
    if(setRefresh.get())
      refresh(true);
  }

  @Override
  public String getContributorId() {
    return ID;
  }

  @Override
  public void childrenOrderChanged(TreeNode node, byte flag) {
  }

  @Override
  public void settingsChanged(TreeDB db) {
    // TODO Auto-generated method stub

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
