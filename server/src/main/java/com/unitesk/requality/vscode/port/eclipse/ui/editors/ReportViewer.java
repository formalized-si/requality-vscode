package com.unitesk.requality.vscode.port.eclipse.ui.editors;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Browser;
import com.unitesk.requality.vscode.ui.swt.browser.LocationEvent;
import com.unitesk.requality.vscode.ui.swt.browser.LocationListener;
import com.unitesk.requality.vscode.ui.swt.browser.ProgressEvent;
import com.unitesk.requality.vscode.ui.swt.browser.ProgressListener;
import com.unitesk.requality.vscode.ui.swt.events.FocusEvent;
import com.unitesk.requality.vscode.ui.swt.events.FocusListener;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.IEditorInput;
import com.unitesk.requality.vscode.ui.workbench.IEditorSite;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.commands.IHandlerActivation;
import com.unitesk.requality.vscode.ui.commands.IHandlerService;
import com.unitesk.requality.vscode.ui.workbench.EditorPart;
import com.unitesk.requality.vscode.ui.workbench.IContributedContentsView;
import com.unitesk.requality.vscode.ui.workbench.IPropertySheetPage;
import com.unitesk.requality.vscode.ui.tabbed.ITabbedPropertySheetPageContributor;
import com.unitesk.requality.vscode.ui.tabbed.TabbedPropertySheetPage;

import com.unitesk.requality.core.IFSIdProvider;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.service.ReportSocketService;
import com.unitesk.requality.core.service.ResourcesProvider;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ITreeStateListener;
import com.unitesk.requality.document.IDocumentSocketServiceStateListener;
import com.unitesk.requality.document.ILinkedStateProvider;
import com.unitesk.requality.vscode.port.eclipse.ITreeDBProvider;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.vscode.port.eclipse.handlers.copypaste.EclipseCopyHandler;
import com.unitesk.requality.vscode.port.eclipse.handlers.copypaste.EclipsePasteHandler;
import com.unitesk.requality.vscode.port.eclipse.handlers.report.OpenReportHandler;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.SelectionFromSource;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tool.CoreUtils;

// TODO: refacatoring
public class ReportViewer extends EditorPart
    implements ISelectionProvider, ITreeDBProvider, IFSIdProvider, IEditorWithId {
  private static final String NO_SOCKET_INITED =
      "<html><body><h3>Socket for report preview is not inited</h3></body></html>";
  public static String ID = "com.unitesk.requality.editors.browser.ReportViewer";
  private ReportData rdata;
  private Browser browser = null;
  private boolean firstUrlChange = true;
  // private String href;
  private FSId fsid;
  private ReportSocketService repservice;
  private int socketStoppedCount = 0;
  private boolean loadTriggered = false;
  private boolean loadingWasInited = false;

  ITreeStateListener treeStateListener =
      new ITreeStateListener() {
        @Override
        public void open(TreeDB db) {}

        @Override
        public void delete(String lname, String pname, TreeDB db) {
          final Browser browser = ReportViewer.this.browser;
          if (rdata.getTreeDB() == db) {
            getSite()
                .getShell()
                .getDisplay()
                .asyncExec(
                    new Runnable() {
                      @Override
                      public void run() {
                        if (browser == null || browser.isDisposed()) return;
                        getEditorSite().getPage().closeEditor(ReportViewer.this, false);
                      }
                    });
          }
        }

        @Override
        public void close(TreeDB db) {
          final Browser browser = ReportViewer.this.browser;
          if (rdata.getTreeDB().equals(db)) {
            ReportViewer.this.close();
          }
        }

        @Override
        public void settingsChanged(TreeDB db) {
          // TODO Auto-generated method stub

        }
      };

  INodeChangeListener nodeChangeListener =
      new INodeChangeListener() {

        @Override
        public void deleted(TreeNode source) {
          final Browser browser = ReportViewer.this.browser;
          if (source.getUUId().equals(rdata.getUUId()))
            getSite()
                .getShell()
                .getDisplay()
                .asyncExec(
                    new Runnable() {
                      @Override
                      public void run() {
                        if (browser == null || browser.isDisposed()) return;
                        getEditorSite().getPage().closeEditor(ReportViewer.this, false);
                      }
                    });
        }

        @Override
        public void attributeChange(TreeNode node, String what, Attribute old) {
          if (what.equals(ReportSettings.ATTR_TEMPLATE) || what.equals(TreeNode.ATTR_UNDEFINED)) {
            if (!rdata.getQualifiedId().equals(node.getQualifiedId())) {
              return;
            }
            TreeDB db = rdata != null ? rdata.getTreeDB() : null;
            TemplatesManager tm = TemplatesManager.getOne(db);
            Template tpl = tm.loadTemlInfo(rdata, rdata.getTemplateId());
            String path = null;
            // get first
            if (rdata.getTemplateId() != null && tpl == null)
              MessageDialog.openError(
                  PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                  "Report generation",
                  "Report template '"
                      + rdata.getTemplateId()
                      + "' for this report is not avalible.");
            String val = node.attrs().getStringAttributeValue(ReportData.ATTR_LAST_GENERATED);
            if (val != null)
              path = ((ReportData) node).getSaveDir() + "/" + CoreUtils.URLEncode(val);
            if (path == null) {
              path = CoreUtils.URLEncode(tm.getTplFileName((ReportData) node, false, null));
            }
            final Browser browser = ReportViewer.this.browser;

            final ReportData rdata = ReportViewer.this.rdata;
            final IResource res =
                OpenReportHandler.getReportFolder((ReportData) node)
                    .getFile(Path.fromOSString(URI.create(path).toString()));
            if (res.exists()) {
              getSite()
                  .getShell()
                  .getDisplay()
                  .asyncExec(
                      new Runnable() {
                        @Override
                        public void run() {
                          OpenReportHandler.openReport(rdata);
                          if (!browser.isDisposed()) browser.refresh();
                        }
                      });
            } else {
              getSite()
                  .getShell()
                  .getDisplay()
                  .asyncExec(
                      new Runnable() {
                        @Override
                        public void run() {
                          if (browser == null || browser.isDisposed()) return;
                          getEditorSite().getPage().closeEditor(ReportViewer.this, false);
                        }
                      });
            }
          }
        }

        @Override
        public void moved(TreeNode source, TreeNode oldParent) {
          // TODO Auto-generated method stub

        }

        @Override
        public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
          if (what.contains(ReportSettings.ATTR_TEMPLATE) || what.contains(TreeNode.ATTR_UNDEFINED))
            attributeChange(
                source, ReportSettings.ATTR_TEMPLATE, what.getOld(ReportSettings.ATTR_TEMPLATE));
        }

        @Override
        public void fireNodeResourceChange(TreeNode treeNode, String resName, Moment m) {
          // TODO Auto-generated method stub

        }
      };

  private String reportName;
  private IWorkbenchPage page;

  public ReportViewer() {
    super();
    fsid = SelectionManager.getOne().registerNewFSId(EventSource.EDITOR, this, true);
  }

  @Override
  public void doSave(IProgressMonitor monitor) {}

  @Override
  public void doSaveAs() {}

  @Override
  public void init(IEditorSite site, IEditorInput input) throws PartInitException {
    setSite(site);
    page = site.getPage();

    setInput(input);
    try {
      rdata = (ReportData) ((NodeEditorInput) input).getStorage().getNode();
      reportName = rdata.getId();
      SelectionRecord sr = new SelectionRecord(rdata);
      SelectionManager.getOne()
          .callSelectionUpdate(
              SourcePriority.SOURCE_CHANGE,
              EventSource.EDITOR,
              fsid,
              sr,
              sr,
              false); // to avoid updates?
    } catch (CoreException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    getSite().setSelectionProvider(this);

    fireSelectionChange();
  }

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public boolean isSaveAsAllowed() {
    return false;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public Object getAdapter(Class required) {
    if (required == IPropertySheetPage.class) {
      TabbedPropertySheetPage sheet =
          new TabbedPropertySheetPage(
              new ITabbedPropertySheetPageContributor() {
                @Override
                public String getContributorId() {
                  return "com.unitesk.requality.views.RequalityPropertyContributor";
                }
              });
      return sheet;
    }
    if (required.equals(IContributedContentsView.class)) {
      return new IContributedContentsView() {
        public IWorkbenchPart getContributingPart() {
          IWorkbenchPart part =
              PlatformUI.getWorkbench()
                  .getActiveWorkbenchWindow()
                  .getActivePage()
                  .findView("com.unitesk.requality.views.RequalityExplorer");
          if (part == null || part.getSite() == null) return null;
          part.getSite().getSelectionProvider().setSelection(new StructuredSelection(rdata));
          return part;
        }
      };
    }
    return super.getAdapter(required);
  }

  public void initRepService(TreeNode doc, FSId fcid, ILinkedStateProvider st) {
    final ReportSocketService fservice = new ReportSocketService(doc, fcid, st);
    repservice = fservice;
    fservice.addStatusListener(
        new IDocumentSocketServiceStateListener() {
          @Override
          public void close() {
            EclipseAPI.getDisplay()
                .asyncExec(
                    new Runnable() {
                      @Override
                      public void run() {
                        if (!fservice.isDisconnectedFromView())
                          setPartName("Report (Disconnected): " + reportName);
                        else setPartName("Report (Static): " + reportName);
                        if(fservice == repservice)
                          repservice = null; // deinit
                        //              ReportViewer.this.close();
                      }
                    });
          }
        });
    boolean pauseSocket = false;
    synchronized (ReportViewer.this) {
      socketStoppedCount++;
      pauseSocket = true;
    }
    if (pauseSocket) fservice.pauseDisposeTimer();
  }

  protected void close() {
    Display.getDefault()
        .asyncExec(
            new Runnable() {

              @Override
              public void run() {
                if (!browser.isDisposed()) {
                  try {
                    browser.close();
                  } catch (Exception e) {
                    RLogger.logError(e);
                  }
                }
                Display.getDefault()
                    .asyncExec(
                        new Runnable() {

                          @Override
                          public void run() {
                            if (page.getWorkbenchWindow() == null
                                || page.getWorkbenchWindow().isClosing()) return;
                            page.closeEditor(ReportViewer.this, false);
                          }
                        });
              }
            });
  }

  private String lastHref = null;
  private ProgressListener progressListener =
      new ProgressListener() {
        @Override
        public void completed(ProgressEvent event) {
          boolean unpause = false, stopLoading = false;;
          synchronized (ReportViewer.this) {
           if (loadTriggered) {
              if ((socketStoppedCount) > 0) {
                socketStoppedCount--;
                unpause = true;
              }
              loadTriggered = false;
              stopLoading = true;
            }
          }
          
          if(stopLoading)
            try {
              RLogger.logDebug("Page load completed " + event.time, 5);
              fireSelectionChange();
              dropLoadingGif();
            } finally {
              //                  }
//              browser.removeProgressListener(this);
            }
          if (unpause) repservice.unpauseDisposeTimer();
        }

        @Override
        public void changed(ProgressEvent event) {
        }
      };

  @Override
  public void createPartControl(Composite parent) {
    setPartName("Report (Init): " + reportName);
    browser = RequalityPlugin.newBrowser(parent, this, RequalityPlugin.getBrowserType());
    IHandlerService handlerService = getSite().getService(IHandlerService.class);
    parent.addFocusListener(
        new FocusListener() {
          IHandlerActivation ha1, ha2;

          @Override
          public void focusLost(FocusEvent e) {
            handlerService.deactivateHandler(ha1);
            handlerService.deactivateHandler(ha2);
          }

          @Override
          public void focusGained(FocusEvent e) {
            ha1 =
                handlerService.activateHandler(
                    "org.eclipse.ui.edit.copy", new EclipseCopyHandler());
            ha2 =
                handlerService.activateHandler(
                    "org.eclipse.ui.edit.paste", new EclipsePasteHandler());
          }
        });
    TreeDB db = rdata.getTreeDB();
    TemplatesManager tm = TemplatesManager.getOne(db);
    Template tpl = tm.getTemplate(rdata);
    URI openPath = rdata.getSaveDir();
    String href = null;
    try {
      if (rdata.getTemplateId() != null) {
        String lastGen = rdata.attrs().getStringAttributeValue(ReportSettings.ATTR_LAST_GENERATED);
        if (lastGen != null && isHTMLName(lastGen)) initRepService(rdata, fsid, null);
        else {
          if (lastGen == null) lastGen = "REPORT_NOT_GENERATED";
          else setPartName("Report: " + reportName);
        }
        URI np = ResourcesProvider.getOne().formReportsURL(rdata, getSession(), getPort());
        if (np != null) openPath = np;
        href = (new URI(openPath + CoreUtils.URLEncode(lastGen))).toString();
      }
      if (tpl == null) {
        tpl = tm.getTemplate(Template.DEFAULT_ID);
      }
      if (tpl != null && href == null) {
        String lastGen = tm.getTplFileName(rdata, false, null);
        if (isHTMLName(lastGen)) initRepService(rdata, fsid, null);
        URI np = ResourcesProvider.getOne().formReportsURL(rdata, getSession(), getPort());
        if (np != null) openPath = np;
        href = new URI(openPath + CoreUtils.URLEncode(lastGen)).toString();
      }
    } catch (URISyntaxException e) {
      RLogger.logDebug(CoreUtils.getTrace(e));
    }
    if (href != null) {
      String sess = "0";
      Integer port = 0;
      if (repservice != null) {
        sess = repservice.getSession();
        port = repservice.getPort();
      }
      if (port != null) {
        String fhref = href;
        LocationListener permitter =
            LocationListener.changingAdapter(
                event -> {
                  boolean properServer =
                      lastHref == null
                          || isServerRelated(event.location) && isSameUrl(event.location, lastHref);
                  if (event.doit)
                    event.doit =
                        properServer
                            || decodedPathWithoutArgs(event.location)
                                .equals(decodedPathWithoutArgs(lastHref));
                  if (properServer && event.location.toLowerCase().contains(".htm")) {
                    if (!isSamePage(event.location, lastHref) || firstUrlChange) {
                      boolean loadingTriggered = false;
                      if (firstUrlChange || !loadTriggered) {
                        if(firstUrlChange)
                          firstUrlChange = false;
                        else {//very first url change already set by socservice
                          synchronized (ReportViewer.this) {
                            socketStoppedCount++;
                          }
                          repservice.pauseDisposeTimer();
                        }
                        loadTriggered = true;
                        loadingTriggered = true;
                      }
                      if (loadingTriggered) {
                        addLoadingGif();
                      }
                      lastHref = event.location;
                    }
                  }
                });

        browser.addProgressListener(progressListener);
        
        browser.addLocationListener(permitter);
        int fport = port;
        String fsess = sess;

        rdata.addChangeListener(nodeChangeListener);
        browser.addLocationListener(
            new LocationListener() {

              @Override
              public void changing(LocationEvent event) {
                RLogger.logDebug(
                    "Location changing " + event.location + " " + event.time + " " + event.top, 5);
                if (!isServerRelated(event.location) || !isSameUrl(event.location, lastHref)) {
                  RLogger.logDebug("deny loading " + event.location);
                  event.doit = false;
                }
              }

              @Override
              public void changed(LocationEvent event) {
                RLogger.logDebug("Location changed " + event.location + " " + event.time, 5);
              }
            });
        browser
            .getDisplay()
            .asyncExec(
                new Runnable() {
                  @Override
                  public void run() {
                    if (!browser.isDisposed()) {
                      lastHref =
                          fhref
                              + "?inTool=1&port="
                              + fport
                              + "&document="
                              + rdata.getSUUId()
                              + "&session="
                              + fsess
                              + "&";
                      browser.setUrl(lastHref);
                    }
                  }
                });

      } else browser.setText(NO_SOCKET_INITED);

      EclipseTracker.getTracker(DBNamesManager.DEFAULT)
          .addListener(Moment.AFTER, treeStateListener);
    }
  }

  private boolean isSamePage(String location, String url) { // TODO Auto-generated method stub
    if (location == null || url == null) return false;
    try {
      URI uri1 = URI.create(location);
      URI uri2 = URI.create(url);
      URI uri1m =
          new URI(
              uri1.getScheme(),
              uri1.getUserInfo(),
              uri1.getHost(),
              uri1.getPort(),
              uri1.getRawPath(),
              uri1.getRawQuery(),
              null);
      URI uri2m =
          new URI(
              uri2.getScheme(),
              uri2.getUserInfo(),
              uri2.getHost(),
              uri2.getPort(),
              uri2.getRawPath(),
              uri2.getRawQuery(),
              null);
      return uri1m.equals(uri2m);
    } catch (Exception e) {
    }
    if (location != null && url != null)
      return location.equals(url); // fallback for sites like about://
    return false;
  }

  protected boolean isSameUrl(String u1s, String u2s) {
    if (u1s == null || u2s == null) return false;
    URI u1 = (toSafeURI(u1s)).normalize();
    URI u2 = (toSafeURI(u2s)).normalize();
    return u1.getAuthority().equals(u2.getAuthority());
  }

  protected boolean isServerRelated(String location) {
    URI uri = (toSafeURI(location)).normalize();
    String pathPart = uri.getPath();
    if (pathPart.startsWith("/" + ResourcesProvider.RANDOM_STRING)) return true;
    return false;
  }
  private static final Pattern URI_INVALID_CHARS = Pattern.compile("[^A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=]");
  private URI toSafeURI(String location){// TODO Auto-generated method stub
    Matcher m = URI_INVALID_CHARS.matcher(location);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
        String ch = m.group();
        byte[] bytes = ch.getBytes(StandardCharsets.UTF_8);
        StringBuilder enc = new StringBuilder();
        for (byte b : bytes) {
            enc.append(String.format("%%%02X", b));
        }
        m.appendReplacement(sb, enc.toString());
    }
    m.appendTail(sb);
    return URI.create(sb.toString());
  }

protected String decodedPathWithoutArgs(String location) {
    try {
      URI uri = URI.create(location).normalize();
      uri =
          new URI(
              uri.getScheme(),
              uri.getAuthority(),
              uri.getPath(),
              null, // Ignore the query part of the input url
              uri.getFragment());
      return uri.toString();
    } catch (URISyntaxException e) {
      RLogger.logError(e);
    }
    return location;
  }

  private boolean isHTMLName(String lastGen) {
    lastGen = lastGen.toLowerCase();
    return lastGen.endsWith(".htm") || lastGen.endsWith(".html");
  }

  private String getSession() {
    if (repservice != null) return repservice.getSession();
    return "";
  }

  private Integer getPort() {
    if (repservice != null) return repservice.getPort();
    return -1;
  }
  private int loading = 0;

  protected void addLoadingGif() {
    RLogger.logDebug("Add loading " + loading, 5);
    loading++;
    if (loading == 1) {
      browser.getParent().setCursor(browser.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
//      if (repservice != null) repservice.pauseDisposeTimer();
      setPartName("Report (Loading): " + reportName);
    }
  }

  protected void dropLoadingGif() {
    RLogger.logDebug("Drop loading " + loading, 5);
    loading--;
    if (loading == 0) {
      browser.getParent().setCursor(null);
//      if (repservice != null) repservice.unpauseDisposeTimer(); // drops
      setPartName("Report: " + reportName);
      browser.getParent().layout(true);
    }
  }

  @Override
  public void dispose() {
    rdata.removeChangeListener(nodeChangeListener);
    EclipseTracker.getTracker(DBNamesManager.DEFAULT)
        .removeListener(Moment.AFTER, treeStateListener);
    super.dispose();
    SelectionManager.getOne().unregisterInstance(fsid);
    if (repservice != null) repservice.dispose(true);
  }

  @Override
  public void setFocus() {
    if (browser != null && !browser.isDisposed()) browser.setFocus();
    fireSelectionChange();
  }

  private List<ISelectionChangedListener> selectionChangeListeners = new ArrayList<>();

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
    return new SelectionFromSource(rdata, fsid);
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

  @Override
  public TreeDB getTreeDB() {
    if (rdata != null) return rdata.getTreeDB();
    return null;
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
    // TODO Auto-generated method stub
    return true;
  }
}
