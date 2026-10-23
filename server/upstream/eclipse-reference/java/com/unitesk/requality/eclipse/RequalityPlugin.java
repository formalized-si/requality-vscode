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
package com.unitesk.requality.eclipse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.Bundle;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.checker.INodeChecker;
import com.unitesk.requality.core.descriptors.NodeTypeDescriptor;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseCheckerContainer;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.eclipse.editors.browser.ReqMarker;
import com.unitesk.requality.eclipse.ui.editors.JSEditor;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.RequalityCLI;
import com.unitesk.requality.tool.message.RMessages;

public class RequalityPlugin {
  private static FSId fromPlugin = SelectionManager.getOne().registerNewFSId(EventSource.SYSTEM, null, false);//TODO change?
  public static final int NOT_INITED=-42;
  public static final int WEBKIT = 1 << 16;
  private static Object sync = "requalitypluginsync";
  private static Map<String, NodeTypeDescriptor> nodeTypes;
  private static int BROWSER_TYPE = NOT_INITED;
  public static final String ID = "com.unitesk.requality";

  private static boolean enginesInitiated = false;
  private static SortedMap<Integer, String> browser_engines = new TreeMap<>();
  private static SortedMap<Integer, Short> browser_engines_state = new TreeMap<>();

  private static void initXULRunner() {
    // String
    // bundleName="org.mozilla.xulrunner."+System.getProperty("osgi.ws")+"."+System.getProperty("osgi.os")+"."+System.getProperty("osgi.arch");
    String bundleName = "org.mozilla.xulrunner";
    //                + System.getProperty("osgi.ws") + "."
    //                + System.getProperty("osgi.os") + "."
    //            + System.getProperty("osgi.arch");
    //        Platform.getBundles("org.mozilla.xulrunner*","*");
    Bundle bundle = Platform.getBundle(bundleName); //$NON-NLS-1$
    if (bundle != null) {
      Enumeration<URL> resourceUrl = bundle.findEntries(".", "xulrunner", false); //$NON-NLS-1$
      if (resourceUrl != null) {
        try {
          URL fileUrl = FileLocator.toFileURL(resourceUrl.nextElement());
          File file = new File(fileUrl.toURI());
          System.setProperty(
              "org.eclipse.swt.browser.XULRunnerPath", file.getAbsolutePath()); //$NON-NLS-1$
        } catch (IOException e) {
          e.printStackTrace();
        } catch (URISyntaxException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static void initBrowserEngines() {
    if(Platform.getOS().equals(Platform.WS_WIN32))
      BROWSER_TYPE = SWT.EDGE;
    else
      BROWSER_TYPE = SWT.NONE;//SWT.CHROMIUM;
  }

  // Replace with standard tools?

  protected static Composite detectShell(Display d) {
    Composite s = d.getActiveShell();
    if(s == null){
      Shell[]sh = d.getShells();
      for(int i = 0;i<sh.length;i++)
        if(sh[i].isVisible())
          return sh[i];
    }
    return s;
  }

  public static EclipseTracker getTreesTracker(DBNamesManager dbnm) {
    return EclipseTracker.getTracker(dbnm);
  }

  private static short isBrowserAvailable(int type, Composite parent) {
    try {
      Browser browser = new Browser(parent, type);
      browser.dispose();
      return 1;

    } catch (SWTError er) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintWriter pw = new PrintWriter(baos);
      er.printStackTrace(pw);
      pw.close();
      try {
        baos.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      RLogger.logDebug(baos.toString(),10);
      return 0;
    }
  }

  public static IEditorReference findMarkEditorForDocument(String docQId) throws PartInitException {
    try {
      IWorkbench w = PlatformUI.getWorkbench();
      if (w == null) return null;
      List<IEditorReference> editors = new ArrayList<IEditorReference>();
      for (IWorkbenchWindow ww : PlatformUI.getWorkbench().getWorkbenchWindows()) {
          for (IWorkbenchPage page : ww.getPages()) {
              for (IEditorReference eRef : page.getEditorReferences()) {
                 if (!(eRef.getEditor(false) instanceof ReqMarker)) {
                  continue;
                }
                ReqMarker marker = (ReqMarker) eRef.getEditor(true);
                TreeDB db = marker.getTreeDB();
                if (db.isVirtual()) db = Instantiator.getRootTree(db);
                //if (!project.equals(TreesTracker.getTracker().getProject(db))) {
                //  continue;
                //}
                for(TreeNode doc:marker.getDocuments()) {
                  if(doc.getQualifiedId().equals(docQId))
                    return eRef;
                }
              }
          }
      }
      //IEditorReference[] eRefs = ww.getActivePage().getEditorReferences();
      
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public static IEditorReference findJSEditorForNode(String nodeQId, IProject project)
      throws PartInitException, CoreException {
    IEditorReference[] eRefs =
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getEditorReferences();
    for (IEditorReference eRef : eRefs) {
      if (!(eRef.getEditor(false) instanceof JSEditor)) {
        continue;
      }
      JSEditor jsEditor = (JSEditor) eRef.getEditor(true);
      if (!(jsEditor.getEditorInput() instanceof NodeEditorInput)) {
        continue;
      }
      NodeEditorInput editorInput = (NodeEditorInput) jsEditor.getEditorInput();
      IResource res = null;
      try {
        res = EclipseTracker.getResourceByNode(editorInput.getStorage().getNode(), LockType.LOCK_WAIT);
      } catch (LockIsBusy e) {
        RLogger.logDebug(CoreUtils.getTrace(e),7);
      }
      if (res == null || !project.equals(res.getProject())) {
        continue;
      }
      if (editorInput.getStorage().getNode().getQualifiedId().equals(nodeQId)) {
        return eRef;
      }
    }
    return null;
  }

  public static Map<String, NodeTypeDescriptor> getAdditionalTypes() {
    synchronized (sync){
      if (nodeTypes == null) {
        nodeTypes = new HashMap<>();
        IExtensionRegistry reg = Platform.getExtensionRegistry();
        IConfigurationElement[] configurationElements =
            reg.getConfigurationElementsFor(Activator.EXTENSION_NAME_NODE_TYPES);
        for (IConfigurationElement conf : configurationElements) {
          try {
            TreeNode nt = (TreeNode) conf.createExecutableExtension("typeclass");
            nodeTypes.put(
                conf.getAttribute("name"),
                new NodeTypeDescriptor(
                    conf.getAttribute("name"),
                    nt.getClass(),
                    conf.getAttribute("icon"),
                    conf.getContributor().getName()));
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
      return nodeTypes;
    }
  }


  public static void stopCheckers() {
    for(TreeDB db : ResManager.getManager().getTreeDBs()) {
      EclipseCheckerContainer ect = db.getMetaObject(META_CHECKER_CONTAINER);
      if(ect!=null)
        ect.stopOldThread();
    }
  }
  private static String META_CHECKER_CONTAINER = "CHECKER_CONTAINER";
  public static EclipseCheckerContainer getCheckers(TreeDB db, RevisionInfo ri) {
    if(db!=null){
      TreeDB base = Instantiator.getBaseTree(db);
      while(base != null && base != db){
        db = base;
        base = Instantiator.getBaseTree(db);
      }
    }else
      return null;
    //TODO return back stop of old checkers?
    EclipseCheckerContainer ecc = db.getMetaObject(META_CHECKER_CONTAINER);
    if (ecc == null) {
      synchronized (META_CHECKER_CONTAINER) {
        ecc = db.getMetaObject(META_CHECKER_CONTAINER);
        if(ecc == null) {
          ecc = new EclipseCheckerContainer(db);
          db.registerMetaObject(META_CHECKER_CONTAINER, ecc);
          IExtensionRegistry reg = Platform.getExtensionRegistry();
          IConfigurationElement[] configurationElements =
              reg.getConfigurationElementsFor(Activator.EXTENSION_NAME_CHECKERS);
          for (IConfigurationElement conf : configurationElements) {
            try {
              INodeChecker checker = (INodeChecker) conf.createExecutableExtension("class");
              ecc.addChecker(checker);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
          for(INodeChecker ch : API.getCheckers(db))
            ecc.addChecker(ch);
        }
      }
    }
    return ecc;
  }

  public static SortedMap<Integer, String> getBrowserEngineNames() {
    RequalityPlugin.initBrowserEngines();
    return browser_engines;
  } 
  private static String gatherFileName(String path) {
    int pos = path.lastIndexOf("/");
    if(pos>-1)
      return path.substring(pos+1);
    return path;
  }
  public static void applyDownloadDialog(Shell sh, String loc) {
    String name = gatherFileName(loc);
    FileDialog dd =
        new FileDialog(sh, SWT.SAVE);
    String visName = CoreUtils.URLDecode(name);
    dd.setFilterExtensions(new String[] {"*.odt"});
    dd.setText("Download '"+visName+"' to:");
    dd.setFileName(visName);
    //dd.setMessage(DIALOG_MESSAGE);
    String exportFile = dd.open();
    if(exportFile!=null) {
      try {
        File f = new File(exportFile);
        String title = "Download '"+visName+"'";
        if(f.exists()) {
          boolean res = RMessages.openQuestion(title,"File '"+visName+"' already exist in '"+exportFile+"', rewrite it?", false, true);
          if(res) {
            if(!f.delete())
              RMessages.openError(title, "Cannot replace '"+visName+"' file","", false);
          }else
            return;
        }
        RequalityCLI.copyfile(URI.create(loc),f.toURI());
      } catch (IOException e) {
        RLogger.logError(e);
      }
    }
  }
  public static Browser newBrowser(Composite parent, ITreeDBProvider db, int style) {
    initBrowserEngines();
    Browser b = new Browser(parent, style);
    LocationListener ll = (new LocationListener(){
      @Override
      public void changing(LocationEvent event) {
        String loc = event.location;
        //TODO we need to detect all download-related calls and replace default handler for them
        if(loc.toLowerCase().endsWith(".odt")) {
          //handle for download events to deal with download request duplication
          event.doit = false;
          EclipseAPI.getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
              applyDownloadDialog(Display.getCurrent().getActiveShell(), loc);
            }
          });
          return;
        }
        if(loc.startsWith("requality://")){
          event.doit = false;
          String path = loc.substring(12);
          TreeNode node = null;
          try{
            TreeDB dba = db.getTreeDB();
            if(dba==null)
              return;
            node = dba.getNode(path);;
          }catch(Exception e){
            RLogger.logError(e);
          }
          if(node!=null){
          //TODO from editors
            //It is links in reports and another html places to nodes in tree 
            SelectionManager.getOne().updateSelection(SourcePriority.EDITOR, fromPlugin, node);
            //SelectionManager.getOne().changeActiveSource(fromPlugin.getEventSource(), fromPlugin, true);
          }else
            RMessages.openInfo("Element not found", "Element not found for path '"+path+"'", false);
        }
      }  
      @Override
      public void changed(LocationEvent event) {
        String loc = event.location;
        if(loc.startsWith("requality://"))
          event.doit = false;
      }
    });
    b.addDisposeListener(new DisposeListener() {
      @Override
      public void widgetDisposed(DisposeEvent e) {
        b.removeLocationListener(ll);
      }
    });
    b.addLocationListener(ll);
  return b;
  }

  public static SortedMap<Integer, Short> getBrowserEnginesState() {
    RequalityPlugin.initBrowserEngines();
    return browser_engines_state;
  }

  public static int getBrowserType() {
    RequalityPlugin.initBrowserEngines();
    return BROWSER_TYPE;
  }
}
