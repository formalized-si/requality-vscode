package com.unitesk.requality.core.storage.cache;

import java.util.UUID;

public class RelationRecord {
  public String projectFrom = null, localProjectFrom = null, projectTo = null, localProjectTo = null;;
  public UUID nodeFrom;
  public UUID nodeTo;
  public String key;
  public String nodeToVersion;
  public RelationRecord(String localProjectFrom, String projectFrom, UUID nf, String localProjectTo, String projectTo, UUID nt, String name, String nodeToVersion){
    this.localProjectFrom = localProjectFrom;
    this.projectFrom = projectFrom;
    this.localProjectTo = localProjectTo;
    this.projectTo = projectTo;
    this.nodeFrom = nf;
    this.nodeTo = nt;
    this.key = name;
    this.nodeToVersion = nodeToVersion;
  }
  public RelationRecord(String projectFrom, UUID nf,  String projectTo, UUID nt, String name, String nodeToVersion){
	  //TODO REMOVE ASAP, TEST ONLY
	    this.localProjectFrom = localProjectFrom = projectFrom;
	    this.projectFrom = projectFrom;
	    this.localProjectTo = localProjectTo = projectTo;
	    this.projectTo = projectTo;
	    this.nodeFrom = nf;
	    this.nodeTo = nt;
	    this.key = name;
	    this.nodeToVersion = nodeToVersion;
	  }

  public RelationRecord(String localProj, String proj, UUID nf, UUID nt, String name, String nodeToVersion){
    this.localProjectFrom = this.localProjectTo = localProj;
    this.projectFrom = this.projectTo = proj;
    this.nodeFrom = nf;
    this.nodeTo = nt;
    this.key = name;
    this.nodeToVersion = nodeToVersion;
  }
  public RelationRecord(String proj, UUID nf, UUID nt, String name, String nodeToVersion){
	 //TODO REMOVE ASAP, TEST ONLY
	    this.localProjectFrom = this.localProjectTo = proj;
	    this.projectFrom = this.projectTo = proj;
	    this.nodeFrom = nf;
	    this.nodeTo = nt;
	    this.key = name;
	    this.nodeToVersion = nodeToVersion;
	  }
  private static String getProjectName(String localName, String projectName) {
    if(localName!=null && projectName!=null) {
      if(localName.equals(projectName))
    	return "["+localName+"]";
      return "["+localName+"->"+projectName+"]";
    }
    return "";
  }
  @Override
  public String toString() {
    return nodeFrom+getProjectName(localProjectFrom, projectFrom)+"-"+key+">"+nodeTo+getProjectName(localProjectTo, projectTo);
  }
  public boolean isCP() {
    return projectFrom != null && projectTo != null && !projectFrom.equals(projectTo)||localProjectFrom!=null && localProjectTo !=null && !localProjectFrom.equals(localProjectTo);//TODO mb init bug
  }
}
