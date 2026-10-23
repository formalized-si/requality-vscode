package com.unitesk.requality.core.api;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.unitesk.requality.core.attribute.DataType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;
import com.unitesk.requality.core.checker.DefTermProjectChecker;
import com.unitesk.requality.core.checker.FIDProjectChecker;
import com.unitesk.requality.core.checker.INodeChecker;
import com.unitesk.requality.core.checker.ITreeChecker;
import com.unitesk.requality.core.checker.UVIdProjectChecker;
import com.unitesk.requality.core.descriptors.CoverageStorageSourceDescriptor;
import com.unitesk.requality.core.fi.FIProvider;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.localization.Localization;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.DefaultMonitorManager;
import com.unitesk.requality.core.operation.IDeleteHandler;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.create.CreateElementOperation;
import com.unitesk.requality.core.propagator.AttributePropagatorComparator;
import com.unitesk.requality.core.storage.ISectionModel;
import com.unitesk.requality.core.storage.cache.CacheManipulator;
import com.unitesk.requality.core.storage.cache.SettingsManipulator;
import com.unitesk.requality.repo.ICVSClient;
import com.unitesk.requality.repo.IRepoMapper;
import com.unitesk.requality.report.IReportGenerationParticipant;
import com.unitesk.requality.report.IReportTemplateProcessor;
import com.unitesk.requality.report.template.ParamDataProvider;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.tests.ICoverageStorageSource;
import com.unitesk.requality.tool.CoreUtils;
//TODO split it
public class API {
  public static byte[] GENERAL_SALT = null;
  private static Map<String, IParamValueProvider> paramProviders =
      null;
  private static FIProvider fip = null;
  
  private static List<ILightAPISource> allapis = new LinkedList<>();
  
  private static Set<Class>knownExtras = new HashSet<>();
  static{
    knownExtras.add(IAPISource.class);
    knownExtras.add(ILightAPISource.class);
    knownExtras.add(IAPIReportSource.class);
    knownExtras.add(IAPIRepoSource.class);
    knownExtras.add(IAPICoverageSource.class);
    knownExtras.add(IAPIStoredVersionSource.class);
    knownExtras.add(IAPIDataTypeProvider.class);
    knownExtras.add(IAPICacheProvider.class);
    knownExtras.add(IAPICacheManDBProvider.class);
    knownExtras.add(IAPICheckerProvider.class);
    knownExtras.add(IAPIChangePropogatorExtensions.class);
    knownExtras.add(IAPICreateElementOperationsExtension.class);
    knownExtras.add(IAPIStaticValueProviderExtentions.class);
    knownExtras.add(IAPIDynamicValueProviderExtension.class);
  }
  private static ServiceLoader<ILightAPISource> apiServices = null;
  private static List<IAPIDataTypeProvider> apiDataTypes = new LinkedList<>();
  private static List<IAPISource> apis = new LinkedList<>();
  private static List<IAPIReportSource> apiReport = new LinkedList<>();
  private static List<IAPIReportTemplateProcessorProviderSource> apiReportTemplateProcessorsProviders = new LinkedList<>();
  private static List<IAPIRepoSource> apiRepo = new LinkedList<>();
  private static List<IAPICoverageSource> apiCoverage = new LinkedList<>();
  private static List<IAPIStoredVersionSource> apiSVersion = new LinkedList<>();
  static List<IAPICacheProvider> apiCache = new LinkedList<>();
  private static List<IAPICacheManDBProvider> apiCacheManDBProvs = new LinkedList<>();
  private static List<IAPICreateElementOperationsExtension> apiCreateOps = new LinkedList<>();
  private static List<IReportGenerationParticipant> apiReportGenerationParticipants = new LinkedList<>();
  private static List<IApiCacheTemplateParamValueProvidersProvider> apiTemplParamVPP = new LinkedList<>();
  private static List<IAPICheckerProvider> apiChackerProviders = new LinkedList<>();
  private static List<IAPIChangePropogatorExtensions> apiNodeExts = new LinkedList<>();
  private static List<IAPIStaticValueProviderExtentions> apiSVP = new LinkedList<>();
  private static List<IAPIDynamicValueProviderExtension> apiDVP = new LinkedList<>();
  private static List<IAPIDataSourceDescriptionProvider> apiDataSourceDescrProvs = new LinkedList<>();
  private static List<IAPIDeleteHandlers> apiDeleteHandlers = new LinkedList<>();
  private static IReferenceVersionRuleProvider ivrp = null;
  private static Map<String, List<Object>> extraExtensions = new HashMap<>();  
  //private static RepoManager repoMan = new RepoManager();
  public static void addParamProvider(String name, IParamValueProvider vp){
    if(paramProviders.containsKey(name))
      throw new RuntimeException("Value provider for name '"+name+"' is already set");
    paramProviders.put(name, vp);
  }
  public static void initReferenceValueProvider(IReferenceVersionRuleProvider ivrp){
    if(API.ivrp!=null)
      throw new RuntimeException("Reference version rule provider was already inited");
    API.ivrp = ivrp;
  }
  public static IReferenceVersionRuleProvider getReferenceValueProvider(){
    return ivrp;
  }
  public static TypedValue getValue(String name, TreeDB db, IProgressMonitor mon, Object ... meta){
    if(paramProviders == null){
      paramProviders = new HashMap<>();
      for(IAPIReportSource api:apiReport){
        Map<String, IParamValueProvider> vps = api.getParamProviders();
        if(vps != null)
          for(String v: vps.keySet())
            addParamProvider(v, vps.get(v));
      }
    }
    TreeNode node = db.getRootNode();
    if(meta.length>0 && meta[0] instanceof TreeNode)
      node = (TreeNode)meta[0];
    if(!paramProviders.containsKey(name))
      return null;
    return paramProviders.get(name).getTypedValue(name, node.attrs(), mon, meta);
  }//TODO move to some distinct class
  public static void initFIProvider(FIProvider fip){
    API.fip = fip;
  }
  public static FIProvider getFIProvider(){
    return fip;
  }
  static {
    register(DefaultAPISource.getOne());
  }
  public static void initLibs(File[]libs) {
    loadExts(libs);
  }
  public static IStoredVersionProvider getStorageVersionProvider(String group) {
    for(IAPIStoredVersionSource api:apiSVersion){
      List<IStoredVersionProvider> sps = api.getStoredversionProviders();
      if(sps!=null)
        for(IStoredVersionProvider sp:sps)
          if(sp.getGroupName().equals(group))
            return sp;
    }
    return null;
  }
  private static Comparator<IStoredVersionProvider> provComp = new Comparator<IStoredVersionProvider>(){
    @Override
    public int compare(IStoredVersionProvider o1, IStoredVersionProvider o2) {
      return Integer.valueOf(o1.getPriority()).compareTo(o2.getPriority());
    }
    
  };
  public static List<IStoredVersionProvider> getStorageVersionProviders() {
    List<IStoredVersionProvider> provs = new ArrayList<>();
    for(IAPIStoredVersionSource api:apiSVersion){
      List<IStoredVersionProvider> sps = api.getStoredversionProviders();
      if(sps!=null)
        provs.addAll(sps);
    }
    provs.sort(provComp);
    return provs;
  }
  private static MassOppDescriptor descrs[];
  private static Set<String> initedIds;
  private static IMonitorManager monitorManager = new DefaultMonitorManager();
  public static void register(ILightAPISource source) {
    String id = source.getId();
    if(id==null)
      return;
    if(initedIds == null)
      initedIds = new HashSet<>();//init order croach
    if(initedIds.contains(id)){
      RLogger.logDebug("Trying to init already inited api source "+id, 2);
      return;
    }
    initedIds.add(id);
    if(source instanceof IAPIReportSource)
      apiReport.add((IAPIReportSource) source);
    if(source instanceof IAPIReportTemplateProcessorProviderSource) 
      apiReportTemplateProcessorsProviders.add((IAPIReportTemplateProcessorProviderSource)source);
    if(source instanceof IAPIRepoSource)
      apiRepo.add((IAPIRepoSource) source);
    if(source instanceof IAPICacheProvider)
      apiCache.add((IAPICacheProvider) source);
    if(source instanceof IAPIDataTypeProvider) 
      apiDataTypes.add((IAPIDataTypeProvider)source);
    if(source instanceof IAPICoverageSource)
      apiCoverage.add((IAPICoverageSource) source);
    if(source instanceof IAPIStoredVersionSource)
      apiSVersion.add((IAPIStoredVersionSource) source);
    if(source instanceof IAPIChangePropogatorExtensions)
      apiNodeExts.add((IAPIChangePropogatorExtensions) source);
    if(source instanceof IAPIStaticValueProviderExtentions)
      apiSVP.add((IAPIStaticValueProviderExtentions) source);
    if(source instanceof IAPIDynamicValueProviderExtension)
      apiDVP.add((IAPIDynamicValueProviderExtension) source);
    if(source instanceof IApiCacheTemplateParamValueProvidersProvider)
      apiTemplParamVPP.add((IApiCacheTemplateParamValueProvidersProvider) source);
    if(source instanceof IAPICacheManDBProvider)
      apiCacheManDBProvs.add((IAPICacheManDBProvider) source);
    if(source instanceof IAPISource)
      apis.add((IAPISource) source);
    allapis.add(source);
    if(source instanceof IAPIDataSourceDescriptionProvider)
      apiDataSourceDescrProvs.add((IAPIDataSourceDescriptionProvider)source);
    if(source instanceof IAPIDeleteHandlers)
      apiDeleteHandlers.add((IAPIDeleteHandlers) source);
    if(source instanceof IAPICheckerProvider)
      apiChackerProviders.add((IAPICheckerProvider) source);
    if(source instanceof IAPICreateElementOperationsExtension) 
      apiCreateOps.add((IAPICreateElementOperationsExtension) source);
    if(source instanceof IReportGenerationParticipant) {
      apiReportGenerationParticipants.add((IReportGenerationParticipant)source);
    }
    if(source instanceof IApiLocalizationSource)
    {
      IApiLocalizationSource locs = (IApiLocalizationSource) source;
      Map<Locale, ResourceBundle> map = locs.getLocalizations();
      if (map != null) {
        for(Entry<Locale, ResourceBundle> en : map.entrySet()) {
          if(en.getKey()!=null && en.getValue()!=null)
            Localization.registerResourcesBundle(en.getKey(), en.getValue());
        }
      }
    }
    for(Class<?> inter: source.getClass().getInterfaces()){
      if(inter.isAnnotationPresent(ApiExtension.class)){
        //source.getcl.getAnnotatedInterfaces()
        String cn = inter.getCanonicalName();
        if(!knownExtras.contains(inter)){
          List<Object> exts = extraExtensions.get(cn);
          if(exts == null){
            exts = new LinkedList<>();
            extraExtensions.put(cn, exts);
          }
          exts.add(source);
          extraExtensions.put(cn, exts);
        }
      }
    }
  }
  
  public static List<Object> getExtraExtensions(String c){
    return extraExtensions.get(c);
  }
  public static void replaceProgressMonitorManager(IMonitorManager mm){
    monitorManager = mm;
  }
  
  private static Class[] getClasses(ClassLoader clp, File[] libs, String packageName)
          throws ClassNotFoundException, IOException {
    int cnt = 0;;
    List<Class> classes = new LinkedList<>();
    List<URL> urlsl = new LinkedList<>();
    
    for(File chi:libs) {
      if(chi.isFile() && chi.getName().toLowerCase().endsWith(".jar")) {
        cnt++;
        urlsl.add(new URL("jar:file:" + chi.getAbsolutePath()+"!/"));
      }
      
    }
    for(File chi:libs) {
      if(chi.isFile() && chi.getName().toLowerCase().endsWith(".jar")) {
          JarFile jarFile = new JarFile(chi);
          try {
          Enumeration<JarEntry> e = jarFile.entries();
          //List<Class> classes = new LinkedList<>();
          while (e.hasMoreElements()) {
            JarEntry je = e.nextElement();
            if(je.isDirectory() || !je.getName().endsWith(".jar") || !je.getName().startsWith("libs") ){
                  continue;
              }
            urlsl.add(new URL("jar:file:" + chi.getAbsolutePath()+"!/"+je.getName()));
          }
          }finally {
            jarFile.close();
          }
      }
      
    }
    int i = 0;
    for(File chi:libs) {
      i++;
      JarFile jarFile = new JarFile(chi);
      try {
        RLogger.logInfo("handle "+chi.getName()+" "+i+" out of "+cnt);
        Enumeration<JarEntry> e = jarFile.entries();
        
        try (URLClassLoader classLoader = URLClassLoader.newInstance(urlsl.toArray(new URL[0]),clp)) {
          Thread.currentThread().setContextClassLoader(classLoader);
          e = jarFile.entries();
          while (e.hasMoreElements()) {
            JarEntry je = e.nextElement();
            if(je.isDirectory() || !je.getName().endsWith(".class")){
                continue;
            }
            // -6 because of .class
            String className = je.getName().substring(0,je.getName().length()-6);
            className = className.replace('/', '.');
            //System.out.println("handle "+className);
            try {
              Class c = Class.forName(className,true,classLoader);
              if(ILightAPISource.class.isAssignableFrom(c))
                classes.add(c);
            }catch(UnsatisfiedLinkError | NoClassDefFoundError | ClassFormatError | ExceptionInInitializerError | IllegalAccessError | VerifyError | Exception  ea ) {
              //RLogger.logError(ea.toString()+CoreUtils.getTrace(ea.getStackTrace()));
            }
          }
        }
        }finally {
          jarFile.close();
        }
    }
    return classes.toArray(new Class[classes.size()]);
  }
  private static void loadExts(File[] libs) {
     //File cf = new File("libs");
//     
    if(libs==null || libs.length==0)
      return;
    ClassLoader loader = ILightAPISource.class.getClassLoader();
  
    Class[] clazz;
    try {
      clazz = getClasses(loader,libs, "com/unitesk/requality/apiSources");
      for(Class c:clazz) {
        if(c == null)
          continue;
        try {
          Constructor[] cs = c.getConstructors();
          if(cs == null)
            continue;
          boolean inited = false;
          try {
            Method m = c.getMethod("getOne");
            try {
              if(m!=null) {
                ILightAPISource one = (ILightAPISource) m.invoke(null);
                if(one!=null) {
                  register(one);
                  inited = true;
                }
              }
            }catch(Exception e) {
              RLogger.logError(e);
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
          if(!inited) {
            if(cs.length==0){
              RLogger.logError(c.getCanonicalName()+" cannot be instantiated - no constructors!");
            }else {
              boolean failed = true;
              for(Constructor con: cs) {
                if(con.getTypeParameters().length==0) {
                  failed = false;
                  register((ILightAPISource) cs[0].newInstance());
                }
              }
              if(failed)
                RLogger.logError("Cannot instantiate "+c.getCanonicalName()+" no constructors with () call");    
            }
          }
        } catch (InstantiationException e) {
          RLogger.logError("Cannot instantiate "+c.getCanonicalName()+" "+e.toString()+" "+CoreUtils.getTrace(e));
        } catch (IllegalAccessException e) {
          RLogger.logError("Cannot instantiate "+c.getCanonicalName()+" "+e.toString()+" "+CoreUtils.getTrace(e));
        } catch (IllegalArgumentException e) {
          RLogger.logError("Cannot instantiate "+c.getCanonicalName()+" "+e.toString()+" "+CoreUtils.getTrace(e));
        } catch (InvocationTargetException e) {
          RLogger.logError("Cannot instantiate "+c.getCanonicalName()+" "+e.toString()+" "+CoreUtils.getTrace(e));
        } catch (SecurityException e) {
          RLogger.logError("Cannot instantiate "+c.getCanonicalName()+" "+e.toString()+" "+CoreUtils.getTrace(e));
        }
      }
    } catch (ClassNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
       //}
    if(apiServices==null)
    apiServices = ServiceLoader.load(ILightAPISource.class);
    Iterator<ILightAPISource> it = apiServices.iterator();
    while(it.hasNext())
      register(it.next());
    ClassLoader cl =ClassLoader.getPlatformClassLoader();

    
  }

  public static MassOppDescriptor[] getMassOppDescriptors() {
    if (descrs != null) return descrs;
    List<MassOppDescriptor> descrs = new LinkedList<>();
    for (IAPISource as : apis) {
      Collection<MassOppDescriptor> d = as.loadMassOpsDescriptors();
      if (d != null) descrs.addAll(d);
    }
    API.descrs = descrs.toArray(new MassOppDescriptor[0]);
    return API.descrs;
  }

  public static Map<String, CoverageStorageSourceDescriptor> getCoverageSources() {
    Map<String, CoverageStorageSourceDescriptor> ret = new HashMap<>();
    for (IAPICoverageSource api : apiCoverage) {
      Map<String, CoverageStorageSourceDescriptor> t = api.getCoverageSources();
      if (t != null) ret.putAll(t);
    }
    return ret;
  }

  public static ICoverageStorageSource getCoverageSource(String type) {
    CoverageStorageSourceDescriptor desc = getCoverageSources().get(type);
    if (desc != null) return desc.getStorageInput();
    return null;
  }

  public static Map<String, Template> getAPIReportTemplates(TreeDB db) {
    Map<String, Template> ret = Collections.emptyMap();
    for (IAPIReportSource api : apiReport) {
      try{
        Map<String, Template> t = api.getReportTemplates(db);
        if (t != null) {
          if(ret.isEmpty())
            ret = new HashMap<>();
          ret.putAll(t);
        }
      }catch(Exception e){
        e.printStackTrace();
      }
    }
    return ret;
  }
  public static List<ICVSClient> getRepositoryProviders() {
    List<ICVSClient> ret = new LinkedList<>();
    for (IAPIRepoSource api : apiRepo) {
      try{
        Map<String, List<ICVSClient>> t = api.getRepositoryProviders();
        if (t != null) 
          for(Entry<String, List<ICVSClient>> en:t.entrySet())
          for(ICVSClient c:en.getValue())
            ret.add(c);
      }catch(Exception e){
        e.printStackTrace();
      }
    }
    return ret;
  }
  public static ICVSClient getRepositoryProvider(TreeDB db) throws Exception {
    return Global.getRepoManager(db).getCVSClient();
  }
  public static ICVSClient getRepositoryProvider(String teamProviderId) {
    if(teamProviderId == null)
      return null;
    for (IAPIRepoSource api : apiRepo) {
      try{
        Map<String, List<ICVSClient>> t = api.getRepositoryProviders();
        if (t != null && t.containsKey(teamProviderId)) 
          for(ICVSClient c:t.get(teamProviderId))
            return c;
      }catch(Exception e){
        e.printStackTrace();
      }
    }
    return null;
  }

  public static IRepoMapper getRepoMapping(URI project) {
    if(project == null)
      return null;
    for (IAPIRepoSource api : apiRepo) {
      try{
        Collection<IRepoMapper> t = api.getRepositoryMappers();
        if (t != null) 
          for(IRepoMapper c:t){
            String id = c.getRepoProviderId(project);
            if(id!=null)
              return c;
          }
      }catch(Exception e){
        e.printStackTrace();
      }
    }
    return null;
  }
  public static IRepoMapper getRepoMapping(TreeDB project) {
    if(project == null)
      return null;
    project = Instantiator.getRootTree(project);
    for (IAPIRepoSource api : apiRepo) {
      try{
        Collection<IRepoMapper> t = api.getRepositoryMappers();
        if (t != null) 
          for(IRepoMapper c:t){
            String id = c.getRepoProviderId(project);
            if(id!=null)
              return c;
          }
      }catch(Exception e){
        e.printStackTrace();
      }
    }
    return null;
  }
  public static Map<String, Pair<Set<String>,Map<String, ISectionModel>>> getCacheSectionModels(boolean isLocal){
    Map<String, Pair<Set<String>,Map<String, ISectionModel>>> reta = new HashMap<>();
    for (IAPICacheProvider api : apiCache) {
      try{
        Map<String, List<ISectionModel>> sections = api.getSectionModels(isLocal);
        if(sections!=null)
          for(String s:sections.keySet())
          {
            Pair<Set<String>, Map<String, ISectionModel>> sect = reta.get(s);
            if(sect == null){
              final Map<String, ISectionModel> map = new TreeMap<>();
              Set<String> set = new TreeSet<>(new Comparator<String>(){
                @Override
                public int compare(String arg0, String arg1) {
                  ISectionModel m1 = map.get(arg0), m2 = map.get(arg1);
                  if(m1!=null && m2!=null)
                  {
                    int p1=m1.getPriority(), p2=m2.getPriority();
                    if(p1!=p2)
                      return Integer.compare(p2,p1);
                    return arg1.compareTo(arg0);
                  }
                  if(m1 == null && m2 != null)
                    return -1;
                  if(m1 == null && m2 != null)
                    return 1;
                  return 0;
                }
                
              });
              sect = new Pair<>(set, map);
              reta.put(s, sect);
            }
            Map<String, ISectionModel> map = sect.getSecond();
            List<ISectionModel> models = sections.get(s);
            if(models!=null)
              for(ISectionModel sm:models){
                sect.getSecond().put(sm.getSectionName(), sm);
                sect.getFirst().add(sm.getSectionName());
              }
          }
      }catch(Exception e){
        e.printStackTrace();
      }
    }
    return reta;
  }
  public static SortedSet<IAttributeChangePropagator> getAttributeChangePropogators(TreeNode node) {
    SortedSet<IAttributeChangePropagator> list = new TreeSet<>(AttributePropagatorComparator.getOne());
    for(IAPIChangePropogatorExtensions source:apiNodeExts){
      List<IAttributeChangePropagator> acp = source.getAttributeChangePropogators(node);
      if(acp!=null && !acp.isEmpty())
      {
        if(acp.contains(null)){
          RLogger.logError("null propogator in api source");
          acp.remove(null);
        }
        list.addAll(acp);
      }
    }
    return list;
  }
  public static void initSources(){
    for(IAPISource as:apis) {
      try {
        RLogger.logInfo("Inited api source "+as.getId());
        as.init();
      }catch(Exception e) {
        RLogger.logError(e);
      }
    }
  }
  public static void destroySources() {
  for(IAPISource as:apis) {
      RLogger.logInfo("Destroy api source "+as.getId());
      as.destroy();
    }  
  }
  public static Set<ClassLoader> getClassLoaders() {
    Set<ClassLoader> cls = new HashSet<>();
    for(ILightAPISource as:allapis)
      cls.add(as.getClass().getClassLoader());
    return cls;
  }
  public static Map<String, CacheManipulator<TreeDB>> initAPIDBPersistCacheManipulator(
      TreeDB treeDB, SettingsManipulator sm) {
    Map<String, CacheManipulator<TreeDB>> mans = Collections.emptyMap();
    for(IAPICacheManDBProvider cman:apiCacheManDBProvs)
    {
      Map<String, CacheManipulator<TreeDB>> smap = cman.getAPIDBPersistCacheManipulator(treeDB, sm);
      if(smap!=null && !smap.isEmpty()){
        if(mans.isEmpty())
          mans = new HashMap<>();
        mans.putAll(smap);
      }
    }
    return mans;
  }
  public static Map<String, CacheManipulator<TreeDB>> initAPIDBCacheManipulator(
      TreeDB treeDB, SettingsManipulator sm) {
    Map<String, CacheManipulator<TreeDB>> mans = Collections.emptyMap();
    for(IAPICacheManDBProvider cman:apiCacheManDBProvs)
    {
      Map<String, CacheManipulator<TreeDB>> smap = cman.getAPIDBCacheManipulator(treeDB, sm);
      if(smap!=null && !smap.isEmpty()){
        if(mans.isEmpty())
          mans = new HashMap<>();
        mans.putAll(smap);
      }
    }
    return mans;
  }
  public static List<INodeChecker> getCheckers(TreeDB db) {
    List<INodeChecker> ret = Collections.emptyList();
    for(IAPICheckerProvider cman:apiChackerProviders)
    {
      List<INodeChecker> prov = cman.getCheckers(db);
      if(prov!=null && !prov.isEmpty()){
        if(ret.isEmpty())
          ret = new LinkedList<>();
        ret.addAll(prov);
      }
    }
    
    return ret;
  }
  public static IMonitorManager getMonitorManager() {
    return monitorManager;
  }
  public static Map<String, Pair<IValueProvider, Boolean>> getStaticValueProviders() {
    Map<String, Pair<IValueProvider, Boolean>> ret = Collections.emptyMap();
    for(IAPIStaticValueProviderExtentions cman:apiSVP)
    {
      Map<String, Pair<IValueProvider, Boolean>> prov = cman.getStaticValueProviders();
      if(prov!=null && !prov.isEmpty()){
        if(ret.isEmpty())
          ret = new HashMap<>();
        ret.putAll(prov);
      }
    }
    return ret;
  }
  public static Map<String, Pair<IValueProviderProvider, Boolean>> getDynamicValueProviders() {
    Map<String, Pair<IValueProviderProvider, Boolean>> ret = Collections.emptyMap();
    for(IAPIDynamicValueProviderExtension cman:apiDVP)
    {
      Map<String, Pair<IValueProviderProvider, Boolean>> prov = cman.getDynamicValueProviders();
      if(prov!=null && !prov.isEmpty()){
        if(ret.isEmpty())
          ret = new HashMap<>();
        ret.putAll(prov);
      }
    }
    return ret;
  }
  public static List<ITreeChecker> getTreeCheckers(TreeDB db){
    List<ITreeChecker>list = new LinkedList<>();
    list.add(new FIDProjectChecker());
    list.add(new UVIdProjectChecker());
    list.add(new DefTermProjectChecker());
    return list;
  }
  public static List<IReportGenerationParticipant> getReportGenerationParticipants(){
    return Collections.unmodifiableList(apiReportGenerationParticipants);
  }
  public static Map<String, CreateElementOperation> getCreateElementOperations() {
    Map<String, CreateElementOperation> ret = new HashMap<>();
    for(IAPICreateElementOperationsExtension s: apiCreateOps) {
      Map<String, CreateElementOperation> apiret = s.getCreateOperations();
      if(apiret!=null && !apiret.isEmpty())
        ret.putAll(apiret);
    }
    return ret;
  }
  public static ParamDataProvider getReportParamValueProvider(String val) {
    for(IApiCacheTemplateParamValueProvidersProvider tvp: apiTemplParamVPP) {
      if(tvp.getIds().contains(val)) {
        ParamDataProvider vp = tvp.getProvider(val);
        if(vp!=null)
          return vp;
      }
    }
    return null;
  }
  public static Map<String, ParamDataProvider> getReportValueProviders() {
    Map<String, ParamDataProvider> ret = new HashMap<>();
    for(IApiCacheTemplateParamValueProvidersProvider tvp: apiTemplParamVPP) {
      for(String s:tvp.getIds()) {
        ParamDataProvider vp = tvp.getProvider(s);
        if(vp!=null)
          ret.put(s, vp);
      }
    }
    return ret;
  }
  private static List<DataType> dTypes = null;
  public static List<DataType> getDataTypes(){
    if(dTypes!=null)
      return dTypes;
    dTypes = new LinkedList<>();
    for(IAPIDataTypeProvider dp: apiDataTypes) {
      List<DataType> dts = dp.getDataTypes();
      if(dts!=null)
        for(DataType dt: dts)
          if(dt!=null)
            dTypes.add(dt);
    }
    dTypes = new CopyOnWriteArrayList<>(dTypes);
    return dTypes;
  }
  private static Map<String, DataSourceDescription> dpds = null;
  public static Map<String, DataSourceDescription> getDataProvidersDescriptions() {
    if(dpds != null)
      return dpds;
    dpds = new HashMap<>();
    for(IAPIDataSourceDescriptionProvider p: apiDataSourceDescrProvs) {
      List<DataSourceDescription> desc = p.getDSDescriptions();
      if(desc!=null)
        for(DataSourceDescription ds:desc)
          if(ds!=null)
            dpds.put(ds.id, ds);
    }
    return dpds;
  }
  public static SortedMap<String, IDeleteHandler> getDeleteHandlers() {
    SortedMap<String, IDeleteHandler> dhs = new TreeMap<>();
    for(IAPIDeleteHandlers dh: apiDeleteHandlers) { 
      Map<String, IDeleteHandler> dhs2 = dh.getDeleteHandlers();
      if(dhs2!=null)
        dhs.putAll(dhs2);
    }
    return dhs;
  }
  public static IReportTemplateProcessor getReportTemplateProcessor(String templateType, File file) throws Exception {
    if(templateType == null)
      return null;
    String lname = templateType.toLowerCase();
    for(IAPIReportTemplateProcessorProviderSource rp: apiReportTemplateProcessorsProviders){
      Collection <IReportTemplateProcessorProvider> provs = rp.getTemplateProcessorProviders();
      if(provs!=null)
        for(IReportTemplateProcessorProvider rpp: provs)
          if(rpp.getTemplateType()!=null && rpp.getTemplateType().toLowerCase().equals(lname))
            return rpp.getOne(file);
    }
    return null;
  }
}
