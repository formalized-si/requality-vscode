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

package com.unitesk.requality.vscode.port.eclipse;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.Command;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.IHandler;
import com.unitesk.requality.vscode.ui.resources.ResourceException;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.FileLocator;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.runtime.IExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.InvalidRegistryObjectException;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.runtime.Platform;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.dialogs.DialogSettings;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.jface.resource.ImageRegistry;
import com.unitesk.requality.vscode.ui.workbench.IPageListener;
import com.unitesk.requality.vscode.ui.workbench.IPartListener2;
import com.unitesk.requality.vscode.ui.workbench.IPropertyListener;
import com.unitesk.requality.vscode.ui.workbench.IWindowListener;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPartReference;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.commands.ICommandService;
import com.unitesk.requality.vscode.ui.commands.IHandlerService;
import com.unitesk.requality.vscode.ui.navigator.CommonNavigator;
import com.unitesk.requality.vscode.ui.workbench.IPage;
import com.unitesk.requality.vscode.ui.plugin.AbstractUIPlugin;
import com.unitesk.requality.vscode.ui.runtime.UIJob;
import com.unitesk.requality.vscode.ui.workbench.ContentOutline;
import com.unitesk.requality.vscode.ui.workbench.PropertySheet;
import com.unitesk.requality.vscode.ui.tabbed.ISection;
import com.unitesk.requality.vscode.ui.tabbed.TabContents;
import com.unitesk.requality.vscode.ui.tabbed.TabbedPropertySheetPage;
import com.unitesk.requality.vscode.ui.runtime.BundleContext;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.IFSIdProvider;
import com.unitesk.requality.core.SharedConsts;
import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.api.ILightAPISource;
import com.unitesk.requality.core.attribute.AttributeGenerator;
import com.unitesk.requality.core.attribute.reference.ReferenceRenames;
import com.unitesk.requality.core.descriptors.DocumentProcessorDescriptor;
import com.unitesk.requality.core.descriptors.NodeTypeDescriptor;
import com.unitesk.requality.core.descriptors.TestGeneratorDescriptor;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.localization.Localization;
import com.unitesk.requality.core.localization.StringsSource;
import com.unitesk.requality.core.log.LoggerSource;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.nodeiterator.IAttributeGenerator;
import com.unitesk.requality.core.operation.IDeleteHandler;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.IUIStateChangeListener;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.service.PortInitStrategy;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.FilesTracker;
import com.unitesk.requality.core.storage.IFileDeleteListener;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.storage.ResManagerProvider;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.document.IDocumentProcessor;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.handlers.NewElementHandler;
import com.unitesk.requality.vscode.port.eclipse.jobs.EclipseJobManager;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF;
import com.unitesk.requality.vscode.port.eclipse.wizards.DocWizard;
import com.unitesk.requality.repo.CVSSourcePlugin;
import com.unitesk.requality.repo.ICVSClient;
import com.unitesk.requality.repo.RepoManager;
import com.unitesk.requality.report.Group;
import com.unitesk.requality.report.Groups;
import com.unitesk.requality.vscode.port.report.StatisticManager;
import com.unitesk.requality.report.template.IReportDataSource;
import com.unitesk.requality.report.template.ITemplatePathSource;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateFile;
import com.unitesk.requality.report.template.TemplateScope;
import com.unitesk.requality.tests.ITestGenerator;
import com.unitesk.requality.tests.ITestGeneratorsProvider;
import com.unitesk.requality.tool.Const;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.IMMagic;
import com.unitesk.requality.tool.Inkscape;
import com.unitesk.requality.tool.cli.CommandLineArgsProvider;
import com.unitesk.requality.tool.cli.LibreCLI;
import com.unitesk.requality.tool.message.RMessages;

/** The activator class controls the plug-in life cycle */
public class Activator extends AbstractUIPlugin {
  private static boolean DEBUG_FILE_TRACKER = false;
  private FSId fsid = new FSId(EventSource.SYSTEM, this);
  private Lock plock = new ReentrantLock();
  private static int MEMFLAG = IFolder.INCLUDE_HIDDEN|IFolder.INCLUDE_TEAM_PRIVATE_MEMBERS|IFolder.INCLUDE_PHANTOMS;
  private IUIStateChangeListener listener = 
      new IUIStateChangeListener() {

    @Override
    public void newActiveNodes(FSId source, SelectionRecord nodes) {
      updateDeleteInMenu();
    }

  };
  static{
    CoreUtils.lockWorkspace("init Requality - eclipse Activator");
    CoreUtils.DeleteHandler dh = CoreUtils.deleteHandler;
    CoreUtils.deleteHandler = new CoreUtils.DeleteHandler(){
      public boolean delete(IResource ifile, boolean force, boolean delcach, int depth){
        try {
          boolean b = true;
          boolean candel = true;
          if(delcach) {
          	try {
              if(ifile.exists()) {
                IPath loc = ifile.getLocation();
                if(loc!=null) {
                  File file = loc.toFile();
                  CacheStorage.closeRecConnection(file);
                }
              }
          	}catch(Exception e) {
          	  RLogger.logError(e);
          	}
          }
          if(ifile instanceof IFolder){
            candel = false;
            IFolder ifo = (IFolder)ifile;
            if(depth==-2||depth>1){
              for(IResource r:ifo.members(MEMFLAG))
                b = b && delete(r, force,false, depth!=-2?depth-1:-2);
            if(ifo.members(MEMFLAG).length == 0)
              candel = true;
            }
          }
          if(candel&&(depth>=0||depth == -2)){
            if(ifile.exists())
              try{
                java.nio.file.Path p = ifile.getLocation().toFile().toPath();
                for(IFileDeleteListener fdl:CoreUtils.getListeners())fdl.fileWillBeDeleted(p);
                try {
                  ifile.delete(force, null);
                }catch(Exception re) {
                  if(re.getMessage().contains("resource tree is locked")) {
                    if(Files.exists(p)){//TODO change to CoreUtils version?
                      recBareDelete(p);
                    }//fallback for PRE_CLOSE and PRE_DELELE
                  //TODO mb core another implementation for it
                  }
                  else throw re;
                  
                }
                for(IFileDeleteListener fdl:CoreUtils.getListeners())fdl.fileWasDeleted(p);
              }
              catch(IOException | ResourceException re){
                RLogger.logError(re);
                return false;
              }
            return b;
          }
        } catch (CoreException e) {
          RLogger.logError(e);
        }
        return false;
      }
      private void recBareDelete(java.nio.file.Path p) throws IOException {
        if(!Files.exists(p))
          return;
        if(Files.isDirectory(p))
        {
          List<java.nio.file.Path> toRemove = Files.list(p).collect(Collectors.toList());
          for(java.nio.file.Path child:toRemove)
            recBareDelete(child);
        }
        try {
          Files.delete(p);
        }catch(IOException e) {
          java.nio.file.Path moved = Files.move(p, p.getParent().resolve(p.getFileName()+"__renamed"));
          if(moved!=null)
            Files.delete(moved);
          else
            throw e;
        }
      }
      @Override
      public boolean delete(File f, boolean force, boolean clearcache, int depth) {
        if(depth>=0 || depth == -2){
          IPath location= Path.fromOSString(f.getAbsolutePath()); 
          if(clearcache) {
        	try {
              CacheStorage.closeRecConnection(f);
        	}catch(Exception e) {
        	  RLogger.logError(e);
        	}
          }
          IProject[] ip = EclipseTracker.getTracker(DBNamesManager.DEFAULT).getProjects();
          for(IProject p:ip) {
            if(p == null)
              continue;
            IPath s = p.getLocation();
            if(s!=null && s.isPrefixOf(location)){
              if(!p.isAccessible())//project close or non-editable
                return CoreUtils.baseDeleteHandler.delete(f, force, clearcache, depth);
              location = location.makeRelativeTo(p.getLocation());
              IResource ifile = p.findMember(location.toOSString());
              boolean b = true;
              if(ifile!=null && ifile.exists()){
                return delete(ifile, force,false, depth);
              }
            }
          }
          return dh.delete(f, force,clearcache, depth);
        }
        return false;
      }
    };
  }
  // The plug-in ID
  public static final String PLUGIN_ID = "com.unitesk.requality";

  public static final String EXTENSION_NAME_DOC_PROCESSOR =
      "com.unitesk.requality.document.processor";
  public static final String EXTENSION_NAME_REFERENCE_ACR = "com.unitesk.requality.referencerename";
  public static final String EXTENSION_NAME_API_SRCS = "com.unitesk.requality.apisource";
  public static final String EXTENSION_NAME_REPORT = "com.unitesk.requality.report";
  public static final String EXTENSION_NAME_TEST_GENERATORS = "com.unitesk.requality.testgenerator";
  public static final String EXTENSION_NAME_TEST_GENERATORS_PROVIDER =
      "com.unitesk.requality.testgeneratorprovider";
  public static final String EXTENSION_NAME_NODE_TYPES = "com.unitesk.requality.nodetype";
  public static final String EXTENSION_NAME_CHECKERS = "com.unitesk.requality.checker";
  public static final String EXTENSION_NAME_COVERAGE_SOURCES =
      "com.unitesk.requality.coveragesource";
  public static final String EXTENSION_NAME_ATTRIBUTE_GENERATORS =
      "com.unitesk.requality.attributegenerator";
  public static final String EXTENSION_NAME_HANDLERS = "org.eclipse.ui.handlers";

  private static final String COMMANDID_NEW_NODE_PREFIX = "com.unitesk.requality.commands.Create";
  public static final String WAIT_DISPLAY = "displayWaitObject";

  // The shared instance
  private static Activator plugin;

  private RepoManager cvclManager;

  private void initAttributeGenerators() {
    AttributeGenerator.initGenMap(loadAttributeGeneratorsDescriptions());
  }

  private Map<String, List<ICVSClient>> supportMap = new HashMap<>();
  /** The constructor */
  public Activator() {
    initTreesTracker();
  }

  // //////////////////////////////////////////////////////////////////////////
  //
  // Bundle operations
  //
  // //////////////////////////////////////////////////////////////////////////
  private boolean loaded = false;

  private ActivationState state = ActivationState.BEFORE_INIT;
  @Override
  public void stop(BundleContext context) throws Exception {
    state = ActivationState.STOPPED;
    Global.callStop();
    super.stop(context);
    SelectionManager.getOne().removeSourceListener(EventSource.EXPLORER, fsid, listener);
    API.destroySources();
  }
  private class ELogger extends RLogger{
    com.unitesk.requality.vscode.ui.e4.core.services.log.Logger logger;
    public ELogger(){
      logger = PlatformUI.getWorkbench().getService(com.unitesk.requality.vscode.ui.e4.core.services.log.Logger.class);
    }
    @Override
    public void lI(String s) {
      super.lI(s);
      if(CoreUtils.DEBUG_LEVEL>0)
        logger.info(s);
    };
    @Override
    public void lE(String s) {
      logger.error(s);
    };
    @Override
    public void lD(String s) {
      logger.info(s);
    };
  }

  @Override
  public void start(BundleContext context) throws Exception {
    try {
      PortInitStrategy.strategy = PortInitStrategy.DYNAMIC_ON_OCCUPIED;
      try {
        
        RequalityCNF.activateFunction = new Runnable() {
  
          @Override
          public void run() {
            initEditorLinkManager();
          }
        };
        initDebug();
        loaded = false;
        super.start(context);
        plugin = this;
        CommandLineArgsProvider.cLineArgs = Platform.getCommandLineArgs();
        JobManager.jm = new EclipseJobManager();
        RLogger.loggerS = new LoggerSource(){
          ELogger el = null;
          @Override
          public RLogger getLogger() {
            if(el==null||el.logger==null){
              try{
                el=new ELogger();
              }
              catch(IllegalStateException ise){
                return RLogger.baseLogger.getLogger();
              }
            }
            return el;
          };
        };
        initSharedConsts();
        initAPISources();
        loadReferenceAcronyms();
        initCVSClients();
        API.initSources();//needed because some providers requires current tree
        //structure or uses tree listeners. //TODO different load events
        initAttributeGenerators();
        initCLIs();
        //ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceChangeListener);
        loaded = true;
        SelectionManager.getOne()
            .addSourceListener(EventSource.EXPLORER, fsid, listener);
  
        getNewElementHandlers();//init menu points
        RLogger.logDebug("all new elements handlers were loaded",5);
        initPartListenerAndDisplay();
        RLogger.logDebug("part listerner was inited",5);
        state = ActivationState.WORKING;
        initTracker(this);
        RLogger.logDebug("tracker was inited",5);
        Activator.getDefault().getProcessors();
        RLogger.logDebug("doc processors were loaded",5);
  
        if (OSNAME.contains("win")) {
          Class osClass = Class.forName("org.eclipse.swt.internal.win32.OS");
          Class tcharClass = Class.forName("org.eclipse.swt.internal.win32.TCHAR");
          if(tcharClass!=null) {
          Object key = tcharClass.getConstructor(int.class, String.class, boolean.class)
              .newInstance(0, "Software\\Microsoft\\Internet Explorer\\Main\\FeatureControl\\MAXCONNECTIONSPERSERVER", true);
          Object key2 = tcharClass.getConstructor(int.class, String.class, boolean.class)
              .newInstance(0, "SYSTEM\\CurrentControlSet\\Control\\FileSystem", true);
            //TCHAR key = new TCHAR (0, "Software\\Microsoft\\Internet Explorer\\Main\\FeatureControl\\MAXCONNECTIONSPERSERVER", true);  //$NON-NLS-1$
            long [] phkResult = new long [1],phkResult2 = new long [1];
            boolean inited = false, inited2 = false;
            try {
              Integer res = (Integer) osClass.getDeclaredMethod("RegOpenKeyEx", long.class, tcharClass,int.class, int.class, long[].class)
                .invoke(null, 0x80000002, key, 0, 0x20019, phkResult);
              inited = res == 0;
              //inited = OS.RegOpenKeyEx (OS.HKEY_LOCAL_MACHINE, key, 0, OS.KEY_READ, phkResult)==0;
              if(inited)
                osClass.getDeclaredMethod("RegCloseKey", long.class)
                  .invoke(null, phkResult[0]);
                //OS.RegCloseKey (phkResult [0]);
            } catch(Exception e) {
            }
            try {
                Integer res = (Integer) osClass.getDeclaredMethod("RegOpenKeyEx", long.class, tcharClass,int.class, int.class, long[].class)
                  .invoke(null, 0x80000002, key2, 0, 0x20019, phkResult2);
                boolean openned = res == 0;
                if(openned) {
                  Object keyLP = tcharClass.getConstructor(int.class, String.class, boolean.class)
                          .newInstance(0, "LongPathsEnabled", true);
                  int size = 2;
                  int result;
                  do {
                    int[] lpcbData = new int[] { 4 * size }; // 4 bytes per int
                    int[] lpData = new int[size];
                    result = (Integer) osClass.getDeclaredMethod("RegQueryValueEx", long.class, tcharClass,long.class, int[].class, int[].class, int[].class)
                        .invoke(null, phkResult2[0], keyLP, 0, null, lpData, lpcbData);
                    if (result == 0) {
                      inited2 = lpData[0]==1;
                    }
                    size *= 2;
                  } while (result == 234);
                  osClass.getDeclaredMethod("RegCloseKey", long.class)
                    .invoke(null, phkResult2[0]);
                }
                  //OS.RegCloseKey (phkResult [0]);
              } catch(Exception e) {
                e.printStackTrace();
              }
            if(!inited) {
              DialogSettings dss = new DialogSettings("registryfix");
              boolean ds = false;
              try {
                dss.load("registryfix");
                ds = dss.getBoolean("doNotShow");
              }catch(IOException e) {
              }
              if(!ds) {
                UIJob uj = new UIJob("Show windows connections issue") {
                  @Override
                  public IStatus runInUIThread(IProgressMonitor monitor) {
                    RMessages.openView(null, Consts.CONNECTION_LIMIT_DIALOG_NAME, true,
                      Localization.getLocalizedString(StringsSource.DIALOG, "title_warning_connection_limit", "Warning! Active editors count may be limited", null),
                      Localization.getLocalizedString(StringsSource.DIALOG, "message_warning_connection_limit", "Windows version of Requality requires additional fix on registry to work with more than 8 of active editors (Uni/Module Editors, Report views, Markup Editors) at a time.", null),
                      Localization.getLocalizedString(StringsSource.DIALOG, "button_apply_warning_connection_limit", "Apply the fix", null),
                      Localization.getLocalizedString(StringsSource.DIALOG, "button_later_warning_connection_limit", "Ask again later", null),
                      Localization.getLocalizedString(StringsSource.DIALOG, "button_ignore_warning_connection_limit", "Do not show this dialog again", null),
                      "resources/regfix/win10SocketConnectionsLimitFix.reg",
                      "doNotShow"
                      );
                    return Status.OK_STATUS;
                  }
                };
                uj.schedule();
              }
            }
            if(!inited2) {
              DialogSettings dss = new DialogSettings("registryfix");
              boolean ds = false;
              try {
                dss.load("registryfix");
                ds = dss.getBoolean("doNotShow2");
              }catch(IOException e) {
              }
              if(!ds) {
                UIJob uj = new UIJob("Show windows long paths issue") {
                  @Override
                  public IStatus runInUIThread(IProgressMonitor monitor) {
                    RMessages.openView(null, Consts.CONNECTION_LIMIT_DIALOG_NAME, true,
                      Localization.getLocalizedString(StringsSource.DIALOG, "title_warning_long_paths", "Warning! Long Paths is not enabled", null),
                      Localization.getLocalizedString(StringsSource.DIALOG, "message_warning_long_paths", "Windows version of Requality requires Long Paths mechanics to be enabled. There are different problems with reports generation and references handling to be happened otherwise. This function can be also enabled from control panel.", null),
                      Localization.getLocalizedString(StringsSource.DIALOG, "button_apply_warning_connection_limit", "Apply the fix", null),
                      Localization.getLocalizedString(StringsSource.DIALOG, "button_later_warning_connection_limit", "Ask again later", null),
                      Localization.getLocalizedString(StringsSource.DIALOG, "button_ignore_warning_connection_limit", "Do not show this dialog again", null),
                      "resources/regfix/win10LongPathsFix.reg",
                      "doNotShow2"
                      );
                    return Status.OK_STATUS;
                  }
                };
                uj.schedule();
              }
            }
          }
        }
        //startRPC();
      } catch (Exception e) {
        RLogger.logError(e);
      }
    }
    finally{
      CoreUtils.unlockWorkspace();
    }
  }

  private static String OSNAME = System.getProperty("os.name").toLowerCase();
  private void initEditorLinkManager() {
    RequalityCNF nav = RequalityCNF.activeNavigator;
    Const.NO_EXPLORER_EDITOR_LINK = !nav.isLinkingEnabled();
    nav.addPropertyListener(new IPropertyListener() {
      
      @Override
      public void propertyChanged(Object source, int propId) {
        if(nav.getCommonViewer() == null || nav.getCommonViewer().getControl().isDisposed())
          return;
        if(propId == CommonNavigator.IS_LINKING_ENABLED_PROPERTY)
          Const.NO_EXPLORER_EDITOR_LINK = !nav.isLinkingEnabled();
      }
    });
  }

  private static void initAPISources() {
    IExtensionRegistry reg = Platform.getExtensionRegistry();
    IConfigurationElement[] configurationElements =
        reg.getConfigurationElementsFor(EXTENSION_NAME_API_SRCS);
    for (IConfigurationElement conf : configurationElements){
      if(conf.getAttribute("class")!=null)
      {
        boolean succeed = false;
        try {
          ILightAPISource as = (ILightAPISource) conf.createExecutableExtension("class");
          if(as!=null){
            try {
              Method m = as.getClass().getMethod("getOne");
              if(m!=null) {
                ILightAPISource one = (ILightAPISource) m.invoke(null);
                as = one;
              }
            }catch(Exception e) {
            }
            API.register(as);
            succeed = true;
            RLogger.logInfo("Instantiates api source "+conf.getAttribute("id"));
          }
        } catch (CoreException e) {
          // TODO Auto-generated catch block
          RLogger.logError(e);
        }
        if(!succeed)
          RLogger.logError("Cannot instantiate api source "+conf.getAttribute("id"));
      }
    }
  }


  private void initDebug() {
    CoreUtils.DEBUG_LEVEL = Platform.inDebugMode()?1:0;
    String[] ca = Platform.getCommandLineArgs();
    for(int i = 0;i<ca.length;i++) {
      String lower = ca[i].toLowerCase();
      if(lower.startsWith("-debuglevel")) {
      int dl = lower.indexOf("=");
      if(dl>-1) {
        String inta = lower.substring(dl+1).trim();
        int a = 1;
          try{
            a = Integer.parseInt(inta);
          }catch(NumberFormatException e){
          }
          if(a>0)
            CoreUtils.DEBUG_LEVEL = a;
      }else
        if((i+1)<ca.length)
        {
          int a = 1;
          try{
            a = Integer.parseInt(ca[i+1]);
            
          }catch(NumberFormatException e){
          }
          if(a>0)
            CoreUtils.DEBUG_LEVEL = a;
        }
      }
    }
  }

  public static boolean isInitOrActive(){
    return plugin!=null && (plugin.state == ActivationState.BEFORE_INIT || plugin.state == ActivationState.WORKING);
  }
  public static boolean isActive(){
    return plugin!=null && plugin.state == ActivationState.WORKING;
  }
  
  private void initPartListenerAndDisplay() {
    JobManager.jm.startJob("init part loader", new RunnableWithMonitor() {
      @Override
      public boolean shouldRerun() {
        return true;
      }
      @Override
      public boolean shouldRun() {
        try{
          return getWorkbench()!=null && !getWorkbench().isStarting();
        }catch(IllegalStateException e){
          return false;
        }
      }
    @Override
    public void run(com.unitesk.requality.core.operation.IProgressMonitor pm) {
      
      EclipseAPI.setDisplay(PlatformUI.getWorkbench().getDisplay());
      synchronized (Activator.WAIT_DISPLAY) {
        Activator.WAIT_DISPLAY.notifyAll();  
      }
      
      getWorkbench().addWindowListener(new IWindowListener(){
          @Override
          public void windowOpened(IWorkbenchWindow window) {
            window.addPageListener(pl);
            for(IWorkbenchPage p:window.getPages())
              if(p!=null)
                p.addPartListener(
                  list);
            windowActivated(window);
          }
        
          @Override
          public void windowDeactivated(IWorkbenchWindow window) {
            // TODO Auto-generated method stub
            
          }
        
          @Override
          public void windowClosed(IWorkbenchWindow window) {
            window.removePageListener(pl);
          }
        
          @Override
          public void windowActivated(IWorkbenchWindow window) {
            if(window.getActivePage()!=null)
              list.partActivated(window.getActivePage().getActivePartReference());
          }
        });
        if(!getWorkbench().isStarting())
          for(IWorkbenchWindow w:getWorkbench().getWorkbenchWindows()){
            w.addPageListener(pl);
            for(IWorkbenchPage p:w.getPages())
              if(p!=null)
                p.addPartListener(
                  list);
          }
    }
  }, false);
  }
  IPageListener pl = new IPageListener(){
    
    @Override
    public void pageOpened(IWorkbenchPage page) {
      page.addPartListener(list);
    }
  
    @Override
    public void pageClosed(IWorkbenchPage page) {
      page.removePartListener(list);
    }
  
    @Override
    public void pageActivated(IWorkbenchPage page) {
      list.partActivated(page.getActivePartReference());
    }
  };
  IPartListener2 list = new IPartListener2() {

    @Override
    public void partVisible(IWorkbenchPartReference partRef) {
      activatePart(partRef, false);
    }

    @Override
    public void partOpened(IWorkbenchPartReference partRef) {
      activatePart(partRef, false);
    }

    @Override
    public void partInputChanged(IWorkbenchPartReference partRef) {
    }

    @Override
    public void partHidden(IWorkbenchPartReference partRef) {
      if(partRef == null)
        return;
      Object ref = partRef.getPart(false);
      //((ContentOutline)ref).getCurrentPage()
      if(ref instanceof ContentOutline) {
        ref = ((ContentOutline)ref).getCurrentPage(); 
      }
      Object fref = ref;
      SelectionManager sman = SelectionManager.getOne();
      if (fref instanceof PropertySheet){
        PropertySheet ps = (PropertySheet) fref;
        IPage p = ps.getCurrentPage();
        if(p!=null) {
          fref = recLocatePropsSection(fref, p);
        }
        //else SelectionManager.getOne().changeActiveSource(EventSource.GENERAL, true);
        //RLogger.logInfo(partRef.getId()+" "+partRef.getPartName()+" "+partRef.getTitle());
      }
      if(fref instanceof IFSIdProvider) {
        IFSIdProvider fp = (IFSIdProvider) fref;
        FSId fs = fp.getFSId();
        sman.deActivateSource(SourcePriority.SOURCE_CHANGE, fs.getEventSource(), fs, fp.isSelectionSource());
      }else {
        //else SelectionManager.getOne().changeActiveSource(EventSource.GENERAL, true);
        //RLogger.logInfo(partRef.getId()+" "+partRef.getPartName()+" "+partRef.getTitle());
      }
    }

    @Override
    public void partDeactivated(IWorkbenchPartReference partRef) {
    }

    @Override
    public void partClosed(IWorkbenchPartReference partRef) {
    }

    @Override
    public void partBroughtToTop(IWorkbenchPartReference partRef) {
      activatePart(partRef, false);
    }
    private void activatePart(IWorkbenchPartReference partRef, boolean selChange) {
      if(partRef == null)
        return; 
      Object ref = partRef.getPart(false);
      //((ContentOutline)ref).getCurrentPage()
      if(ref instanceof ContentOutline) {
        ref = ((ContentOutline)ref).getCurrentPage(); 
      }
      Object fref = ref;
      SelectionManager sman = SelectionManager.getOne();
      if (fref instanceof PropertySheet){
        PropertySheet ps = (PropertySheet) fref;
        IPage p = ps.getCurrentPage();
        if(p!=null) {
          fref = recLocatePropsSection(fref, p);
        }
          //else SelectionManager.getOne().changeActiveSource(EventSource.GENERAL, true);
          //RLogger.logInfo(partRef.getId()+" "+partRef.getPartName()+" "+partRef.getTitle());
      }
      if(fref instanceof IFSIdProvider) {
        IFSIdProvider fp = (IFSIdProvider) fref;
        FSId fs = fp.getFSId();
        sman.activateSource(SourcePriority.SOURCE_CHANGE, fs.getEventSource(), fs, fp.isSelectionSource(), selChange);
      }
      JobManager.jm.startJob("update selection of "+ref, jobNow, false);
    }
    private IFSIdProvider recLocatePropsSection(Object fref, IPage p) {
      if(p instanceof TabbedPropertySheetPage) {
        TabbedPropertySheetPage tpage = (TabbedPropertySheetPage) p;
        TabContents tc = tpage.getCurrentTab();
        if(tc!=null) {
          ISection[] sections = tc.getSections();
          if(sections!=null && sections.length > 0) 
          {
            for(ISection s: sections){
              if(s instanceof IFSIdProvider)
                return (IFSIdProvider) s;
            }
          }
        }
      }
      return null;
    }
    RunnableWithMonitor jobNow = null;
    @Override
    public void partActivated(IWorkbenchPartReference partRef) {
      activatePart(partRef, true);
    }
  };
  private ResManagerProvider rmp = null;
  private IResTracker ft = null;//only for test provider
  private static String TEST_WORKSPACE="";
  private static String TEST_TEMPLATES="";
  public void initTreesTracker() {
    if(rmp == null) {
      if(DEBUG_FILE_TRACKER)
        rmp = getTestFileTrackerProvider(
          TEST_WORKSPACE,
          TEST_TEMPLATES);
      else
        rmp = getTrackerProvider();
      ResManager.changeResManagerProvider(rmp);
    }
  }
  private ResManagerProvider getTrackerProvider() {
    return new ResManagerProvider() {
        @Override
        public IResTracker createTracker(Pair<String, Object>... params) {
          return EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        }
      };
  }

  private ResManagerProvider getTestFileTrackerProvider(String workspace, String templates) {
    return new ResManagerProvider() {
      @Override
      public IResTracker createTracker(Pair<String, Object>... params) {          
        if(ft!=null)
          return ft;
        ft = new FilesTracker(DBNamesManager.DEFAULT);
        ft.init(new Pair<String, Object>(ResManager.PATH, workspace),
            new Pair<String, Object>(ResManager.TEMPL_PATH, templates));
          //return EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        return ft;
      }
    };
  }

  private void initSharedConsts() {
    SharedConsts.statManClass = StatisticManager.class;
    com.unitesk.requality.vscode.port.eclipse.handlers.location.CommonRemoveLocation.init(
        new com.unitesk.requality.vscode.port.eclipse.handlers.location.CommonRemoveLocation());
    com.unitesk.requality.core.operation.GenerateReportHandler.init(
        com.unitesk.requality.vscode.port.eclipse.handlers.report.GenerateReportHandler.getProvider());
  }

  private void initCLIs() {
    final IPreferenceStore pref = Activator.getDefault().getPreferenceStore();
    String path = pref.getString("com.unitesk.requality.librepath");
    LibreCLI.init(
        path,
        new Runnable() {
          public void run() {
            String val = LibreCLI.getDefault().getPath();
            pref.setValue("com.unitesk.requality.librepath", val);
            LibreCLI.getDefault().setPath(val);
          }
        });
    path = pref.getString("com.unitesk.requality.magick");
    IMMagic.init(
        path,
        new Runnable() {
          public void run() {
            String val = IMMagic.getDefault().getPath();
            pref.setValue("com.unitesk.requality.magick", val);
            IMMagic.getDefault().setPath(val);
          }
        });
    path = pref.getString("com.unitesk.requality.inkscape");
    Inkscape.init(
        path,
        new Runnable() {
          public void run() {
            String val = Inkscape.getDefault().getPath();
            pref.setValue("com.unitesk.requality.inkscape", val);
            Inkscape.getDefault().setPath(val);
          }
        });
  }
  //  private void startRPC() {
  //      startRPC(9321,9321);
  //    }
  //    private void startRPC(int port,int start) {
  //      try{
  //        Endpoint end = Endpoint.publish("http://localhost:"+port+"/requalityService2",new ReqService(EclipseTracker.getTracker()));
  //        end.isPublished();
  //        end.getProperties();
  //           RLogger.logInfo("Initiated on "+port);
  //      }catch(Exception be){
  //        bRLogger.logError(e);
  //        if(port<(start+4)){
  //          startRPC(port+1,start);
  //        }
  //        else
  //          bRLogger.logError(e);
  //      }
  //  }

  public boolean isStarted() {
    return loaded;
  }

  private void initCVSClients() throws Exception {
    IConfigurationElement[] extns =
        Platform.getExtensionRegistry()
            .getConfigurationElementsFor("com.unitesk.requality.cvsprovider");
    for (IConfigurationElement e : extns) {
      try {
        Object o = e.createExecutableExtension("cvcloveransvc");
        if ((o != null) && (o instanceof CVSSourcePlugin)) {
          // список поддерживаемых TeamProvider (список строк, возвращаемых getID)
          List<String> tpList = ((CVSSourcePlugin) o).getSupportedTeamProvidersIDList();
          // побежим по списку поддерживаемых TeamProvider'ов
          Iterator<String> lIt = tpList.iterator();
          while (lIt.hasNext()) {
            String teamProviderID = lIt.next();
            // если этот провайдер уже кем-то поддерживается
            ICVSClient cli = ((CVSSourcePlugin) o).getInstanceOfSVCClient();
            cli.setIsStatic(true);//global insts
            List<ICVSClient> supp = supportMap.get(teamProviderID);
            if(supp == null) {
              supp = new ArrayList<>();
              supportMap.put(teamProviderID, supp);
            }
            supp.add(cli);
          }
        }
      } catch (Exception ez) {

      }
    }
  }

  public static Activator getDefault() {
    return plugin;
  }
  // //////////////////////////////////////////////////////////////////////////
  //
  // Active node tracking
  //
  // //////////////////////////////////////////////////////////////////////////

  private void updateDeleteInMenu() {
    IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    if (window != null) {
      ICommandService commandService = window.getService(ICommandService.class);
      if (commandService != null) {
        commandService.refreshElements("org.eclipse.ui.edit.delete", null);
      }
    }
  }

  private void loadReferenceAcronyms() {
    Map<String, String> referenceAcronyms = new HashMap<>();
    IExtensionRegistry reg = Platform.getExtensionRegistry();
    IConfigurationElement[] configurationElements =
        reg.getConfigurationElementsFor(EXTENSION_NAME_REFERENCE_ACR);
    for (IConfigurationElement conf : configurationElements)
      referenceAcronyms.put(conf.getAttribute("Reference_name"), conf.getAttribute("Name_Acronym"));
    ReferenceRenames.setGlobalRenames(referenceAcronyms);
  }

  private Set<NewElementHandler> newehandlers = null;

  public Set<NewElementHandler> getNewElementHandlers() {
    if (newehandlers == null) {
      newehandlers = new HashSet<>();
      IExtensionRegistry reg = Platform.getExtensionRegistry();
      IConfigurationElement[] configurationElements =
          reg.getConfigurationElementsFor(EXTENSION_NAME_HANDLERS);
      for (IConfigurationElement conf : configurationElements) {
        String s = conf.getAttribute("commandId");
        if(s!=null && s.startsWith(COMMANDID_NEW_NODE_PREFIX))
          try {
            Object o = conf.createExecutableExtension("class");
            if(o!=null && o instanceof NewElementHandler)
              newehandlers.add((NewElementHandler) o);
          } catch (CoreException | InvalidRegistryObjectException e) {
            RLogger.logError(e);
            // TODO Auto-generated catch block
            //  RLogger.logError(e);
          }
      }
    }
    return newehandlers;
  }

  // //////////////////////////////////////////////////////////////////////////
  //
  // Report operations
  //
  // //////////////////////////////////////////////////////////////////////////

  //private Map<String, Template> reportTemplates = new HashMap<>();
  /** Get report templates, includes template reloading */
  public Map<String, Template> getRenewedReportTemplates() {
//    reportTemplates = new HashMap<>();
    return getReportTemplates(TemplateScope.ALL);
  }

  public SortedMap<String, IDeleteHandler> getDeleteHandlers() {
    if (deleteHandlers == null) loadDeleteHandlers();
    return deleteHandlers;
  }

  public Map<String, Template> getReportTemplates(TemplateScope ts) {
    return Collections.unmodifiableMap(loadReportTemplatesDescriptors(ts));
  }

  public Map<String, Group> getReportGroups(TemplateScope ts) {
    return Collections.unmodifiableMap(loadReportGroupsDescriptors(ts));
  }
  private SortedMap<String, IDeleteHandler> deleteHandlers;

  private void loadDeleteHandlers() {
    deleteHandlers = new TreeMap<>();
    IExtensionRegistry reg = Platform.getExtensionRegistry();
    IConfigurationElement[] configurationElements =
        reg.getConfigurationElementsFor(EXTENSION_NAME_HANDLERS);
    for (IConfigurationElement conf : configurationElements) {
      try {
        try {
          if (!conf.isValid()
              || conf.getAttribute("commandId") == null
              || !conf.getAttribute("commandId").equals("org.eclipse.ui.edit.delete")) continue;
          AbstractHandler ah = (AbstractHandler) conf.createExecutableExtension("class");
          if (ah != null && ah instanceof IDeleteHandler)
            deleteHandlers.put(conf.getAttribute("class"), (IDeleteHandler) ah);
        } catch (ClassCastException cce) {
          //so, this is bad handlers(
        }
      } catch (Exception e) {
        RLogger.logError(e);
      }
    }
  }

  public IHandler[] getCommandHandlers(ICommandService cmdService, String command) {
    List<IHandler> handlers = new ArrayList<>();
    IExtensionRegistry reg = Platform.getExtensionRegistry();
    IConfigurationElement[] configurationElements =
        reg.getConfigurationElementsFor("org.eclipse.ui.handlers");
    for (IConfigurationElement conf : configurationElements) {
      if (conf.getAttribute("commandId").equals(command))
        try {
          handlers.add((IHandler) conf.createExecutableExtension("class"));
        } catch (CoreException e) {
          // TODO Auto-generated catch block
          RLogger.logError(e);
        }
    }
    Command comm = cmdService.getCommand(command);
    if (!comm.isDefined()) {
      return null;
    }
    return handlers.toArray(new IHandler[0]);
  }

  private Map<String, Template> loadReportTemplatesDescriptors(TemplateScope ts) {
    List<String> tplPaths = new ArrayList<>();
    Set<String> wsPaths = new HashSet<>();
    if(ts == TemplateScope.ALL || ts == TemplateScope.BUILDIN)
      try {
        String p = Activator.getPath("templates");
        if(p!=null)
          tplPaths.add(p);
      } catch (IOException e) {
        RLogger.logError(e);
      }
    if(ts == TemplateScope.ALL || ts == TemplateScope.WORKSPACE)
    {
      String p = ResManager.getManager().getGlobalCacheLocation().getPath()+".reqcache/templates/";
      if(p!=null) {
        tplPaths.add(p);
        wsPaths.add(p);
      }
    }
    try {
      String p = Activator.getResPath("templates");
      if(p!=null)
        tplPaths.add(p);
    } catch (IOException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    IExtensionRegistry reg = Platform.getExtensionRegistry();
    IConfigurationElement[] configurationElements =
        reg.getConfigurationElementsFor(EXTENSION_NAME_REPORT);
    for (IConfigurationElement conf : configurationElements) {
      try {
        if (!conf.getName().equals("templatePath")) {
          continue;
        }
        ITemplatePathSource tplPath =
            (ITemplatePathSource) conf.createExecutableExtension("provider");
        String path = tplPath.getPath();
        if (path != null && new File(path).isDirectory()) {
          tplPaths.add(path);
        }
      } catch (Exception e) {
        RLogger.logError(e);
      }
    }
    Map<String, Template> reportTemplates = new HashMap<>();
    for (String path : tplPaths) {
      if(path == null) continue;
      File tplPath = new File(path);
      if(!tplPath.exists()) continue;
      boolean isWorkspace = wsPaths.contains(path);
      for (String tplFolder : tplPath.list()) {
        String tpli = tplPath.toURI().toString();
        if (new File(URI.create(tpli + tplFolder + "/" + "template.xml")).exists()) {
          Template template;
          try {
            template = new Template(tpli + tplFolder, isWorkspace?
                TemplateScope.WORKSPACE:
                  TemplateScope.BUILDIN);
            reportTemplates.put(template.getId(), template);
          } catch (IOException e) {
            RLogger.logError(e);
          }
        }
      }
    }
    for (IConfigurationElement conf : configurationElements) {
      if (!conf.getName().equals("dataProvider")) {
        continue;
      }
      String tId = conf.getAttribute("templateId");
      String ftlName = conf.getAttribute("ftlName");
      if (reportTemplates.containsKey(tId)) {
        Template tpl = reportTemplates.get(tId);
        for (TemplateFile t : tpl.getTemplates()) {
          if (t.getTemplatePath().equals(ftlName)) {
            IReportDataSource dataObject;
            try {
              dataObject = (IReportDataSource) conf.createExecutableExtension("dataSource");
              t.setDataObject(dataObject);
            } catch (CoreException e) {
              RLogger.logError(e);
            }
          }
        }
      }
    }
    return reportTemplates;
  }
  private Map<String, Group> loadReportGroupsDescriptors(TemplateScope ts) {
    List<String> tplPaths = new ArrayList<>();
    if(ts == TemplateScope.ALL || ts == TemplateScope.BUILDIN)
      try {
        String p = Activator.getPath("templates");
        if(p!=null)
          tplPaths.add(p);
      } catch (IOException e) {
        RLogger.logError(e);
      }
    if(ts == TemplateScope.ALL || ts == TemplateScope.WORKSPACE)
    {
      String p = ResManager.getManager().getGlobalCacheLocation().getPath()+".reqcache/templates/";
      if(p!=null)
        tplPaths.add(p);
    }
    if(ts == TemplateScope.ALL || ts == TemplateScope.BUILDIN)
      try {
        String p = Activator.getResPath("templates");
        if(p!=null)
          tplPaths.add(p);
      } catch (IOException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
    IExtensionRegistry reg = Platform.getExtensionRegistry();
    IConfigurationElement[] configurationElements =
        reg.getConfigurationElementsFor(EXTENSION_NAME_REPORT);
    for (IConfigurationElement conf : configurationElements) {
      try {
        if (!conf.getName().equals("templatePath")) {
          continue;
        }
        ITemplatePathSource tplPath =
            (ITemplatePathSource) conf.createExecutableExtension("provider");
        String path = tplPath.getPath();
        if (path != null && new File(path).isDirectory()) {
          tplPaths.add(path);
        }
      } catch (Exception e) {
        RLogger.logError(e);
      }
    }
    Map<String, Group> reportGroups = new HashMap<>();
    for (String path : tplPaths) {
      if(path == null) continue;
      File tplPath = new File(path);
      if(!tplPath.exists()) continue;
      for (String tplFolder : tplPath.list()) {
        String tpli = tplPath.toURI().toString();
        if (new File(URI.create(tpli + tplFolder + "/" + "groups.xml")).exists()) {
          Groups g;
          try {
            g = new Groups(tpli + tplFolder, TemplateScope.BUILDIN);
            for(Group gr: g.getAllGroups())
              reportGroups.put(gr.getId(), gr);
          } catch (IOException e) {
            RLogger.logError(e);
          }
        }
      }
    }
    for (IConfigurationElement conf : configurationElements) {
      if (!conf.getName().equals("dataProvider")) {
        continue;
      }
    }
    return reportGroups;
  }
  // ///////////////////////////////////////////////////////////////////////////
  //
  // Test generators operations
  //
  // ///////////////////////////////////////////////////////////////////////////

  private SortedSet<TestGeneratorDescriptor> generators = new TreeSet<>();

  public SortedSet<TestGeneratorDescriptor> getTestGenerators() {
    loadTestGeneratorsDescriptors();
    return Collections.unmodifiableSortedSet(generators);
  }

  public void loadTestGeneratorsDescriptors() {
    IExtensionRegistry reg = Platform.getExtensionRegistry();
    IConfigurationElement[] configurationElements =
        reg.getConfigurationElementsFor(EXTENSION_NAME_TEST_GENERATORS);
    for (IConfigurationElement conf : configurationElements) {
      try {
        ITestGenerator docProc = (ITestGenerator) conf.createExecutableExtension("class");
        if (docProc != null)
          generators.add(
              new TestGeneratorDescriptor(
                  conf.getAttribute("id"), conf.getAttribute("name"), docProc));
      } catch (Exception e) {
        RLogger.logError(e);
      }
    }
    configurationElements =
        reg.getConfigurationElementsFor(EXTENSION_NAME_TEST_GENERATORS_PROVIDER);
    for (IConfigurationElement conf : configurationElements) {
      try {
        ITestGeneratorsProvider docProc =
            (ITestGeneratorsProvider) conf.createExecutableExtension("class");
        if (docProc != null) {
          TestGeneratorDescriptor[] desc = docProc.getDescriptors();
          if (desc != null) generators.addAll(Arrays.asList(desc));
        }
      } catch (Exception e) {
        RLogger.logError(e);
      }
    }
  }

  // ///////////////////////////////////////////////////////////////////////////
  //
  // Processors operations
  //
  // ///////////////////////////////////////////////////////////////////////////

  private SortedSet<DocumentProcessorDescriptor> processors =
      new TreeSet<>(new Comparator<DocumentProcessorDescriptor>() {

    @Override
    public int compare(DocumentProcessorDescriptor o1, DocumentProcessorDescriptor o2) {
      return o1.getId().compareTo(o2.getId());
    }
  });

  public SortedSet<DocumentProcessorDescriptor> getProcessors() {
    plock.lock();
    try {
      if (!processors.isEmpty())
        return Collections.unmodifiableSortedSet(processors);
      loadDocumentProcessorDescriptors();
      return Collections.unmodifiableSortedSet(processors);
    }finally {
      plock.unlock();
    }
  }

  public Object instanceObject (ClassLoader baseClassLoader, String className)
      throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    Class c = null;
    for(ClassLoader cl:API.getClassLoaders()){
      try {
        c = Class.forName(className,true, cl);
        if(c!=null)
          break;
      } catch (ClassNotFoundException e) {
      }
    }
    if(c!=null)
      return c.newInstance();
    return null;
  }

  public void loadDocumentProcessorDescriptors() {
    IExtensionRegistry reg = Platform.getExtensionRegistry();
    IConfigurationElement[] configurationElements =
        reg.getConfigurationElementsFor(EXTENSION_NAME_DOC_PROCESSOR);
    plock.lock();
    try {
      if(!processors.isEmpty())
        return;
      for (IConfigurationElement conf : configurationElements) {
        try {
          String clas = conf.getAttribute("class");
          IDocumentProcessor docProc = (IDocumentProcessor) instanceObject(null, clas);
          if(docProc!=null) {
            DocWizard wiz = null;
            try {
              wiz = (DocWizard) instanceObject(null, conf.getAttribute("wizard"));
            } catch (Exception e) {
              //TODO has no wizards
            }
            processors.add(
              new DocumentProcessorDescriptor(
                  conf.getAttribute("id"), conf.getAttribute("name"), docProc, wiz));
          }
          
        } catch (Exception e) {
          RLogger.logError(e);
        }
      }
    }finally {
      plock.unlock();
    }
  }

  // //////////////////////////////////////////////////////////////////////////
  //
  // Resources
  //
  // //////////////////////////////////////////////////////////////////////////
  public static String getResPath(String string) throws IOException {
    java.net.URL res =
        FileLocator.toFileURL(Activator.getDefault().getBundle().getResource(string));
    if (res == null) return null;
    return res.getPath();
  }

  public String getJSToolsPath() {
    String path = null;
    try {
      path = new File( FileLocator.toFileURL(getBundle().getEntry("jstools")).getPath()).getCanonicalPath();
    } catch (IOException e) {
      RLogger.logError(e);
    }
    return path;
  }

  /**
   * Returns an image descriptor for the image file at the given plug-in relative path
   *
   * @param path the path
   * @return the image descriptor
   */
  public static ImageDescriptor getImageDescriptor(String pluginId, String path) {
    return imageDescriptorFromPlugin(pluginId, path);
  }

  public static ImageDescriptor getImageDescriptor(String path) {
    return imageDescriptorFromPlugin(PLUGIN_ID, path);
  }

  @Override
  protected ImageRegistry createImageRegistry() {
    ImageRegistry imgreg = super.createImageRegistry();
    Map<String, NodeTypeDescriptor> types = RequalityPlugin.getAdditionalTypes();
    for (String n : types.keySet()) {
      NodeTypeDescriptor desc = RequalityPlugin.getAdditionalTypes().get(n);
      if (desc.getIcon() != null) {
        imgreg.put(
            "nodetype." + n,
            getImageDescriptor(
                desc.getPluginId(), RequalityPlugin.getAdditionalTypes().get(n).getIcon()));
      } else {
        imgreg.put("nodetype." + n, getImageDescriptor(desc.getPluginId(), "icons/unitesk.png"));
      }
    }
    java.io.File f = null;
    try {
      f =
          new File(
              FileLocator.toFileURL(Platform.getBundle(PLUGIN_ID).getEntry("icons/overlays"))
                  .getPath());
    } catch (IOException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    if (f != null && f.exists()) {
      File[] files = f.listFiles();
      for (File file : files) {
        try {
          imgreg.put(
              "icons/overlays/" + file.getName(),
              getImageDescriptor("icons/overlays/" + file.getName()));
        } catch (Exception e) {
          imgreg.dispose();
        }
      }
    }
    imgreg.put("nodetype.NodeType", getImageDescriptor("icons/nodetypes/NodeType.png"));
    imgreg.put("nodetype.default", getImageDescriptor("icons/unitesk.png"));
    imgreg.put("arrowup", getImageDescriptor("icons/uparrow.png"));
    imgreg.put("arrowdown", getImageDescriptor("icons/downarrow.png"));

    imgreg.put("green_dot", getImageDescriptor("icons/green_dot.png"));
    imgreg.put("red_dot", getImageDescriptor("icons/red_dot.png"));
    imgreg.put("yellow_dot", getImageDescriptor("icons/yellow_dot.png"));
    return imgreg;
  }

  // //////////////////////////////////////////////////////////////////////////
  //
  // Project tracking
  //
  // //////////////////////////////////////////////////////////////////////////

  private static EclipseTracker tracker = null;
  

  private HashMap<String, IAttributeGenerator> attributeGeneratorsMap;
  
  
  private static void initTracker(Activator a) throws CoreException {
    LinkedList<URI> reqProjects = new LinkedList<>();
    for (IProject changedProject : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
      RLogger.logDebug("found project "+changedProject.getName(),5);//local name by fact
      reqProjects.add(changedProject.getLocationURI());
    }
    IResTracker tracker = getTracker();
    TreeStateManager tsm = TreeStateManager.getOne(tracker);
    if(!DEBUG_FILE_TRACKER) {
      if(!reqProjects.isEmpty()){
        tracker.handleNewProjects(reqProjects);
      }
      ResManager.getManager().getTracker().setInited(true);
    }
      
  }

  //Method to get URL from file name. Created
  //here to be modified in future by using
  //bundles instead of file
  public static String getPath(String string) throws IOException {
    return FileLocator.toFileURL(getDefault().getBundle().getEntry(string)).getPath();
  }
//
//  public static URI getUrl(URI uri) throws IOException {
//    try {
//      return FileLocator.toFileURL(uri.toURL()).toURI();
//    } catch (URISyntaxException e) {
//      // TODO Auto-generated catch block
//      RLogger.logError(e);
//    }
//    return null;
//  }
  
  public static IProject getProjectByName(String split) {
    return EclipseTracker.getWorkspaceProject(null, split);
  }

  public Map<String, IAttributeGenerator> loadAttributeGeneratorsDescriptions() {
    if (attributeGeneratorsMap == null) {
      attributeGeneratorsMap = new HashMap<>();
      IExtensionRegistry reg = Platform.getExtensionRegistry();
      IConfigurationElement[] configurationElements =
          reg.getConfigurationElementsFor(EXTENSION_NAME_ATTRIBUTE_GENERATORS);
      for (IConfigurationElement conf : configurationElements) {
        try {
          IAttributeGenerator st = (IAttributeGenerator) conf.createExecutableExtension("class");
          String id = conf.getAttribute("id");
          attributeGeneratorsMap.put(id, st);

        } catch (Exception e) {
          RLogger.logError(e);
        }
      }
    }
    return attributeGeneratorsMap;
  }

  @SuppressWarnings("deprecation")
  public static void executeCommand(IHandler h, Map<?, ?> params) throws ExecutionException {
    IHandlerService hndlrService =
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getService(IHandlerService.class);
    h.execute(new ExecutionEvent(params, null, hndlrService.getCurrentState()));
  }

  private int maxFirstSubstring(String first, String second) {
    if (first == null || second == null || first.length() == 0 || second.length() == 0) {
      return 0;
    }
    int fl = first.length();
    int sl = second.length();
    int s = 0;
    for (s = 0; s < Math.min(sl, fl) && first.charAt(s) == second.charAt(s); s++) ;
    return s;
  }

  public String getProjectRelatedPath(String fname, IProject proj) {
    //TODO test
    fname = new File(fname).toURI().toString();
    String projPath = proj.getLocationURI().toString();
    int s = maxFirstSubstring(fname, projPath);
    if (s < 2) return fname;
    int pos = projPath.indexOf("/", s), upcount = projPath.length() > s ? 1 : 0;
    while (pos > -1) {
      upcount++;
      pos = projPath.indexOf("/", pos + 1);
    }
    StringBuilder sb = new StringBuilder("./");
    for (int i = 0; i < upcount; i++) sb.append("../");
    String end = fname.substring(s);
    if (sb.charAt(sb.length() - 1) == '/' && end.length() > 0 && end.charAt(0) == '/')
      end = end.substring(1);
    sb.append(end);
    return sb.toString();
  }

  public ICVSClient getSVCClientForTeamProvider(String id) {
    if (!supportMap.containsKey(id) || supportMap.get(id).size() == 0) return null;
    return supportMap.get(id).get(0);
  }


  public Map<String, List<ICVSClient>> getRepoProviders() {
    return supportMap;
  }

  public static EclipseTracker getTracker() {
    if(tracker == null)
      tracker = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
    return tracker;
  }

}
