package com.unitesk.requality.core.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.DataType;
import com.unitesk.requality.core.attribute.DataTypeAttribute;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.valueprovider.CoverageSourcesList;
import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;
import com.unitesk.requality.core.descriptors.CoverageStorageSourceDescriptor;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.events.TreeNodeChangeEvent.TreeNodeChangeEventType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.create.CreateCommentOperation;
import com.unitesk.requality.core.operation.create.CreateDocFolderOperation;
import com.unitesk.requality.core.operation.create.CreateElementOperation;
import com.unitesk.requality.core.operation.create.CreateNodeTypeOperation;
import com.unitesk.requality.core.operation.create.CreateReportFolderOperation;
import com.unitesk.requality.core.operation.create.CreateReportSettingsOperation;
import com.unitesk.requality.core.operation.create.CreateRequirementOperation;
import com.unitesk.requality.core.operation.create.CreateRestrictedFolderOperation;
import com.unitesk.requality.core.operation.create.CreateTestPurposeOperation;
import com.unitesk.requality.core.operation.create.CreateTextNodeOperation;
import com.unitesk.requality.core.operation.create.CreateVirtualNodeOperation;
import com.unitesk.requality.core.operation.mass.LibreOfficeAfterProcessing;
import com.unitesk.requality.core.operation.mass.MassIDRenames;
import com.unitesk.requality.core.operation.mass.MassKindFixer;
import com.unitesk.requality.core.operation.mass.MassLocationsFixer;
import com.unitesk.requality.core.operation.mass.MassRefFixer;
import com.unitesk.requality.core.operation.mass.MassReqTypeFixer;
import com.unitesk.requality.core.operation.mass.MassTNHeaderExplode;
import com.unitesk.requality.core.operation.mass.NodeSearch;
import com.unitesk.requality.core.operation.mass.NullTreeNodes;
import com.unitesk.requality.core.operation.mass.TermsHandler;
import com.unitesk.requality.core.propagator.ClonePropagator;
import com.unitesk.requality.core.propagator.LocationLabelPropagator;
import com.unitesk.requality.core.propagator.NumericIdPropagator;
import com.unitesk.requality.core.propagator.ParentAttrsPropagator;
import com.unitesk.requality.core.propagator.ParentChildrenUpdater;
import com.unitesk.requality.core.propagator.PositionChangePropagator;
import com.unitesk.requality.core.propagator.ReferenceRevertPropagator;
import com.unitesk.requality.core.propagator.ReportSettingsTIDPropagator;
import com.unitesk.requality.core.service.ResourcesProvider;
import com.unitesk.requality.core.service.WSServer;
import com.unitesk.requality.core.storage.ISectionModel;
import com.unitesk.requality.core.storage.ITreeStateListener;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.storage.cache.IgnoreCacheMan;
import com.unitesk.requality.core.storage.cache.LinksCacheMan;
import com.unitesk.requality.core.storage.cache.LocationCacheMan;
import com.unitesk.requality.core.storage.cache.NamesCacheMan;
import com.unitesk.requality.core.storage.cache.NewIndexesCacheMan;
import com.unitesk.requality.core.storage.cache.ProjSettsCacheMan;
import com.unitesk.requality.core.storage.cache.TermsCacheMan;
import com.unitesk.requality.core.storage.cache.UUIDsCacheMan;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.NodeType;
import com.unitesk.requality.nodetype.ReportFolder;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.RestrictedFolder;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.repo.CVSException;
import com.unitesk.requality.repo.ICVSClient;
import com.unitesk.requality.repo.RepoManager;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.tests.CoverageFromFile;
import com.unitesk.requality.tool.cli.CommandLineArgsProvider;

public class DefaultAPISource implements IAPISource, IAPICacheProvider, 
  IAPIReportSource, IAPIStoredVersionSource, IAPIChangePropogatorExtensions,
  IAPICreateElementOperationsExtension, IAPIDataTypeProvider, IAPICoverageSource,
  IAPIStaticValueProviderExtentions, IAPIDataSourceDescriptionProvider{
  public DefaultAPISource() {
    if(one == null)
      one = this;
    else
      RLogger.logError("Incorret double source init (defapi)");
  }
  @Override
  public Collection<MassOppDescriptor> loadMassOpsDescriptors() {
    return new LinkedList<MassOppDescriptor>() {
      /** */
      private static final long serialVersionUID = 8988349444956818013L;

      {
        {
          add(
              new MassOppDescriptor(
                  "com.requality.tnFix",
                  "Fix textnodes with req children",
                  new MassReqTypeFixer(),
                  true));
          add(
              new MassOppDescriptor(
                  "com.requality.locFix", "Fix locations", new MassLocationsFixer(), true));
          add(
              new MassOppDescriptor(
                  "com.requality.idRenames", "IDRenames", new MassIDRenames(), true));
          add(
              new MassOppDescriptor(
                  "com.requality.tNHeaderExplode",
                  "TNHeaderExplode",
                  new MassTNHeaderExplode(),
                  true));
          add(
              new MassOppDescriptor(
                  "com.requality.nullTreeNodes", "NullTreeNodes", new NullTreeNodes(), true));
          add(
              new MassOppDescriptor(
                  "com.requality.kindFixer", "Mass 'Вид' fixer", new MassKindFixer(), true));
          add(
              new MassOppDescriptor(
                  "com.requality.libreafterprocessing",
                  "After-import processing for libreoffice html documents",
                  new LibreOfficeAfterProcessing(),
                  false));
          add(
              new MassOppDescriptor(
                  "com.requality.termshandler",
                  "Terms handler",
                  new TermsHandler(),
                  false));
          
          add(
              new MassOppDescriptor(
                  "com.requality.refFixer", "Mass Reference fixer", new MassRefFixer(), true));
          add(
              new MassOppDescriptor(
                  "com.requality.searchNode", "Search Node", new NodeSearch(), true));//hidden for now
        }
      }
    };
  }

  private RequalityStoredVersionProvider rvp = new RequalityStoredVersionProvider();
  @Override
  public List<IStoredVersionProvider> getStoredversionProviders() {
    List<IStoredVersionProvider> set = new ArrayList<>();
    set.add(rvp);
    return set;
  }
  private static DefaultAPISource one = null;
  public static DefaultAPISource getOne() {
    if(one == null)
      one = new DefaultAPISource();
    return one;
  }

  private Map<String,IParamValueProvider> vps = null;
  @Override
  public Map<String, IParamValueProvider> getParamProviders() {
    if(vps == null){
      vps = new HashMap<>();
      vps.put("PROJECT_NAME", new IParamValueProvider(){
       
        @Override
        public TypedValue getTypedValue(String key, AttributeStorage as, IProgressMonitor mon, Object ... meta) {
          return new TypedValue(getAttributeType(), as.getNode().getTreeDB().getProjectName());
        }
      
        @Override
        public String getType() {
          return "ProjectNameValueProvider";
        }
      
        @Override
        public AttributeType getAttributeType() {
          return AttributeType.STRING;
        }

      });
      vps.put("PROJECT_LOCAL_NAME", new IParamValueProvider(){
          
          @Override
          public TypedValue getTypedValue(String key, AttributeStorage as, IProgressMonitor mon, Object ... meta) {
            return new TypedValue(getAttributeType(), as.getNode().getTreeDB().getLocalName());
          }
        
          @Override
          public String getType() {
            return "ProjectLocalNameValueProvider";
          }
        
          @Override
          public AttributeType getAttributeType() {
            return AttributeType.STRING;
          }

        });

    }
    return vps;
  }
  private Map<String, Integer> socketPorts = new HashMap<>();
  private Map<String, List<ISectionModel>> globalSectionModel = new HashMap<>();
  private Map<String, List<ISectionModel>> localSectionModel = new HashMap<>();
  {
    List<ISectionModel> gm = new LinkedList<>();
    gm.add(LinksCacheMan.getGlobalLatesCacheModel());
    gm.add(LinksCacheMan.getGlobalRevCacheModel());
    gm.add(TermsCacheMan.getCPUseTermsSection());
    gm.add(TermsCacheMan.getCPDefTermsSection());
    gm.add(TermsCacheMan.getCPRelationsSection());
    gm.add(ProjSettsCacheMan.getProjectSettingsCacheModel());
    globalSectionModel.put(DEFAULT, gm);
    
    List<ISectionModel> lm = new LinkedList<>();
    lm.add(LinksCacheMan.getRevCacheModel());;
    lm.add(NamesCacheMan.getNamesCacheModel());
    //localSectionModel.add(ResManager.getProjectSettingsCacheModel());
    //localSectionModel.add(ResManager.getUUIDsCacheModel());
    lm.add(TermsCacheMan.getUseTermsSection());
    lm.add(TermsCacheMan.getDefTermsSection());
    lm.add(TermsCacheMan.getRelationsSection());
    lm.add(LocationCacheMan.getLocationSection());
    lm.add(NewIndexesCacheMan.getIndexCacheSection());
    lm.add(UUIDsCacheMan.getUUIDsCacheModel());
    lm.add(ProjSettsCacheMan.getProjectSettingsCacheModel());
    lm.add(IgnoreCacheMan.getIgnoreCacheModel());
    localSectionModel.put(DEFAULT, lm);
    socketPorts.put("custom", 9926);
    socketPorts.put("markup", 9920);
    socketPorts.put("unieditor", 9921);
    socketPorts.put("reportview", 9922);
    socketPorts.put("moduleeditor", 9923);
    socketPorts.put("review", 9924);
    String[] sockets = socketPorts.keySet().toArray(new String[0]);
    String[] ca = CommandLineArgsProvider.cLineArgs;
    for(int i = 0;i<ca.length;i++){
      String lowerCa = ca[i].toLowerCase();
      for(String s:sockets) {
        String sname = "-"+s+"port".toLowerCase();
        int ind = lowerCa.indexOf(sname);
        if(ind>-1) {
          ind = lowerCa.indexOf("=", ind);
          if(ind != -1)
          {
            String portPart = lowerCa.substring(ind+1).trim();
            try{
              socketPorts.put(s, Integer.parseInt(portPart));
            }
            catch(Exception e){
              RLogger.logInfo("unsuccessfull parsing "+ca+" port setting");
            }
            
          }
        }
      }
    }
  }
  
  @Override
  public Map<String, List<ISectionModel>> getSectionModels(boolean isLocal) {
    if(isLocal)
      return localSectionModel;
    return globalSectionModel;
  }
  @Override
  public List<IAttributeChangePropagator> getAttributeChangePropogators(TreeNode node) {
    List<IAttributeChangePropagator> list = new LinkedList<>();
    list.add(ClonePropagator.getOne());
    list.add(ParentAttrsPropagator.getOne());
    if(node == null || node instanceof Requirement)
      list.add(LocationLabelPropagator.getOne());
    list.add(ReferenceRevertPropagator.getOne());
    list.add(ParentChildrenUpdater.getOne());
    list.add(NumericIdPropagator.getOne());
    list.add(PositionChangePropagator.getOne());
    if(node == null || node.getType().equals(ReportSettings.TYPE_NAME))
      list.add(ReportSettingsTIDPropagator.getOne());
    return list;
  }

  @Override
  public void init() {
    API.register(new RepoManager());
    initIgnoreMoveListener();
    WSServer.startSockets(socketPorts);

    ResourcesProvider.getOne().init();
  }

  @Override
  public void destroy() {
	WSServer.stopSockets();
	ResourcesProvider.getOne().destroy();
  }

  private ITreeStateListener tsl = new ITreeStateListener(){
    
    @Override
    public void settingsChanged(TreeDB db) {

    }
  
    @Override
    public void open(TreeDB db) {
      db.addListener(tcl);
    }
  
    @Override
    public void delete(String lname, String pname, TreeDB db) {
      db.removeListener(tcl);
    }
  
    @Override
    public void close(TreeDB db) {
      db.removeListener(tcl);
    }
  };
  //private static Map<TreeDB, ICVSClient> cc = new HashMap<>();
  private static String META_CVS_CLIENT = "CVS_CLIENT_INSTANCE";
  private static ICVSClient getClient(TreeDB db) throws Exception{
    ICVSClient cvs = db.getMetaObject(META_CVS_CLIENT);
    if(cvs == null){
      synchronized (META_CVS_CLIENT) {
        cvs = db.getMetaObject(META_CVS_CLIENT);
        if(cvs == null)
        {
          cvs = API.getRepositoryProvider(db);
          db.registerMetaObject(META_CVS_CLIENT, cvs);
        }
      }
    }
    return cvs;
  }
  private ITreeChangeListener tcl = new ITreeChangeListener(){
    
    @Override
    public void moved(
        TreeNode node, TreeNode oldParent, TreeNode newParent, Moment m, byte flag) {
      if(m == Moment.BEFORE){
        try {
          ICVSClient c = getClient(node.getTreeDB());
          if(c!=null&&c.getIsIgnored(oldParent, node.getId())&&!c.getIsIgnored(oldParent)){
            c.setIgnore(oldParent, node.getId(), false);
            c.setIgnore(newParent, node.getId(), true);
          }
        } catch (CVSException e) {
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  
    @Override
    public void deleted(TreeNode node, byte flag, Moment m) {
      if(m == Moment.BEFORE){
        try {
          ICVSClient c = getClient(node.getTreeDB());
          if(c!=null&&c.getIsIgnored(node)){
            c.setIgnore(node, false);
          }
        } catch (CVSException e) {
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  
    @Override
    public void created(TreeNode node, byte flag) {
    }
  
    @Override
    public void childrenOrderChanged(TreeNode node, byte flag) {
      // TODO Auto-generated method stub
      
    }
  
    @Override
    public void changed(TreeNode node, ChangesSet<Attribute> params, Moment m, byte flag) {
      if(m == Moment.BEFORE){
        Attribute oa = params.getOld(TreeNode.ATTR_ID);
        if(oa!=null && oa.getValue()!=null && node.getWorkParentUUID()!=null){
          try {
            ICVSClient c = getClient(node.getTreeDB());
            if(c!=null&&c.getIsIgnored(node.getWorkParent(),oa.getValue().toString())&&!c.getIsIgnored(node.getWorkParent())){
              c.setIgnore(node.getWorkParent(), oa.getValue().toString(), false);
              c.setIgnore(node.getWorkParent(), node.getId(), true);
            }
          } catch (CVSException e) {
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
    }
  
    @Override
    public void changed(List<TreeNodeChangeEvent> changes) {
      for(TreeNodeChangeEvent ce: changes){
    	byte flag = ce.getFlag();
        if(ce.getMoment() == Moment.BEFORE){
          if(ce.getType() == TreeNodeChangeEventType.MOVE)
            moved(ce.getTreeNodeTarget(), (TreeNode)ce.getArguments()[0], 
                ce.getTreeNodeTarget().getWorkParent(), Moment.BEFORE, flag);
          else if(ce.getType() == TreeNodeChangeEventType.CHANGE)
            changed(ce.getTreeNodeTarget(), (ChangesSet<Attribute>)ce.getArguments()[0], Moment.BEFORE, flag);
          else if (ce.getType() == TreeNodeChangeEventType.DELETE)
            deleted(ce.getTreeNodeTarget(), flag, Moment.BEFORE);
        }
        
      }
    }
  
    @Override
    public void beforeCreated(NodeDesc node, byte flag) {
      // TODO Auto-generated method stub
      
    }
  };
  private void initIgnoreMoveListener() {
    ResManager.getManager().addListener(Moment.BEFORE, tsl);
    for(TreeDB db:ResManager.getManager().getTreeDBs())
      db.addListener(tcl);
  }
  @Override
  public void clearCaches(TreeDB db, boolean delete) {
    // TODO Auto-generated method stub
    
  }
  @Override
  public Map<String, Template> getReportTemplates(TreeDB db) {
    // TODO Auto-generated method stub
    return null;
  }
  @Override
  public String getId() {
    return "ru.ispras.API.Core";
  }
  @Override
  public Map<String, CreateElementOperation> getCreateOperations() {
    Map<String, CreateElementOperation> cop = new HashMap<>();
    cop.put(RestrictedFolder.TYPE_NAME, new CreateRestrictedFolderOperation());
    cop.put(Comment.TYPE_NAME, new CreateCommentOperation());
    cop.put(DocFolder.TYPE_NAME, new CreateDocFolderOperation());
    cop.put(ReportFolder.TYPE_NAME, new CreateReportFolderOperation());
    cop.put(ReportSettings.TYPE_NAME, new CreateReportSettingsOperation());
    cop.put(Requirement.TYPE_NAME, new CreateRequirementOperation());
    cop.put(TestPurpose.TYPE_NAME, new CreateTestPurposeOperation());
    cop.put(TextNode.TYPE_NAME, new CreateTextNodeOperation());
    cop.put(VirtualNode.TYPE_NAME, new CreateVirtualNodeOperation());
    cop.put(NodeType.TYPE_NAME, new CreateNodeTypeOperation());
    return cop;
  }
  @Override
  public List<DataType> getDataTypes() {
    //TODO proper config on /config folder of project
    List<DataType> ret = new LinkedList<>();
    ret.add(new DataType("REVISION", "Map of availiable revisions for object(node of project).", "MAP[STRING]", null));
    
    ret.add(new DataType("COVERAGE_SOURCE_ID", "Identificator of coverage source. Can be commonly get from _coverageSources.", "STRING", 
    		new DataTypeAttribute[] {
    				new DataTypeAttribute("coverageStorageType", "Id of coverage source. List of coverage sources is availiable on dataSource '_coverageSources' ", "STRING")
    		}));
    
    ret.add(new DataType("COVERAGE_SOURCE_DATA", "Coverage source settings. | is commonly used as separator.", "STRING", 
    		new DataTypeAttribute[] {
    				new DataTypeAttribute("coverageStorageSettings", "Settings for chosed coverage source. Values are commonly divided by , separator. | can be used as additional separator. Additional info is availiable on dataSource description '_cov{id}' where id is an id of some coverage source", "STRING")
    		}));
    
    ret.add(new DataType("TRACEABILITY", "Settings map for traceabilitySettings report. Is divided into 5 sections. Section 1 is the templates for direct and revert links for node([1], [2] and so on are repling groups). Section 2,3,4,5 are rules for [1] [2] [3] [4] [5]. Each one contains 4 params - templates for text on resolved references, template for link in the same case, text for reference that points to nowhere and link for that case. In 2+ sections it is possible to use one of availiable constants denoted by {} braces. List of them - id of node {id}, user visible id -{user-visible-name}, name of attribute(reference) - {attribute-name}, value of attribute - {value}, name of revert link - {revert-attribute-name}, nameOrId on node - {name-or-id}, link to current report page for a hrefs - {this-page}, link to revert links page(direct if used on revert page) - {next-page}, link start for link on requality cnf {requality-cnf}  ", "LIST[LIST[STRING]]", null));
    
    ret.add(new DataType("COVERAGE", "Coverage settings for template. Includes id of coverage source - coverageStorageType and data stored by coverage source in coverageStorageSettings", "LIST[STRING]",
    		new DataTypeAttribute[] {
    				new DataTypeAttribute("coverageStorageType", "Id of coverage source. List of coverage sources is availiable on dataSource '_coverageSources' ", "COVERAGE_SOURCE_ID, STRING"),
    				new DataTypeAttribute("coverageStorageSettings", "Settings for chosed coverage source. Values are commonly divided by comma(',') separator. | can be used as additional separator. Additional info is availiable on dataSource description '_cov{id}' where id is an id of some coverage source", "COVERAGE_SOURCE_DATA, STRING")}));
    
	
    return ret;
  }
  @Override
  public Map<String, CoverageStorageSourceDescriptor> getCoverageSources() {
    Map<String, CoverageStorageSourceDescriptor> covS = new HashMap<>();

    covS.put(IAPICoverageSource.COVERAGE_FILE_ID, new CoverageStorageSourceDescriptor(
        IAPICoverageSource.COVERAGE_FILE_ID, "From File", new CoverageFromFile()));
    return covS;
  }
  @Override
  public Map<String, Pair<IValueProvider, Boolean>> getStaticValueProviders() {
    Map<String, Pair<IValueProvider, Boolean>> ret = new HashMap<>();
    ret.put("_coverageSources", new Pair<>(new CoverageSourcesList(), true));
    return ret;
  }
  @Override
  public List<DataSourceDescription> getDSDescriptions() {
    List<DataSourceDescription> desc = new LinkedList<>();
    desc.add(new DataSourceDescription("_coverageSources", "LIST[STRING]", "The list of id for existing data sources", false));
    for(Entry<String, CoverageStorageSourceDescriptor> en:API.getCoverageSources().entrySet())
    {
      //TODO mb different types instead of string
      desc.add(new DataSourceDescription("_cov"+en.getKey(), AttributeType.STRING, "Coverage settings for "+en.getKey()+" "+en.getValue().getSettingsDescription(),false));
    }
    return desc;
  }
}
