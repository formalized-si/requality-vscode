package com.unitesk.requality.core.storage;

import java.util.UUID;

import com.unitesk.requality.core.model.TreeNode;

public class LateResolveRequest {
  public String localProjName;
  public String projName;
  public UUID uid;
  public String suid;
  public String key;
  public int hash = 0;

  public LateResolveRequest(TreeNode node, String key) {
    this.uid = node.getUUId();
    this.suid = node.getSUUId();
    this.localProjName = node.getTreeDB().getLocalName();
    this.projName = node.getTreeDB().getProjectName();
    if(this.projName == null)
    	System.out.println(5);
    this.key = key;
    this.hash = calcHash();
  }

  private int calcHash() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((suid == null) ? 0 : suid.hashCode());
    result = prime * result + ((localProjName == null) ? 0 : localProjName.hashCode());
    result = prime * result + ((projName == null) ? 0 : projName.hashCode());
    result = prime * result + ((key == null) ? 0 : key.hashCode());
    return result;
  }

  public LateResolveRequest(UUID uid, String localProjName, String projName, String key) {
    this.uid = uid;
    this.localProjName = localProjName;
    this.projName = projName;
    if(projName == null)
      System.err.println(3);
    this.suid = uid!=null?uid.toString():null;
    this.key = key;
    this.hash = calcHash();
  }

  @Override
  public boolean equals(Object obj) {
    try{
      LateResolveRequest lrr = (LateResolveRequest) obj;
      if(lrr.hash!=hash)
        return false;
      return (suid != null ? suid.equals(lrr.suid) : lrr.suid == null)
          && (key != null ? key.equals(lrr.key) : lrr.key == null);
    }catch(ClassCastException er){
      return false;
    }
  }
}
