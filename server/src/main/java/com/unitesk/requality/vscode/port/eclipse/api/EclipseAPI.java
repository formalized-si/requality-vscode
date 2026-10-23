package com.unitesk.requality.vscode.port.eclipse.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.unitesk.requality.vscode.ui.commands.operations.IOperationHistory;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoContext;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoableOperation;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.runtime.IExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.Platform;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialogWithToggle;
import com.unitesk.requality.vscode.ui.swt.graphics.Font;
import com.unitesk.requality.vscode.ui.swt.graphics.FontData;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.team.RepositoryProvider;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.api.Global.ICheckerContainerProvider;
import com.unitesk.requality.core.api.IAPICacheProvider;
import com.unitesk.requality.core.api.IAPICoverageSource;
import com.unitesk.requality.core.api.IAPIDeleteHandlers;
import com.unitesk.requality.core.api.IAPIRepoSource;
import com.unitesk.requality.core.api.IAPIReportSource;
import com.unitesk.requality.core.api.IAPISource;
import com.unitesk.requality.core.api.IParamValueProvider;
import com.unitesk.requality.core.api.MassOppDescriptor;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.descriptors.CoverageStorageSourceDescriptor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.operation.IDeleteHandler;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.document.DocumentProcessor;
import com.unitesk.requality.core.operation.mass.MassAttributeTypeChange;
import com.unitesk.requality.core.operation.mass.MassAttributeValueChange;
import com.unitesk.requality.core.operation.mass.MassDoorsIDImporter;
import com.unitesk.requality.core.operation.undoredo.IUndoRedoOperator;
import com.unitesk.requality.core.operation.undoredo.UndoRedoMech;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ISectionModel;
import com.unitesk.requality.core.transaction.TransactionStorage;
import com.unitesk.requality.core.transaction.UndoableTreeDBOperation;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseCheckerContainer;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseUtils;
import com.unitesk.requality.vscode.port.eclipse.operations.UndoableEclipseTreeDBOperation;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.EclipseModalMessageConsumer;
import com.unitesk.requality.vscode.port.eclipse.views.RequalityPreferences;
import com.unitesk.requality.vscode.port.eclipse.wizards.ProjectMappingPage;
import com.unitesk.requality.vscode.port.eclipse.wizards.massops.AbstractExtraSettingsPage;
import com.unitesk.requality.vscode.port.eclipse.wizards.massops.AttrTypeChangeExtraSettingsPage;
import com.unitesk.requality.vscode.port.eclipse.wizards.massops.AttrValueChangeExtraSettingsPage;
import com.unitesk.requality.vscode.port.eclipse.wizards.massops.ReqIFFIleSelectionPage;
import com.unitesk.requality.repo.ICVSClient;
import com.unitesk.requality.repo.IRepoMapper;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.tests.ICoverageStorageSource;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.message.RMessages;

public class EclipseAPI implements IAPISource, IAPICacheProvider, IAPICoverageSource, IAPIReportSource, IAPIRepoSource, IAPIDeleteHandlers {
  private static Collection<MassOppDescriptor> descrs;
  private static Collection<IRepoMapper> repoMappers = null;
  private static List<ICVSClient> repoClients = null;
  private static EclipseAPI one = null;
  public EclipseAPI(){
    if(one == null)
      one = this;
    else
      RLogger.logError("Eclipse api duplication problem");
  }
  public static EclipseAPI getOne(){
    if(one == null)
      one = new EclipseAPI();
    return one;
  }
  {
    descrs = new ArrayList<>();

    descrs.add(
        new MassOppDescriptorWithPages(
            "com.requality.attributeValueChange",
            "AttributeValueChange",
            new MassAttributeValueChange(),
            false) {
          @Override
          public List<Class<? extends AbstractExtraSettingsPage>> getPages() {
            List<Class<? extends AbstractExtraSettingsPage>> l =
                new LinkedList<>();
            l.add(AttrValueChangeExtraSettingsPage.class);
            return l;
          }
        });
    descrs.add(
        new MassOppDescriptorWithPages(
            "com.requality.attributeTypeChange",
            "AttributeTypeChange",
            new MassAttributeTypeChange(),
            false) {
          @Override
          public List<Class<? extends AbstractExtraSettingsPage>> getPages() {
            List<Class<? extends AbstractExtraSettingsPage>> l =
                new LinkedList<>();
            l.add(AttrTypeChangeExtraSettingsPage.class);
            return l;
          }
        });
    descrs.add(
        new MassOppDescriptorWithPages(
            MassDoorsIDImporter.ID, "DoorsIdImporter", new MassDoorsIDImporter(), false) {
          @Override
          public List<Class<? extends AbstractExtraSettingsPage>> getPages() {
            List<Class<? extends AbstractExtraSettingsPage>> l =
                new LinkedList<>();
            l.add(ReqIFFIleSelectionPage.class);
            l.add(ProjectMappingPage.class);
            return l;
          }
        });
    //		descrs.add(new MassOppDescriptorWithPages(
    //			add(new MassOppDescriptor("com.requality.attributeValueChange", "AttributeValueChange", new MassAttributeValueChange()));
    //			add(new MassOppDescriptor("com.requality.attributeTypeChange", "AttributeTypeChange", new MassAttributeTypeChange()));))
  }


  @Override
  public Collection<MassOppDescriptor> loadMassOpsDescriptors() {
    return descrs;
  }

  @Override
  public Map<String, Template> getReportTemplates(TreeDB db) {
	return null;//No additional templates provided
    //return TemplatesManager.getOne(db).getTemplates();
  }

  @Override
  public Map<String, IParamValueProvider> getParamProviders() {
    Map<String, IParamValueProvider> map = new HashMap<>();
    map.put("requalityVersion", new IParamValueProvider() {
      
      @Override
      public TypedValue getTypedValue(String key, AttributeStorage as, IProgressMonitor mon, Object ... meta) {
        String v = getToolVersion();
        return new TypedValue(getAttributeType(), v);
      }
      
      @Override
      public String getType() {
        return "eclipseRequalityVersionProvider";
      }
      @Override
      public AttributeType getAttributeType() {
        return AttributeType.STRING;
      }
    });
  	return map;
  }
  public String getToolVersion() {
    String tv = Activator.getDefault().getBundle().getHeaders().get("Bundle-Version");
    if(tv.endsWith("0.0.0.SNAPSHOT"))
       //dev versions
      tv = "DEVELOPMENT VERSION";//TODO load git commit
	return tv;
  }

  @Override
  public Map<String, List<ICVSClient>> getRepositoryProviders() {
    return Activator.getDefault().getRepoProviders();
  }

  @Override
  public SortedMap<String, IDeleteHandler> getDeleteHandlers() {
    return Activator.getDefault().getDeleteHandlers();
  }
  @Override
  public Collection<IRepoMapper> getRepositoryMappers() {
    if(repoMappers == null){
      repoMappers = new ConcurrentLinkedQueue<>();
      repoMappers.add(new IRepoMapper() {
        @Override
        public String getRepoProviderId(TreeDB project) {
          IProject p = EclipseTracker.getTracker(DBNamesManager.DEFAULT).getProject(project);
          return getRepoProviderId(p);
        }
        public String getRepoProviderId(IProject p) { 
          if(p == null)
            return null;
          RepositoryProvider rp = RepositoryProvider.getProvider(p);
          if(rp!=null)
            return rp.getID();
          return null;
        }

		@Override
		public String getRepoProviderId(URI project) {
          IProject[] projs = EclipseTracker.getTracker(DBNamesManager.DEFAULT).getProjects();
          String reqId = project.toString();
          if(reqId.endsWith("/"))
            reqId = reqId.substring(0, reqId.length()-1);
          for(IProject p : projs) {
            URI u1 = p.getLocationURI();
            String pid = u1.toString();
            if(pid.endsWith("/"))
              pid = pid.substring(0, pid.length()-1);
            if(reqId.startsWith(pid)) {
              return getRepoProviderId(p);
            }
          }
          return null;
		}
      });
    }
    return repoMappers;
  }

  @Override
  public Map<String, List<ISectionModel>> getSectionModels(boolean isLocal) {
    // TODO Auto-generated method stub
    return null;
  }
  public static void showNoBrowserEngine(){
    boolean isGui = EclipseAPI.getDisplay()!=null;
    if (Activator.getDefault()
        .getPreferenceStore()
        .getBoolean(RequalityPreferences.OPEN_NO_ENGINES_DIALOGS)) {
      Runnable r = (new Runnable(){
      
        @Override
        public void run() {
          if (Activator.getDefault()
              .getPreferenceStore()
              .getBoolean(RequalityPreferences.NOT_OPEN_EDITORS)) return;
          MessageDialogWithToggle mdt = 
              MessageDialogWithToggle.openInformation(
                  null,
                  "Browser engines are not available",
                  "There are no available browser engines. Please install XulRunner or WebKit. \nFollow instructions from http://requality.org/en/general.en.html#browser_integration. \nFurther work can lead to unexpected results.",
                  "Do not show again",
                  false,
                  Activator.getDefault().getPreferenceStore(),
                  RequalityPreferences.OPEN_NO_ENGINES_DIALOGS);
          if (mdt.getToggleState())
            Activator.getDefault()
                .getPreferenceStore()
                .setValue(RequalityPreferences.OPEN_NO_ENGINES_DIALOGS, false);
        }
      });
      if(isGui)
        r.run();
      else
        EclipseAPI.getDisplay().syncExec(r);
    }
  }

  @Override
  public void init() {
	Activator.getDefault().initTreesTracker();
    DocumentProcessor.eclipseGUI = true;
    Global.checkProvider = new ICheckerContainerProvider(){
      @Override
      public CheckerContainer getCheckerContainer(TreeDB db, RevisionInfo ri) {
    	if(ri==null || ri == RevisionInfo.NO_REV || ri == RevisionInfo.NONE)
          return EclipseCheckerContainer.getOne(db, ri);
        return CheckerContainer.DISABLED_CHECKER_CONTAINER;
      }
    };
    UndoRedoMech.setOperator(new IUndoRedoOperator(){
      @Override
      public boolean callUndo() {
        try {
          IOperationHistory ih = PlatformUI.getWorkbench().getOperationSupport().getOperationHistory();
          IUndoableOperation op = ih.getUndoOperation(
              PlatformUI.getWorkbench().getOperationSupport().getUndoContext());
          if(op == null)
            return false;
          if(!op.canUndo())
            return false;
          ih.undoOperation(op, null, null);
        } catch (Exception e) {
            RLogger.logError("error executing command org.eclipse.ui.edit.undo "+CoreUtils.getTrace(e));
        }
        return true;
      }
    
      @Override
      public boolean callRedo() {
        try {
          IOperationHistory ih = PlatformUI.getWorkbench().getOperationSupport().getOperationHistory();
          IUndoableOperation op = ih.getRedoOperation(
              PlatformUI.getWorkbench().getOperationSupport().getUndoContext());
          if(op == null)
            return false;
          if(!op.canRedo())
            return false;
          ih.redoOperation(op, null, null);
        } catch (Exception e) {
            RLogger.logError("error executing command org.eclipse.ui.edit.redo "+CoreUtils.getTrace(e));
        }
        return true;
      }

      @Override
      public List<UndoableTreeDBOperation> getUndoList() {
        IOperationHistory ih = PlatformUI.getWorkbench().getOperationSupport().getOperationHistory();
         IUndoableOperation[] ops = ih.getUndoHistory(
             PlatformUI.getWorkbench().getOperationSupport().getUndoContext()).clone();
        List<UndoableTreeDBOperation> list = new LinkedList<>();
        for(IUndoableOperation opa:ops){
          if(opa instanceof UndoableEclipseTreeDBOperation)
            list.add(((UndoableEclipseTreeDBOperation)opa).getCoreOperation());//TODO non requality?
        }
        return list;
      }

      @Override
      public List<UndoableTreeDBOperation> getRedoList() {
        IOperationHistory ih = PlatformUI.getWorkbench().getOperationSupport().getOperationHistory();
        IUndoableOperation[] ops = ih.getRedoHistory(
            PlatformUI.getWorkbench().getOperationSupport().getUndoContext()).clone();
        List<UndoableTreeDBOperation> list = new LinkedList<>();
        for(IUndoableOperation opa:ops){
          if(opa instanceof UndoableEclipseTreeDBOperation)
            list.add(((UndoableEclipseTreeDBOperation)opa).getCoreOperation());//TODO non requality?
        }
        return list;
      }
      @Override
      public void callReset() {
        EclipseUtils.resetUndoRedo();
      }

     @Override
     public void registerOperation(TransactionStorage tStorage) {
        UndoableEclipseTreeDBOperation operation = UndoableEclipseTreeDBOperation.getOne(tStorage);
        operation.addContext(getContext());
        IOperationHistory history =
          PlatformUI.getWorkbench().getOperationSupport().getOperationHistory();
        history.add(operation);
     }

      private IUndoContext getContext() {
        return PlatformUI.getWorkbench().getOperationSupport().getUndoContext();
      }
    });
    RMessages.initMessageConsumer(new EclipseModalMessageConsumer());
//    WorkspaceJob wj = new WorkspaceJob("init browsers engines"){
//    
//      @Override
//      public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
//        int bt = RequalityPlugin.getBrowserType();
//        if(bt == RequalityPlugin.NOT_INITED)
//        {
//          this.schedule(2000);
//          return Status.CANCEL_STATUS;
//        }
//        if (bt == SWT.NONE) {
//          showNoBrowserEngine();
//        }
//        return Status.OK_STATUS;
//      }
//      
//    };
//    wj.setPriority(Job.DECORATE);
//    wj.schedule();
    API.replaceProgressMonitorManager(new EclipseMonitorManager());
    RLogger.logInfo("Requality "+getToolVersion()+" was inited");
  }
  @Override
  public void destroy() {
    RequalityPlugin.stopCheckers();
  }

  @Override
  public void clearCaches(TreeDB db, boolean delete) {
    IUndoContext context = PlatformUI.getWorkbench().getOperationSupport().getUndoContext();
    PlatformUI.getWorkbench().getOperationSupport().getOperationHistory().dispose(context, true, true, true);
  }

  public static Display getDisplay() {
	if(display == null)
      synchronized (Activator.WAIT_DISPLAY) {
    	try {
    		System.err.println("WAIT DISPLAY "+CoreUtils.getShortTrace());
			Activator.WAIT_DISPLAY.wait();
			System.err.println("test display "+CoreUtils.getShortTrace()+" "+(display==null?"null":"not null"));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
      }
	return display;
  }

  public static void setDisplay(Display display) {
	EclipseAPI.display = display;
	synchronized (Activator.WAIT_DISPLAY) {
	  Activator.WAIT_DISPLAY.notifyAll();
	}
  }

  public static final String EXTENSION_NAME_COVERAGE_SOURCES =
      "com.unitesk.requality.coveragesource";
  public static Map<String, CoverageStorageSourceDescriptor> coverageInputMethods;
  public static Map<String, String> coverageIdToName;

  private static Display display = null;

  public static void loadCoverageInputMethods() {
	if (coverageInputMethods != null)
      return;
    coverageInputMethods = new HashMap<>();
    coverageIdToName = new HashMap<>();
    IExtensionRegistry reg = Platform.getExtensionRegistry();
    IConfigurationElement[] configurationElements =
        reg.getConfigurationElementsFor(EXTENSION_NAME_COVERAGE_SOURCES);
    for (IConfigurationElement conf : configurationElements) {
      try {
        ICoverageStorageSource st =
            (ICoverageStorageSource) conf.createExecutableExtension("class");
        String id = conf.getAttribute("id");
        coverageInputMethods.put(
            id, new CoverageStorageSourceDescriptor(id, conf.getAttribute("name"), st));
        coverageIdToName.put(id, conf.getAttribute("name"));

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  @Override
  public String getId() {
    return "ru.ispras.API.Eclipse";
  }
  @Override
  public Map<String, CoverageStorageSourceDescriptor> getCoverageSources() {
    EclipseAPI.loadCoverageInputMethods();
    return coverageInputMethods;
  }

  public static ICoverageStorageSource getCoverageSource(String type) {
	EclipseAPI.loadCoverageInputMethods();
    if (coverageInputMethods.get(type) == null) return null;
    return coverageInputMethods.get(type).getStorageInput();
  }


  public Map<String, String> getCoverageSourceNames() {
	    EclipseAPI.loadCoverageInputMethods();
    return coverageIdToName;
  }
  private static Map<FontData, Font> fonts = new HashMap<>();
  public static Font getFont(FontData fd) {
    if(fonts.isEmpty()) {
      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        @Override
        public void run() {
          for(Font f:fonts.values())
            f.dispose();
        }
      }));
    }
    for(Entry<FontData, Font> en: fonts.entrySet()) 
      if(en.getKey().equals(fd)&&!en.getValue().isDisposed())
        return en.getValue();
    Font f = new Font(EclipseAPI.getDisplay(), fd);
    fonts.put(fd, f);
    return f;
  }

}
