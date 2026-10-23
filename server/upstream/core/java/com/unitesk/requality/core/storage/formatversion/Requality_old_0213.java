package com.unitesk.requality.core.storage.formatversion;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.valueprovider.GeneratorValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.CacheInitializationStage;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.LinkResolver;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class Requality_old_0213 implements IFormatVersionConverter {
  private static String NODE_GROUP = "com.unitesk.nodegroup.requality";
  private OldTo0213Convert conv = new OldTo0213Convert(){
    private static ConcurrentHashMap<UUID, Object> registeredRoots = new ConcurrentHashMap<>();
    @Override
    Map<String, AttributeDefinition> reworkAttrs(UUID uid, Map<String, AttributeDefinition> ad, Pair<Map<Path, UUID>, Map<UUID, Path>> uuidMaps, String version, boolean isRoot) {
      return reworkAttrs(null, uid, ad, uuidMaps, version, isRoot);
    }
    Map<String, AttributeDefinition> reworkAttrs(UUID requestRoot, UUID uid, Map<String, AttributeDefinition> ad, Pair<Map<Path, UUID>, Map<UUID, Path>> uuidMaps, String version, boolean isRoot) {
      if(ad.containsKey("_revlinks"))
        ad.remove("_revlinks");
      if (ad.containsKey("_clones")) {
        ad.remove("_clones");
      }
      if (ad.containsKey("_%revlocations")) ad.remove("_%revlocations");
      if (ad.containsKey("_%clones")) ad.remove("_%clones");
      if (ad.containsKey("_revLocations")) {
        ad.remove("_revLocations");
      }
      AttributeDefinition def = ad.get("_%cloneparent");
      if(def!=null) {
        ad.remove("_%cloneparent");
        String oldVal = null;
        IValueProvider vp = def.getValueProvider();
        if(vp != null) {
          TypedValue tv = vp.getRawValue();
          if(tv != null && tv.getValue()!=null) 
            oldVal = tv.getValue().toString();
        }
        ad.put("_cloneparent", new AttributeDefinition(def.getOrigin(),
            "_cloneparent", def.getAvailability(), 
            new ReferenceValueProvider(
                        AttributeType.REFERENCE, oldVal), def.getFlags()));
      }
      def = ad.get("_%cloneid");
      if(def!=null) {
        ad.remove("_%cloneid");
        ad.put("_cloneid", new AttributeDefinition(def.getOrigin(),
            "_cloneid", def.getAvailability(), def.getValueProvider(), def.getFlags()));
      }
      if (ad.containsKey("_locations")) {
        AttributeDefinition a = ad.get("_locations");
        if(a==null||a.getRawValue()==null)
          ad.remove("_locations");
        if(a!=null && a.getRawValue()!=null && a.getRawValue().getValue() instanceof ListValue)
          ad.put(a.getKey(), new AttributeDefinition(a.getOrigin(), a.getKey(), new 
            ReferenceValueProvider(AttributeType.LIST, new ListValue(
            AttributeType.REFERENCE,
            new ArrayList<>(((ListValue) a.getRawValue().getValue()).getValues()))),a.getFlags()));
      }
      if (ad.containsKey("_cloneParent") || ad.containsKey("_cloneparent")) {
        AttributeDefinition a = ad.get("_cloneParent");
        if(a == null)
          a = ad.get("_cloneparent");
        ad.put("_cloneparent", new AttributeDefinition(a.getOrigin(), "_cloneparent", 
            new ReferenceValueProvider(AttributeType.REFERENCE, a.getRawValue().getValue()),a.getFlags()));
      }
      boolean isClone = false;
      if (ad.containsKey("_cloneparent")) {
        AttributeDefinition a = ad.get("_cloneparent");
        ad.put("_cloneparent", new AttributeDefinition(a.getOrigin(), a.getKey(), 
            new ReferenceValueProvider(AttributeType.REFERENCE, a.getRawValue().getValue()),a.getFlags()));
      }
      Iterator<String>it = ad.keySet().iterator();
      while(it.hasNext()){
        String s = it.next();
        AttributeDefinition a = ad.get(s);
        if(!a.getOrigin().equals(uid)&&!a.getKey().startsWith("_") ||s.startsWith("@")&&isRoot) {
          it.remove();
          continue;
        }
        if(s.endsWith("_revlinks")||s.endsWith("%revlinks")){
          it.remove();
          continue;
        }
        if(a.getValueProvider() == null ||
            !(a.getValueProvider() instanceof GeneratorValueProvider) &&(
            a.getValueProvider().getRawValue() == null ||
                a.getValueProvider().getRawValue().getValue() == null)) {
          UUID auid = a.getOrigin();
          if(!auid.equals(uid)) {
            if(requestRoot == null)
              synchronized(registeredRoots) {
                if(registeredRoots.containsKey(auid))
                  continue;//we were already started handlng, deps loop
                registeredRoots.put(uid, uid);
              }
            else
            {
              synchronized(registeredRoots) {
                if(registeredRoots.containsKey(auid))
                  continue;//we were already started handlng, deps loop
              }
            }
            Path basis = uuidMaps.getSecond().get(auid);
            Map<String,AttributeDefinition> map = loadDeclarativeAttributes(auid, basis, version);
            if(map!=null)
              map = reworkAttrs(requestRoot == null?uid:requestRoot, auid, map, uuidMaps, version, false);//TODO isRoot test?
            if(map!=null) {
              AttributeDefinition oad = map.get(s);
              if(oad!=null)
              {
              //TODO test on null?
                ad.put(s, oad);//TODO test
              }
            }
          }
        }
      }
      return ad;
    }
    @Override
    protected void handleFile(String path, Path f, String version, Pair<Map<Path, UUID>, Map<UUID, Path>> uuidMaps, boolean isRoot) throws IOException {
      if(path.equals("/root/Instances.json")){
        CoreUtils.delete(f.getParent().resolve("Instances").toFile(),true,false);
        CoreUtils.delete(f.getParent().resolve("Instances"+ResManager.RESOURCES_SUFFIX).toFile(),true,false);
        CoreUtils.delete(f.toFile(),true,false);//TODO add path to delete utils
      }else{
        super.handleFile(path, f, version, uuidMaps, isRoot);
      }
    }
    private void recDelete(File findMember) {
      if(findMember == null || !findMember.exists())
        return;
      if(findMember.isDirectory()){
        File[] members = findMember.listFiles();;
        for (File member : members)
         {
          recDelete(member);
         }
      }
      findMember.delete();
    };
  };
  @Override
  public String convert(IResTracker tracker, RevisionInfo ri,File proj, IProgressMonitor mon) throws Exception {
    StoredFormatVersioning sfv = new StoredFormatVersioning();
    String name = proj.getName();
    String pname = tracker.getProjectName(proj.toURI());
    TreeStateManager tsm = TreeStateManager.getOne(tracker);
    //TreeState old = tsm.getState(name, ri);
    tsm.setStateWithoutDB(name, pname, ri, TreeState.RESTART);//TODO change?
    List<String >vf = sfv.getFormatsVersions(proj);
    if(vf == null)
      return "Cannot load file with version-related info";
    String ver = StoredFormatVersioning.getVersionByGroupId(vf, "com.unitesk.nodegroup.requality");
    if(ver == null)
      ver = "0.15";
    java.nio.file.Path cache = CoreUtils.getCacheDir(proj.toURI());
    
    if(cache!=null && cache.toFile().exists())
       CoreUtils.delete(cache.toFile(), true, true);//remove outdated caches before conversion
    if(cache!=null && cache.toFile().exists())
      CoreUtils.delete(cache.toFile(), true, true);//remove outdated caches before conversion
    String s =  conv.convert(proj.toPath(), ver);
    if(cache!=null && cache.toFile().exists())
      CoreUtils.delete(cache.toFile(), true, true);//remove outdated caches before conversion
     
    TreeDB db = null;
//    try {
//      //TODO mb ri
//        db=tracker.trackTreeDB(proj.toURI(),null,true,true, tracker);
//      } catch (IOException e2){// TODO Auto-generated catch block
//        e2.printStackTrace();
//      }
//    if(db!=null)
//      db = tracker.reOpen(db, true);
    if(s==null || s.isEmpty())
    {
      boolean isLisDisabled = tracker.isDisabledListening(IResTracker.DISABLED_LIS_EVENTS);
      try {
        if(!isLisDisabled)
          tracker.disableListeners(IResTracker.DISABLED_LIS_EVENTS);
        tracker.open(proj.toURI());
        //TODO mb ri
        tracker.refresh(proj, 0x2);
        db = tracker.loadTreeDBDescription(proj.toURI(),ri,true,false,tracker, mon);//TODO look
        if(db!=null)
          db.cachedLocation = proj.toURI().toString();
      } catch (IOException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
      finally {
        if(!isLisDisabled)
          tracker.enableListeners(IResTracker.DISABLED_LIS_EVENTS);
      }
      //EclipseTracker.getTracker().trackProject(proj,true);
//      if(name!=null) {
//        db = tracker.getTreeDB(db, ri, name);
//      }
      CacheTransactionDescription b = null;
      CacheStorage cs = null;
      Map<String, List<NodeDesc>> newlocations = new ConcurrentHashMap<>();
      try{
        if(db!=null){
          db.startChangeRow();
          List<Document> docs = new LinkedList<>();
          cs = db.getCacheStorage();
          //TODO add project-related info
          b = cs.startCacheTransaction("convert old project",CacheStorage.COMMIT_ALL);
          db.getStorage().getCMM().getResourceMan().legacyMode = true;//TODO check it to take it offs?
          db.getStorage().initCaches(db.getStorage(), CacheInitializationStage.BEFORE_TRACK, mon);
          Lock wl = db.getStorage().writeLock(null);
          try{
            DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(db.getRootNode(),true,false,true,true,false);
            Iterator<TreeNode> it = dftw.iterator();
            while(it.hasNext())
            {
              TreeNode nde = it.next();
              Set<String> nkeys = new HashSet<>(nde.attrs().getLocalAttributeKeys());
              boolean achanges = false;
              AtomicBoolean changes = new AtomicBoolean(false);
              final TreeNode fnode = nde;
              CountDownLatch attrsLatch = new CountDownLatch(nkeys.size());
              nkeys.stream().parallel().forEach(s2->{
                try {
                  Attribute a = fnode.attrs().getAttribute(s2);
                  if(a!=null && (a.getType()==AttributeType.HTML||a.getType().equals(AttributeType.STRING))){
                    TypedValue tv = a.getDefinition().getRawValue();
                    if(tv!=null){
                      String val = tv.getGValue();
                      StringBuilder sb = null;
                      String aval = val;
                      boolean resCopied = false;

                      TempFolderFilesProvider tfp = TempFolderFilesProvider.EMPTY;
                      List<Path> toRemovePath = null;
                      if(val.indexOf(ResManager.RES_VAR)>-1){
                        sb = new StringBuilder();
                        int ind = val.indexOf(ResManager.RES_VAR);
                        int lastind = 0;
                        while(ind>-1){
                          int min = ind;
                          if(ind>0 && val.charAt(ind-1)=='$')
                            min = ind-1;
                          sb.append(val.substring(lastind, min));
                          int q = val.indexOf("'",ind+1),q1 = val.indexOf("\"",ind+1);
                          if(q1!=-1&&(q1<q || q==-1))
                            q = q1;
                          if(q>-1){
                            String fname = val.substring(ind+ResManager.RES_VAR.length()+1,q);
                            File resFile = fnode.getNodeResourcesFile();
                            Path resPath = resFile.toPath().resolve(fname);
                            Path replPath = null;
                            String newPath = fname;
                            if(!fname.startsWith(s2+"/"))
                              newPath = s2+"/"+fname;
                            if(Files.exists(resPath)) {
                              if (tfp == TempFolderFilesProvider.EMPTY && newPath!=null) {
                                tfp = TempFolderFilesProvider.getOne(a.getType(), null, false);
                                try(InputStream is = Files.newInputStream(resPath)){
                                  replPath = tfp.addFile(newPath, is, resPath.toFile());
                                  if(toRemovePath == null)
                                    toRemovePath = new LinkedList<>();
                                  toRemovePath.add(resPath);
                                } catch (IOException ioe) {
                                  RLogger.logError(ioe);
                                }
                              }
                            }
                            else RLogger.logInfo("Image was not found during version conversion "+fname+" on node "+nde.getSUUId());
                            if(replPath!=null)
                              sb.append(replPath.toAbsolutePath());
                            else {
                              sb.append(ResManager.RES_VAR);//TODO test
                              sb.append("/");
                              sb.append(fname);
                            }
                            lastind = q;
                          }
                          ind = val.indexOf(ResManager.RES_VAR,ind+1);
                        }
                        sb.append(val.substring(lastind));
                        val = sb.toString();
                      } 
                      
                      if(val.indexOf(ResManager.ATTR_RES_VAR)>-1){
                        sb = new StringBuilder();
                        int ind = val.indexOf(ResManager.ATTR_RES_VAR);
                        int lastind = 0;
                        while(ind>-1){
                          int min = ind;
                          if(ind>0 && val.charAt(ind-1)=='$')
                            min = ind-1;
                          sb.append(val.substring(lastind, min));
                          int q = val.indexOf("'",ind+1),q1 = val.indexOf("\"",ind+1);
                          if(q1!=-1&&(q1<q || q==-1))
                            q = q1;
                          int inc = (val.charAt(q-1) == '\\')?1:0;
                          String fname = CoreUtils.URLDecode(val.substring(ind+ResManager.ATTR_RES_VAR.length()+1,q-inc));
                          String newPath = fname;
                          if(q>-1){
                            try {
                              File resFile = fnode.getNodeResourcesFile();
                              File attrResFile = new File(fnode.getNodeResourcesPath(s2));
                              Path resPath = resFile.toPath().resolve(fname);
                              Path attrResPath = attrResFile.toPath().resolve(fname);
                                
                              if(!fname.startsWith(s2+"/"))
                                newPath = s2+"/"+fname;
                              Path targetPath = null;
                              if(Files.exists(resPath)) { //old format images
                                targetPath = resPath;
                              } else if(Files.exists(attrResPath)) {
                               // targetPath = attrResPath;//correct case
                              }else
                                RLogger.logInfo("Image was not found during version conversion "+fname+" on node "+nde.getSUUId());

                              Path replPath = null;
                              if (tfp == TempFolderFilesProvider.EMPTY && targetPath!=null) {
                                tfp = TempFolderFilesProvider.getOne(a.getType(), null, false);
                                try(InputStream is = Files.newInputStream(targetPath)){
                                  replPath = tfp.addFile(newPath, is, resPath.toFile());
                                  if(toRemovePath == null)
                                    toRemovePath = new LinkedList<>();
                                  toRemovePath.add(resPath);
                                }
                              }
                              if(replPath!=null)
                                sb.append(replPath.toAbsolutePath());
                              else {
                                sb.append(ResManager.ATTR_RES_VAR);
                                sb.append("/");
                                sb.append(val.substring(ind+ResManager.ATTR_RES_VAR.length()+1,q));//can be replaced by fname
                              }
                              //val = val.replace("${node.resURL}", "{node.resURL}");
                              lastind = q;
                            }catch(Exception e) {
                              RLogger.logError(e);
                            }
                          }
                          ind = val.indexOf(ResManager.ATTR_RES_VAR,ind+1);
                        }
                        sb.append(val.substring(lastind));
                      } 
                      if(toRemovePath!=null)
                        for(Path p:toRemovePath) {
                          if(Files.exists(p))
                            try {
                              resCopied = true;
                              Files.delete(p);
                            } catch (IOException e) {
                              RLogger.logInfo("File "+p+" cannot be removed during old image clearing");
                            }//remove old image
                        }
                      if(sb!=null) {
                        val = sb.toString();
                        if(!val.equals(aval) || resCopied){
                      //tends to be issues here on htmlvalueprovider
                          IValueProvider ovp = a.getDefinition().getValueProvider();
                          IValueProvider vp = AttributeDefinition.detectValueProvider(a.getKey(), ovp.getAttributeType(),
                            val, tfp);
                          fnode.attrs().setAttribute(new AttributeDefinition(a.getOrigin(),a.getKey(), a.getAvailability(),
                            vp,a.getDefinition().getFlags()));
                          changes.set(true);
                        }
                      }
                    }
                  }
                }finally {
                  attrsLatch.countDown();
                }
              });
              attrsLatch.await();//on interrapted think of it
              //TODO noderespath
              Attribute l = fnode.attrs().getAttribute(Requirement.ATTR_LOCATIONS);
              if(l!=null){
                Object o = l.getDefinition().getRawValue().getValue();
                if(!(o instanceof ListValue))
                {
                  RLogger.logError("Error while parsing locations for "+fnode.getSUUId()+". Waiting for listvalue but get a "+o);
                  continue;
                }
                ListValue lv = (ListValue) o;
                List<Object> nl = new ArrayList<>();
                if(lv!=null){
                  for(Object r:lv.getValues()){
                    TreeNode ref = fnode.getTreeDB().getNode(r.toString(), com.unitesk.requality.core.model.Path.QID|
                                                                           com.unitesk.requality.core.model.Path.UUID|
                                                                           com.unitesk.requality.core.model.Path.UV);
                    if(ref!=null)
                      nl.add(ref.getSUUId());
                    else{
                      nl.add(r.toString());
                      if(CoreUtils.DEBUG_LEVEL>0)
                        RLogger.logInfo("Location "+r.toString()+" was not found!");
                    }
                  }
                  fnode.attrs().setAttribute(new AttributeDefinition(l.getOrigin(), l.getKey(), 
                      new ReferenceValueProvider(AttributeType.LIST, new ListValue(AttributeType.REFERENCE, nl))));
                  achanges = true;
                }
              }
              if(fnode!=null){
                String oldResults = fnode.attrs().getStringAttributeValue("expectedResults", "");
                if (oldResults != null && !oldResults.isEmpty()){
                  fnode.attrs().removeAttribute("expectedResults");
                  achanges = true;
                  if(fnode.getType().equals(TestPurpose.TYPE_NAME)){
                    ((TestPurpose)fnode).setExpectedResults(fnode.attrs().getStringAttributeValue(TestPurpose.ATTR_EXPECTED_RESULTS, "") + oldResults);
                  }
                }
                String nodeType = fnode.getType(); 
                if(nodeType.equals(ReportSettings.TYPE_NAME) || fnode instanceof Requirement) {
                  String oldRepName = fnode.attrs().getStringAttributeValue("ReportHeader");
                  if(oldRepName!=null) {
                    fnode.attrs().removeAttribute("ReportTitle");
                    fnode.attrs().setConstAttribute("ReportTitle", oldRepName, 0);
                    achanges = true;
                  }
                  oldRepName = fnode.attrs().getStringAttributeValue("ProjectHeader");
                  if(oldRepName!=null) {
                    fnode.attrs().removeAttribute("ProjectHeader");
                    fnode.attrs().setConstAttribute("ReportSubTitle", oldRepName, 0);
                    achanges = true;
                  }
                }
                if(nodeType.equals(ReportData.TYPE_NAME)) {
                  String oldAttr = fnode.attrs().getStringAttributeValue("_last_generated");//fix strange old generated paths
                  if(oldAttr!=null) {
                    if(oldAttr.startsWith("/") || oldAttr.startsWith("\\"))
                    {
                      oldAttr = oldAttr.substring(1); 
                      fnode.attrs().removeAttribute("_last_generated");
                      fnode.attrs().setConstAttribute("_last_generated", oldAttr, 0);
                      achanges = true;
                    }
                  }
                }
              }
              if(fnode!=null && fnode.getType().equals(Document.TYPE_NAME))
                docs.add((Document) fnode);
              if(changes.get() || achanges)
                fnode.commitAttributes();
            }
            CountDownLatch latch = new CountDownLatch(docs.size());
            docs.stream().parallel().forEach(one->{
              if (mon.isCanceled()) {
               latch.countDown();
               return;
              }
              try {
                updateMarkup(one, newlocations);
              } catch(Exception e) {
                RLogger.logError(e);
              } finally {
                latch.countDown();
              }
            });
            latch.await();//on interrapted think of it
            for(Entry<String, List<NodeDesc>> en: newlocations.entrySet()) 
              for(NodeDesc nd:en.getValue())
                try {
                  nd.createNode(db,nd.getWorkParentUUID(), true, true);//TODO mb crossproject?
                }catch(RuntimeException notCreated) {
                  TreeNode par = db.getNode(nd.getWorkParentUUID());
                  TreeNode altNode = par.findWorkChild(nd.getUUId().toString());
                  RLogger.logDebug("Node was already created "+nd.getUUId()+" -> "+altNode.getUUId());
                }
          }
          finally{
            if(docs!=null && newlocations!=null)
              updateRelatedReqs(docs);
            db.stopChangeRow();
            newlocations.clear();
            if(cs!=null && b != null && b.error == null){
              cs.commit(b);
            }
//            if(old!=null) {
//              tsm.setState(name, ri, old);
//            }
            wl.unlock();
          }
        }
      }catch(Exception r){
        r.printStackTrace();
        s = CoreUtils.getTrace(r);
        if(cs!=null && b != null && b.error == null)
          cs.rollback(b);
      }
      if(db!=null && !db.isBaseDisposed()){
        db.setDisposed();
//      TreeStateManager.getOne().setDisposed(name, ri);
//        db.getStateLock().writeLock().lock();
//        try{
//          if(db.getState()!=TreeState.DISPOSED)
//            db.setState(TreeState.READY);
//        }finally{
//          db.getStateLock().writeLock().unlock();
//        }
      }
    }
    
    return s;
  }
  private void updateRelatedReqs(List<Document> docs) {
    if(docs.isEmpty())
      return;
    Document d = docs.iterator().next();
    DeepFirstTreeWalker reqfinder = new DeepFirstTreeWalker(d.getTreeDB().getNode(Requirement.getTypeRootQId()),false,false,true);
    Iterator<TreeNode> it = reqfinder.iterator();
    List<TreeNode> nodesToUp = null;
    while(it.hasNext()) {
      TreeNode node = it.next();
      if(node.attrs().getAttribute(Requirement.ATTR_LOCATIONS)!=null) {
        TreeDB db = node.getTreeDB();
        LinkResolver lr = db.getLinkResolver(); 
        lr.clearNodeCache(db, db.getCMM().getSettingsStore(), node);
        if(nodesToUp == null)
          nodesToUp = new LinkedList<>();
        nodesToUp.add(node);
      }
    }
    if(nodesToUp!=null)
      for(TreeNode node:nodesToUp) {
        TreeDB db = node.getTreeDB();
        LinkResolver lr = db.getLinkResolver(); 
        lr.initCache(node, false);
      }
  }
  private void updateMarkup(TreeNode node, Map<String, List<NodeDesc>> newlocations) {
    if(node == null)
      return;
    String type = node.getType();
    if(!type.equals(Document.TYPE_NAME))
      return;
    if(node.getWorkChildrenUUIDs().size()==0)
    {
      String name = ((Document)node).getResourceName();
      File cont = node.getResource(name);
      boolean hasLocations = false;
      String prevLine = "";
      if(cont!=null && cont.exists()) {
        try (BufferedReader br = Files.newBufferedReader(cont.toPath())){
          while(br.ready()) {
            String line = br.readLine();
            if((prevLine.trim()+line.trim()).contains("requality_text")) {
              hasLocations = true;
              break;
            }
            prevLine = line;
          }
        }catch(IOException e) {
          RLogger.logError(e);
        }
      }else {
        RLogger.logError("File "+name+" not found for node "+node.getQualifiedId());
      }
      if(!hasLocations)
        return;
    }
    MarkupExtractor.extract(node, newlocations);
  }
//  @Override
//  public boolean isAppliable(File proj) {
//    if(proj.getFile("root/Requirements.json").exists()){
//      List<String> vers = StoredFormatVersioning.getFormatsVersions(proj);
//      if(vers == null)
//        return true;
//      for(String s:vers){
//        if(s.startsWith(NODE_GROUP)){
//          return s.equals(NODE_GROUP + "|0.21.1")|| s.equals(NODE_GROUP + "|0.21")|| s.equals(NODE_GROUP + "|0.15");
//        }
//      }
//    }
//    return false;
//  }
  @Override
  public String versionFromMin() {
    return "*";
  }
  @Override
  public String versionFromMax() {
    return "0.21.3";//up to but not included
  }
  @Override
  public String versionTo() {
    return "0.21.3";
  }
  @Override
  public String getGroup() {
    return NODE_GROUP;
  }
}
