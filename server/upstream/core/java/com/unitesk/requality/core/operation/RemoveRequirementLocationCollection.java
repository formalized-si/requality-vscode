package com.unitesk.requality.core.operation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.model.TreeNode;

public class RemoveRequirementLocationCollection {
  private Set<UUID> locationsToRemoveUUIDs;
  private Set<UUID> locationsToUpdateUUIDs;
  private List<TreeNode> locationsToRemove;
  private Map<UUID, List<UUID>> remList;
  private List<TreeNode> reqs;
  public RemoveRequirementLocationCollection() {
    locationsToUpdateUUIDs = new HashSet<>();
    locationsToRemove = new ArrayList<TreeNode>();
    locationsToRemoveUUIDs = new HashSet<UUID>();
    remList = new HashMap<UUID, List<UUID>>();
    reqs = new LinkedList<>();
  }
  public synchronized Set<UUID> getLocationsToUpdate(){
    return locationsToUpdateUUIDs;
  }
  public synchronized Set<UUID> getLocationsToRemoveUUIDs(){
    return locationsToRemoveUUIDs;
  }
  public synchronized List<TreeNode> getLocationsToRemove(){
    return locationsToRemove;
  }
  public synchronized List<TreeNode> getRequiremnetsToUpdate(){
    return reqs;
  }
  public synchronized List<UUID> getRemovedLocs(UUID req){
    return remList.getOrDefault(req, Collections.emptyList());
  }
  public synchronized void registerToRemove(TreeNode l) {
    UUID uid = l.getUUId();
    if(locationsToRemoveUUIDs.add(uid)) {
      locationsToUpdateUUIDs.remove(uid);
      locationsToRemove.add(l);
    }
  }
  public synchronized void registerToUpdate(TreeNode l) {
    UUID uid = l.getUUId();
    if(!locationsToRemoveUUIDs.contains(uid)) 
      locationsToUpdateUUIDs.add(uid);
  }
  public synchronized void registerRemoveLocation(TreeNode req, UUID loc) {
    UUID uid = req.getUUId();
    List<UUID> lrem = remList.get(uid);
    if(lrem == null) {
      lrem = new ArrayList<UUID>();
      remList.put(uid, lrem);
      reqs.add(req);
    }
    locationsToRemoveUUIDs.add(loc);
    lrem.add(loc);
  }
}
