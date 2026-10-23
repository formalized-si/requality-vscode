package com.unitesk.requality.vscode.port.eclipse.ui.editors;

import com.unitesk.requality.vscode.ui.workbench.operations.UndoRedoActionGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Browser;
import com.unitesk.requality.vscode.ui.swt.browser.LocationListener;
import com.unitesk.requality.vscode.ui.swt.browser.ProgressEvent;
import com.unitesk.requality.vscode.ui.swt.browser.ProgressListener;
import com.unitesk.requality.vscode.ui.swt.events.KeyEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyListener;
import com.unitesk.requality.vscode.ui.swt.events.MouseEvent;
import com.unitesk.requality.vscode.ui.swt.events.MouseListener;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.IEditorInput;
import com.unitesk.requality.vscode.ui.workbench.IEditorSite;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.commands.IHandlerService;
import com.unitesk.requality.vscode.ui.workbench.EditorPart;
import com.unitesk.requality.vscode.ui.workbench.IContributedContentsView;
import com.unitesk.requality.vscode.ui.runtime.UIJob;
import com.unitesk.requality.vscode.ui.workbench.IContentOutlinePage;
import com.unitesk.requality.vscode.ui.workbench.IPropertySheetPage;
import com.unitesk.requality.vscode.ui.tabbed.ITabbedPropertySheetPageContributor;
import com.unitesk.requality.vscode.ui.tabbed.TabbedPropertySheetPage;

import com.unitesk.requality.core.IFSIdProvider;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.ISelectionConverter;
import com.unitesk.requality.core.selection.IUIStateChangeListener;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.service.EditorsSocketService;
import com.unitesk.requality.core.service.ISelectionChangedHandler;
import com.unitesk.requality.core.service.ResourcesProvider;
import com.unitesk.requality.core.service.ResourcesServlet;
import com.unitesk.requality.document.IDocumentSocketServiceStateListener;
import com.unitesk.requality.document.ILinkedStateProvider;
import com.unitesk.requality.vscode.port.eclipse.ITreeDBProvider;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.vscode.port.eclipse.editors.browser.NoLinkKeepSelectionConverter;
import com.unitesk.requality.vscode.port.eclipse.handlers.copypaste.EclipseCopyHandler;
import com.unitesk.requality.vscode.port.eclipse.handlers.copypaste.EclipsePasteHandler;
import com.unitesk.requality.vscode.port.eclipse.ui.ObjectRule;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.SelectionFromSource;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;

public abstract class JSEditor extends EditorPart implements ISelectionProvider, ITreeDBProvider, IFSIdProvider, IEditorWithId, ITabbedPropertySheetPageContributor {
  private static final String NO_SOCKET_INITED = "<html><body><h3>Socket for editor is not inited</h3></body></html>";
  private final FSId fsid;
  private int socketStoppedCount = 0;
  protected EditorsSocketService socservice;
  public static Set<String> defaultAttrs = new HashSet<>();
  public static final int EDIT_MODE_REQ_RO = 1;
  public static final int EDIT_MODE_TESTPURPOSE_RO = 2;
  public static final int EDIT_MODE_TESTSTEP_RO = 4;
  public static final int EDIT_MODE_TN_RO = 16;
  public static final int EDIT_MODE_TESTPROCEDURE_RO = 32;
  public static final int EDIT_MODE_NOT_EDIT_VIRTUAL = 8;
  private Browser browser;
  //protected File html;
  private String htmlName;
  private TreeNode rootNode;
  private TreeNode currentSelection;
  private TreeDB db;
  private UniEditorOutlinePage uniEditorOutlinePage;
  private boolean javaDialogOpened = false;
  private int editMode = 0;
  private ObjectRule or = null;
  private String url = null;
  
  private boolean loadingStarted = false;
  java.util.Timer updateFreeTimer = new Timer();
  private boolean isUpdating = false;
  private ISelectionChangedHandler onSelectionChanged = new ISelectionChangedHandler() {
    @Override
    public void run (TreeNode node) {
      SelectionManager sman = SelectionManager.getOne();
      FSId sid = socservice!=null?socservice.getFSId():null;
      if(sid == null)
        return;
      sman.removeSourceListener(sid.getEventSource(), sid, selListener);
      try {
        sman.updateSelection(SourcePriority.EDITOR, sid, node);

        //TODO temp to sync selection
        SelectionManager.getOne().
          activateSource(SourcePriority.SOURCE_CHANGE, 
            sid.getEventSource(),
            sid, true, true);//TODO true?
      }finally {
        sman.addSourceListener(sid.getEventSource(), sid, selListener);
      }
    }
  };
  public TreeDB getTreeDB(){
    if(rootNode!=null)
      return rootNode.getTreeDB();
    return db;
  }
  @Override
  public String getContributorId() {
    return "com.unitesk.requality.views.RequalityPropertyContributor";
  }

  public String getSelectionText() {
    return "";
    //TODO reimplement
//    return getSelectionText(true);
  }

  final class BrowserKeyListener implements KeyListener {
    @Override
    public void keyReleased(KeyEvent e) {
      if ((e.stateMask & SWT.CTRL) != 0) {
        if (e.keyCode == 'c') {
          //TODO reimplement
//          String textData = getSelectionText(false);
//          if (textData == null || textData.trim().equals("")) return;
//          Clipboard clipboard = new Clipboard(getSite().getShell().getDisplay());
//          TextTransfer textTransfer = TextTransfer.getInstance();
//          Transfer[] transfers = new Transfer[] {textTransfer};
//          Object[] data = new Object[] {textData};
//          clipboard.setContents(data, transfers, DND.CLIPBOARD);
//          clipboard.dispose();
        }
        if (e.keyCode == 'v') {
          //                  Clipboard clipboard = new Clipboard(getSite().getShell().getDisplay());
          //                    TextTransfer textTransfer = TextTransfer.getInstance();
          //                    Object o = clipboard.getContents(textTransfer);
          //                    clipboard.dispose();
          //                    String textData = (String)o;
          //                    if(textData == null || textData.trim().equals(""))
          //                        return;
          //                    setSelectionText(CoreUtils.htmlEscape(textData));

        }
      }
//      if ((e.stateMask & SWT.CTRL) != 0 && (e.stateMask & SWT.SHIFT) != 0 && e.keyCode == 'd') {
//        CoreUtils.DEBUG_LEVEL = 1;
//      }
      if (e.keyCode == SWT.F5) {
        oldRefresh(rootNode);
        browser.refresh();
      }
    }

    @Override
    public void keyPressed(KeyEvent e) {}
  }

  public void initSocService(TreeNode doc, FSId fcid, ILinkedStateProvider st) {
    socservice = new EditorsSocketService(doc, htmlName, fcid, editMode, st, onSelectionChanged );
    socservice.addStatusListener(new IDocumentSocketServiceStateListener() {
      @Override
      public void close() {
        EclipseAPI.getDisplay().asyncExec(new Runnable() {
    
          @Override
          public void run() {
            try {
              JSEditor.this.close();
            }catch(Exception e) {
              RLogger.logError(e);
            }
          }
        });
      }
    });
    socservice.systemAttrs.addAll(rats);
//    boolean stopSocket = false;
//    synchronized (JSEditor.this) {
//      socketStoppedCount++;
//      stopSocket = socketStoppedCount == 1; 
//    }
//    if(stopSocket)
//      socservice.pauseDisposeTimer();
  }
  
  protected void close() {
    if(this.getBrowser().isDisposed())
      return;
    page.closeEditor(this, false);
  }

  public TreeNode getCurrentSelection() {
    if (currentSelection == null) {
      currentSelection = targetNode;
    }
    return currentSelection;
  }

  public void setSelectedNode(FSId fsid,TreeNode node) {
    setSelectedNode(fsid, node, true);
  }
  public void setSelectedNode(FSId fsid, TreeNode node, boolean withoutline) {
    UUID nuid = null;
    if(fsid == null)
      fsid = getFSId();
    if (node != null)
      nuid = node.getUUId();
//      currentSelection = node;
//      return;
//    }

    setUniEditorFocus(nuid, node, false);
    currentSelection = node;
    if(withoutline) {
      setOutlineFocus(fsid, currentSelection);
    }
  }

  private void setOutlineFocus(FSId source, TreeNode node) {
    EclipseAPI.getDisplay().asyncExec(new Runnable() {
      @Override
      public void run() {
        try {
          if (uniEditorOutlinePage != null && !uniEditorOutlinePage.getControl().isDisposed()) {
            //uniEditorOutlinePage.removeSelectionChangedListener(outlineSelectionChangeListener);
            try {
              uniEditorOutlinePage.setSelection(node);
            }finally {
//              uniEditorOutlinePage.addSelectionChangedListener(outlineSelectionChangeListener);
            }
          }
        }finally {
        }
      }
    });
  }
  public boolean findText(
      String aString, boolean aCaseSensitive, boolean aBackwards, boolean aWrapAround) {
    return false;
    //TODO reimplement
//    return (Boolean)
//        browser.evaluate(
//            "return findText('"
//                + aString
//                + "',"
//                + aCaseSensitive
//                + ","
//                + aBackwards
//                + ","
//                + aWrapAround
//                + ");");
  }

  private Map<UUID, NodeDesc> changedNodes;
  private Map<UUID, Integer> changedNodesCount;


  private TreeNode targetNode;

  @Override
  public void doSave(IProgressMonitor monitor) {}

  @Override
  public void doSaveAs() {}

  private String tabLabel = "";
  private IWorkbenchPage page;
  /* (non-Javadoc)
   * setter for Editor's prefix in tab label
   */
  protected void setTabLabel(String value) {
    tabLabel = value;
  }
  /* (non-Javadoc)
   * Update a tab name
   */
  protected void updateTabLabel() {
    final TreeNode rootNode = this.rootNode;
    final String tabLabel = this.tabLabel;
    UIJob job =
        new UIJob("update label") {
          @Override
          public IStatus runInUIThread(IProgressMonitor monitor) {
            String partName;
            if (rootNode instanceof Requirement) {
              partName = ((Requirement) rootNode).getNameOrId();
            } else {
              partName = rootNode.toString();
            }
            partName += " (" + rootNode.getType() + ")";
            if (tabLabel.equals("")) {
              setPartName(partName);
            } else {
              setPartName(tabLabel + " - " + partName);
            }
            return Status.OK_STATUS;
          }
        };
    job.schedule();
  }

  @Override
  public void init(IEditorSite site, IEditorInput input) throws PartInitException {
    changedNodes = new HashMap<>();
    page = site.getPage();
    changedNodesCount = new HashMap<>();
    TreeNode sel = null;
    try {
      rootNode = ((NodeEditorInput) input).getStorage().getNode();
      //initSocService(rootNode, fsid, EditorUtils.LINKED_PROVIDER);
      db = rootNode.getTreeDB();
      //dbs.addAll(VirtualNode.getInstanceDBMap().keySet());
      updateTabLabel();
      sel = ((NodeEditorInput) input).getSelectedNode();
    } catch (CoreException e) {
      RLogger.logError(e);
    }
    setSite(site);
    setInput(input);
    targetNode = sel;
    getSite().setSelectionProvider(this);
    //Two handlers to deals with command's exceptions
    IHandlerService handlerService = site.getService(IHandlerService.class);
    handlerService.activateHandler("org.eclipse.ui.edit.copy", new EclipseCopyHandler());
    handlerService.activateHandler("org.eclipse.ui.edit.paste", new EclipsePasteHandler());
    
    SelectionRecord sr = new SelectionRecord(rootNode);
    SelectionManager.getOne().callSelectionUpdate(SourcePriority.SOURCE_CHANGE, EventSource.EDITOR, fsid, sr, sr, false);//to avoid updates?

    //add tree change listener
  }

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public boolean isSaveAsAllowed() {
    return false;
  }

  long cnt = 0;

  

  //  private IPartListener plistener =
//      new IPartListener() {
//        @Override
//        public void partOpened(IWorkbenchPart part) {}
//
//        @Override
//        public void partDeactivated(IWorkbenchPart part) {}
//
//        @Override
//        public void partClosed(IWorkbenchPart part) {}
//
//        @Override
//        public void partBroughtToTop(IWorkbenchPart part) {}
//
//        @Override
//        public void partActivated(IWorkbenchPart part) {
//          if (part != JSEditor.this)
//            JSEditor.this.execute("saveEditor();");
//        }
//      };
  private boolean pageLoaded;
  private boolean fbloaded;
  private Map<String, Long> cnter = new HashMap<>();
  private Set<String> rats = null;

  IUIStateChangeListener selListener = new IUIStateChangeListener() {
    
    @Override
    public void newActiveNodes(FSId source, SelectionRecord sel) {
      //TODO if is location -> change to requirement?
      TreeNode newSelection = sel.getFirstNode();
      newSelection = sel.getFirstNode();
      FSId rootSource = source.getRootId();
      if(rootSource != getFSId()&&rootSource.getEventSource()==EventSource.EDITOR)
        return;
      if(newSelection!=null && related(newSelection, rootNode)) {
        UUID selectionUUID = newSelection == null?null:newSelection.getUUId();
        if(source.getRootId() != getFSId() 
            || currentSelection==null
            || !newSelection.getSUUId().equals(currentSelection.getSUUId())) {
          setUniEditorFocus(selectionUUID, newSelection, false);
          setOutlineFocus(source, currentSelection);
        }
      }
      if(newSelection == null){
        UUID selectionUUID = null;
        // if(source.getEventSource() != fsid.getEventSource())
        //if(source.getRootId() != fsid)
        //TODO filter?
        if(currentSelection!=null) {
          setUniEditorFocus(selectionUUID, newSelection, false);
          setOutlineFocus(source, newSelection);
        }
      }
    }
  };
  @Override
  public void createPartControl(Composite parent) {
    new UndoRedoActionGroup(
            getSite(), PlatformUI.getWorkbench().getOperationSupport().getUndoContext(), true)
        .fillActionBars(getEditorSite().getActionBars());
    browser = RequalityPlugin.newBrowser(parent, this, RequalityPlugin.getBrowserType());
    browser.addMouseListener(
        new MouseListener() {
          @Override
          public void mouseUp(MouseEvent e) {}

          @Override
          public void mouseDown(MouseEvent e) {
            //JSEditor.this.setFocus();
          }

          @Override
          public void mouseDoubleClick(MouseEvent e) {}
        });
    browser.addKeyListener(new BrowserKeyListener());
    pageLoaded = false;
    fbloaded = false;
    browser.addLocationListener(LocationListener.changingAdapter(event->{
      boolean pause = true;
      synchronized (JSEditor.this) {
        if(!loadingStarted)
        {
          loadingStarted = true;
          socketStoppedCount++;
          if(socketStoppedCount==1)
            pause = true;
        }
      }
      if(pause)
        socservice.pauseDisposeTimer();
    }));
    browser.addProgressListener(
        new ProgressListener() {
          @Override
          public void completed(ProgressEvent event) {
            if (!pageLoaded && browser.getUrl().equals(url)) {
              setSelectedNode(getFSId(), currentSelection);
              pageLoaded = true;
              if (!fbloaded && CoreUtils.DEBUG_LEVEL > 0) {
                fbloaded = true;
              }
            }
            boolean unpause = false;
            synchronized (JSEditor.this) {
              if(loadingStarted) {
                loadingStarted = false;
                if(socketStoppedCount>0) {
                  socketStoppedCount--;
                }
                if(socketStoppedCount==0)
                  unpause = true;
              }
            }
            if(unpause)
              socservice.unpauseDisposeTimer();
          }

          @Override
          public void changed(ProgressEvent event) {}
        });
    //getSite().getPage().addPartListener(plistener);
//    RunnableWithMonitor rwm = new RunnableWithMonitor() {
//      @Override
//      public void run(com.unitesk.requality.core.operation.IProgressMonitor pm) {
        initSocService(rootNode, fsid, null);
        url = ResourcesProvider.getOne().formEditorURL(htmlName, rootNode, socservice.getSession(), socservice.getPort()).toString();
          
        String path = url.toString();
        FSId sid = socservice.getFSId();
        if(sid!=null) {
          SelectionManager.getOne().addSourceListener(sid.getEventSource(), sid, selListener);
        }
        String sess = socservice.getSession();
        Display.getDefault().asyncExec(new Runnable() {
          @Override
          public void run() {
            if(browser.isDisposed())
              return;
            Integer port = socservice.getPort();
            String surl = port!=null?url+"/?inTool=1&port="+port+"&document="+rootNode.getSUUId()+"&session="+sess+"&":"np";
            RLogger.logDebug("open editor on: "+surl,5);
            if(port!=null) {
              browser.setUrl(surl);
            }else
              browser.setText(NO_SOCKET_INITED);
            socservice.initSocket(sess);
          }
        });
//      }
//    };
//    JobManager.jm.startBaseJob("Init sockets for jseditor "+rootNode.getQualifiedId(), rwm, false);
    
  }

  protected boolean related(TreeNode currentSelection2, TreeNode rootNode2) {
    if(currentSelection2 == null)
      return false;
    return (currentSelection2.getQualifiedId()+"/").startsWith(rootNode2.getQualifiedId()+"/");
  }
  @Override
  public void setFocus() {
    //browser.setFocus();
    fireSelectionChange();
  }
  @Override
  public void dispose() {
    if(socservice!=null) {
      socservice.dispose(true);
      FSId sid = socservice.getFSId();
      SelectionManager.getOne().removeSourceListener(sid.getEventSource(), sid, selListener);
      SelectionManager.getOne().unregisterInstance(sid);
    }
    for (String s : cnter.keySet()) RLogger.logDebug(s + ": " + cnter.get(s),2);
    cnter.clear();
    super.dispose();
  }

  public JSEditor() {
    this(ResourcesServlet.UNIEDITOR, defaultAttrs);
  }

  public JSEditor(String module,Set<String> rats) {
    super();
    htmlName = module;
    this.rats = rats;
    //html = new File(Activator.getPath("jstools/" + module));
    or = new ObjectRule(this);
    fsid = SelectionManager.getOne().registerNewFSId(EventSource.EDITOR, this, true);
    SelectionManager.getOne().registerSelectionConverter(fsid, EventSource.EXPLORER, NoLinkKeepSelectionConverter.getOne(true));
    SelectionManager.getOne().registerSelectionConverter(fsid, EventSource.LCNF, NoLinkKeepSelectionConverter.getOne(false));
  }

//  private boolean execute(String s) {
//    if(pageLoaded)
//      return browser.execute("try{" + s + "}catch(ed){alert(ed+\"\\n\"+ ed.stack);}");
//    return false;
//  }
  
  protected void oldRefresh(final TreeNode node) {
    // Refresh nothing but root node subtree
    updateFreeTimer.cancel();
    changeUpdating(true);
    if(!socservice.relates(node))
      return;
    String rootQID = this.rootNode.getQualifiedId();
    String nodeQID = node.getQualifiedId(true);
    if (!nodeQID.startsWith(rootQID + "/") && (!nodeQID.equals(rootQID))) {
//      if (node.getType().equals("TestPurpose") && (this.rootNode.getType().equals("TestProcedure")))
//        execute("testStepUpdate('" + node.getUUId() + "');");
      //TODO replace
      return;
    }
    if (rootQID.equals(nodeQID)) updateTabLabel();
    final Browser browser = this.browser;
    final Map<UUID, NodeDesc> changedNodes = this.changedNodes;
    final Map<UUID,Integer> changedNodesCount = this.changedNodesCount;
    getSite()
        .getShell()
        .getDisplay()
        .asyncExec(
            new Runnable() {
              @Override
              public void run() {
                if (browser != null && !browser.isDisposed()) {
                  TreeNode callRefresh = null;
                  synchronized (JSEditor.this) {
                    boolean torem = false;
                    if (changedNodes.containsKey(node.getUUId())) {
                      NodeDesc nd = changedNodes.get(node.getUUId());
                      if (nd.equals(NodeDesc.fromNode(node,true))) {//TODO adds compare nodedesc -> treenode
                        Integer count = changedNodesCount.get(node.getUUId());
                        if (count > 0) {
                          changedNodesCount.put(
                              node.getUUId(), changedNodesCount.get(node.getUUId()) - 1);
                          return;
                        }
                      }
                      torem = true;
                    }
                    callRefresh = node;
                    if (torem) {
                      changedNodes.remove(node.getUUId());
                      changedNodesCount.remove(node.getUUId());
                    }
                    //  browser.forceFocus();
                  }
                  if(callRefresh!=null)
                    socservice.oldRefresh(callRefresh);
                }
              }
            });
    updateFreeTimer = new Timer();
    updateFreeTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            changeUpdating(false);
          }
        },
        300);
  }

  private void changeUpdating(boolean b) {
    isUpdating = b;
    socservice.sendChangeUpdatingState(b);
   
  }
  protected void setUniEditorFocus(final UUID uuId, TreeNode node, boolean force) {
    final Browser browser = this.browser;
    if(currentSelection==null || !currentSelection.getUUId().equals(uuId) || force) {
      getSite()
        .getShell()
        .getDisplay()
        .asyncExec(
            new Runnable() {
              @Override
              public void run() {
                if (javaDialogOpened) return;
                if (browser != null && !browser.isDisposed()) {
                  if(socservice!=null)
                    socservice.sendNewSelectedNode(currentSelection == null ? "": currentSelection.getSUUId());
                  //TODO onChangeFocus
//                  if(uuId!=null)
//                    execute("onChangeFocus('" + uuId + "');");
//                  else
//                    execute("onChangeFocus();");
                }
              }
            });
      if(node!=null)
        currentSelection = node;
      else
        currentSelection = db.getNode(uuId);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Object getAdapter(Class required) {
    if (IContentOutlinePage.class.equals(required)) {
      if (uniEditorOutlinePage == null) {
        uniEditorOutlinePage = new UniEditorOutlinePage();
        if(uniEditorOutlinePage.getFSId()!=null)
          SelectionManager.getOne().registerSelectionConverter(uniEditorOutlinePage.getFSId(), EventSource.EDITOR, new ISelectionConverter() {
            @Override
            public Pair<Boolean, SelectionRecord> convertSelection(FSId fsida, SelectionRecord sel,
                SelectionRecord oldTargSel) {
              if(!fsida.isSameAs(getFSId()))
                return new Pair<Boolean, SelectionRecord>(false, oldTargSel);
              TreeNode next = sel.getFirstNode();
              if(next!=null && related(next, rootNode)) {
                setUniEditorFocus(next!=null? next.getUUId():null, next, false);
                return new Pair<>(true, sel);
              }
              return new Pair<Boolean, SelectionRecord>(false, oldTargSel);
            }
          });
        //uniEditorOutlinePage.addSelectionChangedListener(outlineSelectionChangeListener);
        uniEditorOutlinePage.setInput(this.rootNode);
      }
      return uniEditorOutlinePage;
    }
    if (required == IPropertySheetPage.class) {
      return new TabbedPropertySheetPage(this);
    }
    if (required.equals(IContributedContentsView.class)) {
      return new IContributedContentsView() {
        public IWorkbenchPart getContributingPart() {
          return PlatformUI.getWorkbench()
              .getActiveWorkbenchWindow()
              .getActivePage()
              .findView("com.unitesk.requality.views.RequalityExplorer");
        }
      };
    }
    return super.getAdapter(required);
  }

  public Browser getBrowser() {
    return browser;
  }

  public void setEditMode(int editMode) {
    this.editMode = editMode;
  }

  private void closeEditor() {
    final Browser browser = this.browser;
    getSite()
        .getShell()
        .getDisplay()
        .asyncExec(
            new Runnable() {
              @Override
              public void run() {
                if(browser==null || browser.isDisposed())
                  return;
                getEditorSite().getPage().closeEditor(JSEditor.this, false);
              }
            });
  }

  // //////////////////////////////////////////////////////////////////////////
  //
  // Selection provider
  //
  // //////////////////////////////////////////////////////////////////////////

  private List<ISelectionChangedListener> selectionChangeListeners =
      new ArrayList<>();
  private SelectionFromSource emptySelection;

  @Override
  public void addSelectionChangedListener(ISelectionChangedListener listener) {
    selectionChangeListeners.add(listener);
  }

  @Override
  public void removeSelectionChangedListener(ISelectionChangedListener listener) {
    selectionChangeListeners.remove(listener);
  }

  @Override
  public ISelection getSelection() {
    FSId fsid = getFSId();
    if (currentSelection == null) {
      if(emptySelection == null || !emptySelection.getBaseEvent().isSameAs(fsid))
        emptySelection = new SelectionFromSource(new TreeNode[0], fsid);
      return emptySelection;
    }
    return new SelectionFromSource(currentSelection, fsid);
  }

  @Override
  public void setSelection(ISelection selection) {}

  private void fireSelectionChange() {
    ISelection sel = this.getSelection();
    SelectionChangedEvent event = new SelectionChangedEvent(this, sel);
    for (ISelectionChangedListener listener : selectionChangeListeners) {
      listener.selectionChanged(event);
    }
  }

 
  public TreeNode getRootNode() {
    return rootNode;
  }

  public boolean isDisposed() {
    return browser.isDisposed();
  }
  @Override
  public FSId getFSId() {
    if(socservice!=null)
      return socservice.getFSId();
    return null;
  }
  @Override
  public boolean isSelectionSource() {
    return true;
  }
}
