package com.unitesk.requality.core.storage;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.model.NodeDescSet;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.ProgressRunnable;

public abstract class StorageDeleteOperation<F, Fld>{
  private static final Random r = new Random();
  private IStorageDeltaListener dt;
  public void delete(IStorageDeltaListener dt,  ResourceMap rm, NodeDescSet nd, IFileAble<F, Fld> ares, IContainerAble<Fld> childsCont, 
      IContainerAble<Fld> resourcesCont, IContainerAbleRefresher contUpdater,
      Map<UUID,Map<String,AttributeDefinition>> activeAttributes,
      Object project, byte opFlag) {
    this.dt = dt;
    UUID uuid = nd.root.getUUId();
    long sess = r.nextLong();
    
    ConcurrentLinkedDeque<DeleteNodeResources<F, Fld>> deleteQueue = new ConcurrentLinkedDeque<DeleteNodeResources<F, Fld>>();
    Queue<UUID> toh = new ConcurrentLinkedQueue<UUID>();
    Map<UUID, DeleteNodeResources<F, Fld>> delNodeDesc = new ConcurrentHashMap<UUID, DeleteNodeResources<F, Fld>>();
    
    Map<Path, IFileAble<F, Fld>> jsonFile = new ConcurrentHashMap<>();
    Map<Path, IContainerAble<Fld>> children = new ConcurrentHashMap<>();
    Map<Path, IContainerAble<Fld>> resources = new ConcurrentHashMap<>();
    Map<Path, UUID> fileUUID = new ConcurrentHashMap<>();
    Map<Path, Path> filePath = new ConcurrentHashMap<>();
    
    delNodeDesc.put(uuid, new DeleteNodeResources<F, Fld>(ares == null?null:ares.getParent(), uuid, ares, childsCont, resourcesCont, ares == null?null:ares.toRelativePath()));
    toh.add(uuid);
    while(!toh.isEmpty()){
      UUID now = toh.poll();
      DeleteNodeResources<F, Fld> desc = delNodeDesc.get(now);
      IContainerAble<Fld> childrenCont = null;
      
      if(desc!=null) {
        deleteQueue.addFirst(desc);
        childrenCont = desc.children;
      }
      jsonFile.clear();
      children.clear();
      resources.clear();
      
      if(childrenCont != null){
        IResourceAble[] rs = childrenCont.members();
        Arrays.stream(rs).forEach(one->{
            if(one instanceof IContainerAble){
              IContainerAble<Fld> cont = (IContainerAble<Fld>) one;
              String cname = cont.getName();
              if(cname.toLowerCase().endsWith(ResManager.RESOURCES_SUFFIX)){
                  resources.put(Path.of(cname.substring(0,cname.length()-ResManager.RESOURCES_SUFFIX.length())), cont);
                  contUpdater.refreshRecoursive(cont);
              }else
                  children.put(Path.of(cname), cont);
            }else{
                IFileAble<F, Fld> f = (IFileAble<F, Fld>) one;
                String fname = f.getName();
                if(fname.toLowerCase().endsWith(".json"))
                    jsonFile.put(Path.of(fname.substring(0,fname.length()-5)), f);
            }
        });
      }
      rm.readLock();
      try{
        jsonFile.entrySet().stream().forEach(en->{
          IFileAble<F, Fld> file = en.getValue();
            Path path = file.toRelativePath();
            try {
              UUID uid = rm.cacheGet(path, LockType.NO_LOCK);
              if(uid!=null) 
                fileUUID.put(en.getKey(), uid);
              else
                rm.cacheGet(path, LockType.NO_LOCK);
              filePath.put(en.getKey(), path);
            } catch (LockIsBusy e) {
            }
        });
      }finally{
        rm.readUnlock();
      }
      final IContainerAble<Fld> fchildrenCont = childrenCont;
      jsonFile.entrySet().stream().map(one->{
        Path name = one.getKey();
        UUID node = fileUUID.get(name);
        Path path = filePath.get(name);
        if(node!=null) {
          return new DeleteNodeResources<F, Fld>(fchildrenCont, node, jsonFile.get(name), children.get(name), resources.get(name), path); 
        }
        return null;
      }).forEach(one->{if(one!=null) {delNodeDesc.put(one.uid, one);toh.add(one.uid);}});
      //remove from cache
      for(DeleteNodeResources<F, Fld> dnr: deleteQueue)
        activeAttributes.remove(dnr.uid);
      rm.writeLock();
      try{
        for(DeleteNodeResources<F, Fld> dns:deleteQueue)
          rm.cacheRemove(dns.filePath,sess);
      }finally{
        rm.writeUnlock();
      }
//      for(Object o:arr)
//        if(o!=null) {
//          DeleteNodeResources dnrr = (DeleteNodeResources) o;
//          toh.add(dnrr.uid);
//          deleteQueue.add(dnrr);
//        }
      
    }
    Object rule = formSchelduleRule(deleteQueue);
    ProgressRunnable pr = new ProgressRunnable() {
      Map<Fld, IContainerAble<Fld>> parentsToCheck = new HashMap<>(); 
      @Override
      public void run(IProgressMonitor monitor) {
        boolean wl = false; 
        deleteQueue.stream().forEach(one->{
          //remove resources and json file, can be made in parallel
          if(one.json!=null) {
            IContainerAble<Fld> par = one.json.getParent();
            if(par!=null)
              addParent(par);
            one.json.delete(true, monitor);
          }
          IContainerAble< Fld> resources = one.resources;
          //but beware of server-related resources load issues
          if(resources != null) {
            LinkedList<IResourceAble> handled = new LinkedList<>(), toh = new LinkedList<>();
            toh.add(resources);
            while(!toh.isEmpty()) {
              IResourceAble rs = toh.remove(0);
              handled.addFirst(rs);
              rs.waitAccess();
              if(rs instanceof IContainerAble)
                for(IResourceAble r: ((IContainerAble<Fld>)rs).members()){
                  if(!handled.contains(r))
                    toh.add(r);
                }
            }
            ListIterator<IResourceAble> li = handled.listIterator(handled.size());
            while(li.hasPrevious()) {
              IResourceAble rd = li.previous();
              rd.delete(true, monitor);//false causes some issues on long operations
            }
          }

        });
        //then remove all exceed folders, needs to be done in sequence
        for(DeleteNodeResources<F, Fld> dns:deleteQueue)
          if(dns.children!=null)
            dns.children.delete(false, monitor);
        if (dt != null) 
          dt.deleted(nd, opFlag);
//        while(!parentsToCheck.isEmpty()) {
          for(Entry<Fld, IContainerAble<Fld>> en: new LinkedList<>(parentsToCheck.entrySet())) {//remove empty parent container
            parentsToCheck.remove(en.getKey());
            IContainerAble<Fld> container = en.getValue();
            if(container.members().length == 0) {
              container.delete(true, monitor);//rec should be not needed
//              addParent(container.getParent());
            }
          }
//        }
      }
      private void addParent(IContainerAble<Fld> par) {
        if(par == null)
          return;
        Fld file = par.getRaw();
        if(!parentsToCheck.containsKey(file)) {
          parentsToCheck.put(file, par);
//          recAddParent(par.getParent());
        }
      }
    };
    if (dt != null) 
      dt.beforeDeleted(nd, opFlag);
    startDeleteJob(rule, pr);
    
  }
  public abstract void startProjectScopedJob(String string, ProgressRunnable progressRunnable, Object project);
  public abstract Object formSchelduleRule(ConcurrentLinkedDeque<DeleteNodeResources<F, Fld>> deleteQueue); 
  public abstract void startDeleteJob(Object rule, ProgressRunnable pr);
}
