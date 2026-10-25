/*
 * Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
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

package com.unitesk.requality.eclipse.editors.browser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.navigator.CommonNavigator;
import org.eclipse.ui.operations.UndoRedoActionGroup;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.IContributedContentsView;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.ITabbedPropertySheetPageContributor;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import org.json.simple.JSONObject;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.IFSIdProvider;
import com.unitesk.requality.core.SharedConsts;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.ISelectionAdapter;
import com.unitesk.requality.core.selection.ISelectionConverter;
import com.unitesk.requality.core.selection.IUIStateChangeListener;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.service.DocumentsSocketService;
import com.unitesk.requality.core.service.ISocketMessageListener;
import com.unitesk.requality.core.service.ResourcesProvider;
import com.unitesk.requality.core.service.WSSocket;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.document.IDocumentSocketServiceStateListener;
import com.unitesk.requality.document.ILocationChangedHandler;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.ITreeDBProvider;
import com.unitesk.requality.eclipse.RequalityPlugin;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.editors.EditorUtils;
import com.unitesk.requality.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.eclipse.handlers.copypaste.EclipseCopyHandler;
import com.unitesk.requality.eclipse.handlers.copypaste.EclipsePasteHandler;
import com.unitesk.requality.eclipse.ui.cnf.RequalityCNF;
import com.unitesk.requality.eclipse.ui.cnf.SelectionFromSource;
import com.unitesk.requality.eclipse.ui.editors.IEditorWithId;
import com.unitesk.requality.eclipse.ui.editors.ISearchProvider;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;

public class ReqMarker extends EditorPart implements ITabbedPropertySheetPageContributor, ISelectionProvider,
    ITreeDBProvider, IFSIdProvider, ISelectionAdapter, IEditorWithId/*, ISearchProvider*/ {
  private Location initLoc = null;
  private static TreeNode[] NO_NODES = new TreeNode[0];
  private static final String DEFAULT_BASE_ENCODING = "UTF-8";
  public static final String ID = Consts.ReqMarkerID;
  private final ILocationChangedHandler locChangeListener = new ILocationChangedHandler() {

    @Override
    public void onLocationChanged(long time, Location loc, boolean upreq, boolean source) {
      onLocationChange(time, loc, upreq);
      fireSelectionChange(loc, true);
      if(source)
      {
          //TODO temp to sync selection
          SelectionManager.getOne().
              activateSource(SourcePriority.SOURCE_CHANGE, 
              fsid.getEventSource(),
              fsid, isSelectionSource(), true);
          }
      }
  };
  private IUIStateChangeListener selfListener = new IUIStateChangeListener() {
    @Override
    public void newActiveNodes(FSId source, SelectionRecord nodes) {
      FSId rootId = source.getRootId();
      if(rootId.getEventSource() == fsid.getEventSource() && rootId.getId()!=fsid.getId())
        return;//ignore another edi	
      SelectionManager sman = SelectionManager.getOne();
      Location loc = null;
      if (browser.docservice != null) {
        sman.removeSourceListener(fsid.getEventSource(), fsid, selfListener);
        try {
          Document doc = browser.docservice.getStartDocument();
          Collection<TreeNode> reqs = nodes.getOrDefault(Requirement.TYPE_NAME, Collections.emptyList());
          Collection<TreeNode> locs = nodes.getOrDefault(Location.TYPE_NAME, Collections.emptyList());
          if (locs.size() == 0){
            boolean haveRelated = false;
            for(TreeNode req:reqs)
              if(req.getTreeDB() == doc.getTreeDB())
                haveRelated = true;
            if(!haveRelated)
              return;
          }
          if (browser.docservice == null)
            return;
          for (TreeNode sela : locs) {
            if(browser.docservice.selLocation == sela)
              return;//already selected
          }
          browser.docservice.disableLocationChangeListener();
          Location newLoc = null;
          try {
            for (TreeNode sela : locs) {// after adaptation all tends to be good
              Location l = (Location) sela;
              loc = l;
              // if(browser.docservice.hasRelatedSessions(l.getDocument())){//TODO check fo
              removeSelectionChangedListener(null);
              setInput(new NodeEditorInput(doc, l, ID, fsid));
              setSelection(new SelectionFromSource(l, source));
              break;
              // }
            }
            browser.docservice.newLocations(doc, locs);
            fireSelectionChange(newLoc, false);
          } finally {
            browser.docservice.enableLocationChangeListener();
          }
        } finally {
          sman.addSourceListener(fsid.getEventSource(), fsid, selfListener);
        }
      }
    }

  }, cnfListener = new IUIStateChangeListener() {
    @Override
    public void newActiveNodes(FSId source, SelectionRecord nodes) {
      SelectionManager sman = SelectionManager.getOne();
      FSId rootId = source.getRootId(); 
      if(rootId.getEventSource() == fsid.getEventSource() && rootId.getId()!=fsid.getId())
        return;//ignore another edi	
      if (browser.docservice != null) {
        sman.removeSourceListener(EventSource.EXPLORER, fsid, cnfListener);
        try {
          Document doc = browser.docservice.getStartDocument();
          Collection<TreeNode> reqs = nodes.getOrDefault(Requirement.TYPE_NAME, Collections.emptyList());
          if (reqs.size() == 0)
            return;
          if (browser.docservice == null)
            return;
          Collection<TreeNode> reqs2 = new java.util.LinkedList<>(reqs);
          for(TreeNode node:reqs)
            if(Instantiator.getRootTree(node.getTreeDB())!=Instantiator.getRootTree(doc.getTreeDB())) 
              reqs2.remove(node);
          if(reqs2.size()==0)
            return;//reqs update call from another project
          reqs = reqs2;
          browser.docservice.disableLocationChangeListener();
          try {
            browser.docservice.newRequirements(doc, reqs);
          } finally {
            browser.docservice.enableLocationChangeListener();
          }
        } finally {
          sman.addSourceListener(EventSource.EXPLORER, fsid, cnfListener);
        }
      }
      // fireSelectionChange(get);
    }

  };

  // private SelectionRecord emptyRecord;
  @Override
  public Pair<SelectionRecord, SelectionRecord> adaptChange(FSId selectionSource, SelectionRecord visRecord, SelectionRecord oldVisRecord, SelectionRecord storedSel, SelectionRecord oldStoredSel){
    return new Pair<SelectionRecord, SelectionRecord>(adaptChange(selectionSource, visRecord, oldVisRecord),
                                                      adaptChange(selectionSource, storedSel, oldStoredSel));
  }
  public SelectionRecord adaptChange(FSId selectionSource, SelectionRecord record, SelectionRecord oldRecord) {
    SelectionRecord newSel = null;
    if (record == null)
      return null;
    if (record.fullSelection.isEmpty() && selectionSource.isSameAs(fsid)) {
      return SelectionRecord.EMPTY;
    }
    Collection<TreeNode> oldLocs = oldRecord == null ? Collections.emptyList(): 
      oldRecord.getOrDefault(Location.TYPE_NAME, Collections.emptyList());
    Location oldLoc = oldLocs.isEmpty()?null:(Location) oldLocs.iterator().next();//TODO multisel?
    Collection<TreeNode> locs = record.get(Location.TYPE_NAME);
    if (locs != null) {
      if(oldLoc!=null)
        for (TreeNode sela : locs)
          if(sela == oldLoc)
            return oldRecord;//adaptation is not needed
      for (TreeNode sela : locs) {
        
        Location l = (Location) sela;
        if (browser != null && browser.docservice != null
            && browser.docservice.hasRelatedSessions(l.getDocument())) {// TODO check fo
          newSel = new SelectionRecord(l);// adapt to first possible location
          break;
        }
      }
    }
    Collection<TreeNode> docs = record.get(Document.TYPE_NAME);
    if(docs!=null && docs.size()>0 && docs.iterator().next() == startDoc && newSel == null)
      return newSel;//selected document
    if (newSel != null)
      return newSel;// , false);
    return oldRecord;// , true);
  }

  protected ReqDocBrowser browser;
  //protected int pausedSocketServiceCount = 0;

  private String docQId;
  private TreeDB db;
  private Document startDoc = null;
  private int sendCode = 0;
  private FSId fsid = null;

  public ISocketMessageListener mh = new ISocketMessageListener() {

    @Override
    public boolean handleMessage(WSSocket ws, Session session, String op, String win, JSONObject obj) {
      if (browser.isDisposed() || browser.getDocService() == null || browser.getDocService().isDisposed())
        return false;
      switch (op) {
      case "tryOpenInEditor":
        String puid = startDoc.getQualifiedId();
        puid = puid.substring(0, puid.lastIndexOf("/"));

        String url = (String) obj.get("url");

        Object[] rw = CoreUtils.reworkPath(url);
        String rework = (String) rw[0];
        int up = (int) rw[2];
        TreeNode node = startDoc.getTreeDB().getNode(rework, Path.QID | Path.UV | Path.UUID);
        if (node == null) {
          while (up > 0) {
            up--;
            int pos = puid.lastIndexOf("/");
            if (pos == -1)
              RLogger.logError("incorrect path call(tends to be outside of project)");
            puid = puid.substring(0, pos);
          }
          rework = puid + "/" + rw[0];// TODO look
          rw = CoreUtils.reworkPath(rework);
          node = startDoc.getTreeDB().getNode((String) rw[0], Path.QID | Path.UV | Path.UUID);
        }
        if (node != null) {
          IEditorPart part = EditorUtils.openEditor(node);
          if (rw[1] != null && part instanceof ReqMarker) {
            ((ReqMarker) part).browser.scrollToTag((String) rw[1]);
            try {
              session.getRemote().sendString("true");// TODO incorrect for current model
            } catch (IOException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
            return true;
          }
          // TODO check
          try {
            session.getRemote().sendString("false");// TODO incorrect for current model
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
        return false;
      }
      return false;
    }

    @Override
    public String getId() {
      return fsid.getFullIdString();
    }
  };
  private ReqMarkerOutlinePage reqMarkerOutlinePage;

  public boolean findText(String aString, boolean aCaseSensitive, boolean aBackwards, boolean aWrapAround) {
    return browser.findText(aString, aCaseSensitive, aBackwards, aWrapAround);
  }

  public IUndoContext getUndoContext() {
    return PlatformUI.getWorkbench().getOperationSupport().getUndoContext();
  }

  public void openDocument(URL url) {
    DocumentsSocketService ds = browser.docservice;
    if(ds == null)
       return;
    ds.addStatusListener(new IDocumentSocketServiceStateListener() {
      @Override
      public void close() {
        EclipseAPI.getDisplay().asyncExec(new Runnable() {

          @Override
          public void run() {
            if (!ds.isDisconnectedFromView() && !ReqMarker.this.browser.isDisposed())
              ReqMarker.this.close(false);
          }

        });
      }
    });
    reqMarkerOutlinePage.setInput(this);// needs to be called after docservice init

    String path = url.toString();
    String sess = browser.setUrl(path, startDoc, fsid, EditorUtils.LINKED_PROVIDER);// TODO rewrite
    browser.initSocket(sess);
  }

  private void onLocationChange(long time, Location loc, boolean fireGlobal) {
    // TODO change to req1
    SelectionManager sman = SelectionManager.getOne();
    sman.removeSourceListener(fsid.getEventSource(), fsid, selfListener);
    try {
      TreeNode nextSel = loc;
      if(nextSel == null)
        nextSel = startDoc;
      sman.updateSelection(SourcePriority.EDITOR, fsid, nextSel);
      fireSelectionChange(loc, true);
    } finally {
      sman.addSourceListener(fsid.getEventSource(), fsid, selfListener);
    }
  }

  public ReqDocBrowser getBrowser() {
    return browser;
  }

  public ReqMarker() {
    super();
    fsid = SelectionManager.getOne().registerNewFSId(EventSource.EDITOR, this, true);
    SelectionManager selMan = SelectionManager.getOne();
    selMan.setFlag(fsid, SelectionManager.MARKUP_FLAG);
    selMan.registerSelectionAdapter(fsid, this);
    selMan.registerSelectionConverter(fsid, EventSource.EXPLORER, LocationRequirementConverter.getOne(true, true));
    selMan.registerSelectionConverter(fsid, EventSource.LCNF, LocationRequirementConverter.getOne(false, true));
  }

  int getNewSendCode() {
    if (sendCode < Integer.MAX_VALUE)
      return sendCode++;
    sendCode = Integer.MIN_VALUE;
    return sendCode;
  }

  @Override
  public void doSave(IProgressMonitor monitor) {
    monitor.beginTask("save reqmarker", 1);
    monitor.done();
  }

  @Override
  public void doSaveAs() {// TODO Auto-generated method stub
  }

  @Override
  public void init(IEditorSite site, IEditorInput input) throws PartInitException {
    try {
      setSite(site);
      page = getSite().getPage();
      Location l;
      if (input instanceof NodeEditorInput) {
        NodeEditorInput docInput = (NodeEditorInput) input;
        if (!docInput.getStorage().getNode().getType().equals(Document.TYPE_NAME)
            && !docInput.getStorage().getNode().getType().equals(Requirement.TYPE_NAME)) {
          throw new PartInitException("Wrong input type: " + docInput.getStorage().getNode().getType());
        }
        if (docInput.getStorage().getNode() instanceof Requirement) {
          Requirement req = (Requirement) docInput.getStorage().getNode();
          List<Location> locs = req.getLocations(false);
          if (locs.size() > 0) {
            startDoc = (Document) locs.get(0).getWorkParent();
            l = locs.get(0);
          } else {
            input = null;
            l = (Location) docInput.getData();
            if (l != null)
              startDoc = l.getDocument();
            else
              startDoc = null;
          }
        } else {
          startDoc = (Document) docInput.getStorage().getNode();
          // clearCaches(startDoc);
          l = (Location) docInput.getData();
        }
        if (startDoc != null) {
          db = docInput.getStorage().getNode().getTreeDB();
          docQId = startDoc.getQualifiedId();
          if (l != null)
            initLoc = l;
        }
      } else if (input instanceof FileEditorInput) {
        FileEditorInput fei = (FileEditorInput) input;

        TreeNode node = EclipseTracker.getNodeByResource(DBNamesManager.DEFAULT, fei.getFile());
        startDoc = (Document) node;
        if (startDoc != null)
          docQId = startDoc.getQualifiedId();
      }
    } catch (CoreException e) {
      e.printStackTrace();
    }finally {
      SelectionRecord sr = new SelectionRecord(startDoc);
      SelectionManager.getOne().callSelectionUpdate(SourcePriority.SOURCE_CHANGE, EventSource.EDITOR, fsid, sr, sr, false);//to avoid updates?
    }
    if (input == null)
      return;
    setInput(input);
    if (startDoc != null) {
      setPartName(startDoc.getId());
    }
    if (db == null)
      return;
    // db.addGUIListener(treeChangeListener);
    // EclipseTracker.getTracker(DBNamesManager.DEFAULT).addListener(dbStateListener);
  }

  private void initSocketService() {
    if (startDoc != null && browser != null) {
      browser.initDocService(startDoc, fsid, locChangeListener, EditorUtils.LINKED_PROVIDER, initLoc);
      if(browser.docservice!=null)
        browser.docservice.registerMessangeHandler(mh);
    }
  }

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public boolean isSaveAsAllowed() {
    return false;
  }

  @Override
  public void createPartControl(Composite parent) {
    new UndoRedoActionGroup(getSite(), getUndoContext(), true).fillActionBars(getEditorSite().getActionBars());
    parent.setLayout(new GridLayout(1, false));

    Composite browserPanel = new Composite(parent, SWT.NONE);
    browserPanel.setLayoutData(new GridData(GridData.FILL_BOTH));
    browserPanel.setLayout(new FillLayout());

    IHandlerService handlerService = getSite().getService(IHandlerService.class);
    browserPanel.addFocusListener(new FocusListener() {
      IHandlerActivation ha1, ha2;
      @Override
      public void focusLost(FocusEvent e) {
        handlerService.deactivateHandler(ha1);
        handlerService.deactivateHandler(ha2);
      }

      @Override
      public void focusGained(FocusEvent e) {
        ha1 = handlerService.activateHandler("org.eclipse.ui.edit.copy", new EclipseCopyHandler());
        ha2 = handlerService.activateHandler("org.eclipse.ui.edit.paste", new EclipsePasteHandler());
      }
    });

    browser = new ReqDocBrowser(browserPanel, this, SWT.NONE);
    
    initSocketService();
    
    setPartProperty(ID, DEFAULT_BASE_ENCODING);
//    IHandlerService handlerService = getSite().getService(IHandlerService.class);
//    handlerService.activateHandler("org.eclipse.ui.edit.copy", new EclipseCopyHandler());
//    handlerService.activateHandler("org.eclipse.ui.edit.paste", new EclipsePasteHandler());
    // Get path to file
    browser.openEditorAndChangeFocus = one->openEditorAndChangeFocus(one);
    String sdocName = startDoc == null ? "null" : startDoc.getUserVisibleName();
    JobManager.jm.startJob("Init markup editor for " + sdocName, new RunnableWithMonitor() {
      @Override
      public boolean shouldRerun() {
        return true;
      }

      @Override
      public boolean shouldRun() {
        return super.shouldRun() && ResourcesProvider.getOne().initiated;
      }

      @Override
      public void run(com.unitesk.requality.core.operation.IProgressMonitor pm) {
        Display d = EclipseAPI.getDisplay();
        if (startDoc != null &&  browser.docservice!=null) {
          URI openPath = ResourcesProvider.getOne().formDocumentURL(startDoc,
              browser.docservice.getSession(), browser.docservice.getPort());
          if (openPath == null)
            openPath = startDoc.getHTMLPath();
          try {

            URL url = openPath.toURL();
            d.syncExec(new Runnable() {
              @Override
              public void run() {
                openDocument(url);
              }
            });
          } catch (MalformedURLException e1) {
            e1.printStackTrace();
          }
        }
        getSite().setSelectionProvider(ReqMarker.this);
        SelectionManager sman = SelectionManager.getOne();
        //sman.addSourceListener(EventSource.OUTLINE, fsid, outlineListener);
        sman.addSourceListener(fsid.getEventSource(), fsid, selfListener);
        sman.addSourceListener(EventSource.EXPLORER, fsid, cnfListener);
      }
    }, false);
  }

  private void openEditorAndChangeFocus(TreeNode doc2){// TODO Auto-generated method stub
    SelectionManager selMan = SelectionManager.getOne();
    selMan.removeSourceListener(fsid.getEventSource(), fsid, selfListener);
    selMan.removeSourceListener(EventSource.EXPLORER, fsid, cnfListener);
    try {
      IEditorPart part = EditorUtils.openEditor(doc2);
      if(part instanceof IFSIdProvider) {
        IFSIdProvider prov = (IFSIdProvider) part;
        FSId id = prov.getFSId();
        SelectionRecord sr = new SelectionRecord(doc2);
        selMan.callSelectionUpdate(SourcePriority.EDITOR,
          id.getEventSource(), id, sr, sr, prov.isSelectionSource());
        selMan.changeActiveSource(SourcePriority.EDITOR, 
          id.getEventSource(), id, prov.isSelectionSource(), true);
        part.getSite().getPage().bringToTop(part);
      }
    }finally {
      selMan.addSourceListener(EventSource.EXPLORER, fsid, cnfListener);
      selMan.addSourceListener(fsid.getEventSource(), fsid, selfListener);
    }
  }

  private boolean allow_popup_menu;
  private IWorkbenchPage page;

  // TODO: refactor - move to helpers class
  public static void copyFile(File in, File out) throws IOException {
    FileInputStream fis = new FileInputStream(in);
    FileOutputStream fos = new FileOutputStream(out);
    try {
      byte[] buf = new byte[1024];
      int i = 0;
      while ((i = fis.read(buf)) != -1) {
        fos.write(buf, 0, i);
      }
    } catch (IOException e) {
      throw e;
    } finally {
      fis.close();
      fos.close();
    }
  }

  public void setFocus(long time) {
    if (browser.isDisposed())
      return;
    browser.setFocus();
    if (reqMarkerOutlinePage != null && reqMarkerOutlinePage.getReqMarker() != this) {
      reqMarkerOutlinePage.setInput(this);
    }
  }

  @Override
  public void setFocus() {
    setFocus(0);
  }

  public TreeDB getTreeDB() {
    return db;
  }

  public Document getStartDocument() {
    return startDoc;
  }

  public void close(boolean save) {
    page.closeEditor(this, save);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object getAdapter(@SuppressWarnings("rawtypes") Class required) {
    if (required.equals(this.getClass())) {
      return this;
    }
    if (required.equals(ITextEditor.class)) {
      return null;
    }
    if (IContentOutlinePage.class.equals(required)) {
      if (reqMarkerOutlinePage == null) {
        reqMarkerOutlinePage = new ReqMarkerOutlinePage(this);
        SelectionManager sman = SelectionManager.getOne();
        sman.registerSelectionConverter(reqMarkerOutlinePage.getFSId(), EventSource.EDITOR, new ISelectionConverter() {
          @Override
          public Pair<Boolean, SelectionRecord> convertSelection(FSId fsida, SelectionRecord sel,
              SelectionRecord oldTargSel) {
            if(!fsida.isSameAs(getFSId()))
              return new Pair<Boolean, SelectionRecord>(false, oldTargSel);
            Collection<TreeNode> sela = sel.getOrDefault(Location.TYPE_NAME, Collections.emptyList());
            if (sela.size() == 0)
              return new Pair<Boolean, SelectionRecord>(false, oldTargSel);
            for (TreeNode selt : sela) {
              Location l = (Location) selt;
              if (l == null)
                continue;
              if (browser.docservice!=null && browser.docservice.hasRelatedSessions(l.getDocument())) {
                browser.docservice.setTargetLocation(l);
                return new Pair<Boolean, SelectionRecord>(true, sel);
              }
            }
            return new Pair<Boolean, SelectionRecord>(false, oldTargSel);
          }
        });
//        SelectionManager.getOne().addSourceListener(EventSource.OUTLINE, fsid, outlineListener);
        // reqMarkerOutlinePage.addSelectionChangedListener(locChangeListener);
      }
      return reqMarkerOutlinePage;
    }

    if (required == IPropertySheetPage.class) {
      return new TabbedPropertySheetPage(this);
    }
    if (required == IContributedContentsView.class) {
      return new IContributedContentsView() {
        public IWorkbenchPart getContributingPart() {
          return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
              .findView("com.unitesk.requality.views.RequalityExplorer");
        }
      };
    }

    return super.getAdapter(required);
  }

  @Override
  public String getContributorId() {
    return "com.unitesk.requality.views.RequalityPropertyContributor";
  }

  // //////////////////////////////////////////////////////////////////////////
  //
  // Selection provider
  //
  // //////////////////////////////////////////////////////////////////////////
  private List<ISelectionChangedListener> selectionChangeListeners = new ArrayList<>();

  @Override
  public void addSelectionChangedListener(ISelectionChangedListener listener) {
    if (listener != null) {
      selectionChangeListeners.add(listener);
    }
  }

  @Override
  public void removeSelectionChangedListener(ISelectionChangedListener listener) {
    if (listener != null) {
      selectionChangeListeners.remove(listener);
    }
  }

  private static SelectionFromSource emptySelection;

  @Override
  public ISelection getSelection() {
    if (browser.docservice != null)
      return getSelection(browser.docservice.selLocation);
    return getSelection(null);
  }

  public ISelection getSelection(Location loc) {
    // String locationId = null;
    if (browser.docservice != null && loc != null) {
      return new SelectionFromSource(loc, fsid);
    } else {
      // TODO filter locations?
      if (emptySelection == null || !emptySelection.getBaseEvent().isSameAs(fsid))
        emptySelection = new SelectionFromSource(new TreeNode[0], fsid);
      return emptySelection;
    }
  }

  @Override
  public void setSelection(ISelection selection) {
    // TODO remove
    RLogger.logDebug("setSelection: " + selection);
  }

  public void fireSelectionChange(Location loc, boolean sync) {
    Runnable r = new Runnable() {
      @Override
      public void run() {
        ISelection sel = ReqMarker.this.getSelection(loc);
        SelectionChangedEvent event = new SelectionChangedEvent(ReqMarker.this, sel);
        for (ISelectionChangedListener listener : selectionChangeListeners) {
          listener.selectionChanged(event);
        }
      }
    };
    if(sync)
      EclipseAPI.getDisplay().syncExec(r);
    else
      EclipseAPI.getDisplay().asyncExec(r);
  }

  public void synchWithExplorer() {
    // synchronization with Requality Explorer
    IViewPart view = Activator.getDefault().getWorkbench().getActiveWorkbenchWindow().getActivePage()
        .findView(RequalityCNF.ID);
    if (view instanceof CommonNavigator) {
      CommonNavigator navigator = (CommonNavigator) view;
      SharedConsts.isNavigatorLinkingEnabled = navigator.isLinkingEnabled();
      if (navigator.isLinkingEnabled()) {
        navigator.setLinkingEnabled(true);
      }
    }
  }

  // //////////////////////////////////////////////////////////////////////////
  //
  // Undo/Redo support
  //
  // //////////////////////////////////////////////////////////////////////////
  public boolean redo(final String jsData) throws UnsupportedEncodingException, CoreException {
    // TODO proper frame undo redo
    ReqDocBrowser docBrowser = pullBrowser();
    IJSCommand redo = new IJSCommand() {
      @Override
      public void onError(Exception e) {
        if(docBrowser.docservice!=null)
          fireSelectionChange(docBrowser.docservice.selLocation, false);
        e.printStackTrace();
      }

      @Override
      public void onComplete(Object result, ReqDocBrowser docBrowser) {
        if(docBrowser.docservice!=null)
          fireSelectionChange(docBrowser.docservice.selLocation, false);
      }

      @Override
      public String getJS() {
        return "return redoAction(" + jsData + ");";
      }

      @Override
      public boolean isEvaluate() {
        return true;
      }
    };
    docBrowser.scheduleJSCommand(redo);
    return true;
  }

  public boolean undo(final String jsData) throws UnsupportedEncodingException, CoreException {
    ReqDocBrowser docBrowser = pullBrowser();
    IJSCommand undo = new IJSCommand() {
      @Override
      public void onError(Exception e) {
        fireSelectionChange(docBrowser.docservice.selLocation, false);
        e.printStackTrace();
      }

      @Override
      public void onComplete(Object result, ReqDocBrowser docBrowser) {
        fireSelectionChange(docBrowser.docservice.selLocation,false);
      }

      @Override
      public String getJS() {
        return "return undoAction(" + jsData + ");";
      }

      @Override
      public boolean isEvaluate() {
        return true;
      }
    };
    docBrowser.scheduleJSCommand(undo);
    return true;
  }

  private ReqDocBrowser pullBrowser() throws PartInitException {
    ReqDocBrowser docBrowser = getBrowser();
    if (docBrowser.isDisposed()) {
      ReqMarker reqMarker;
      IEditorReference eref = RequalityPlugin.findMarkEditorForDocument(docQId);
      if (eref != null) {
        reqMarker = (ReqMarker) eref.getEditor(true);
      } else {
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        reqMarker = (ReqMarker) IDE.openEditor(page, getEditorInput(), ReqMarker.ID, false);
      }
      reqMarker.getSite().getPage().bringToTop(reqMarker);
      docBrowser = reqMarker.getBrowser();
    }
    docBrowser.setFocus();
    return docBrowser;
  }

  @Override
  public void dispose() {
    if (browser != null)
      browser.dispose();
    if(browser.docservice!=null)
      browser.docservice.locationChangedHandler = null;
    super.dispose();
    getSite().setSelectionProvider(null);
    SelectionManager sman = SelectionManager.getOne();
    sman.removeSourceListener(fsid.getEventSource(), fsid, selfListener);
    sman.removeSourceListener(EventSource.EXPLORER, fsid, cnfListener);
    sman.unregisterInstance(fsid);
  }

  public Collection<TreeNode> getDocuments() {
    if (browser == null || browser.docservice == null || browser.docservice.resourceNodeToSocks == null)
      return Arrays.asList(new Document[] { startDoc });
    Collection<TreeNode> docs = new HashSet<TreeNode>();
    synchronized (browser.docservice.resourceNodeToSocks) {
      docs.addAll(browser.docservice.resourceNodeToSocks.keySet());
    }
    docs.add(startDoc);
    return docs;
  }

  @Override
  public FSId getFSId() {
    return fsid;
  }

  @Override
  public String getEditorKindId() {
    return ID;
  }

  @Override
  public boolean isSelectionSource() {
    return true;
  }
  /*
  @Override
  public String getSelectionText() {
    if (browser == null || browser.docservice == null || browser.docservice.resourceNodeToSocks == null)
      return "";
    return (String) browser.getWebBrowser().evaluate("return window.getSelection().toString()", true);
  }*/
}
