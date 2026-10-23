package com.unitesk.requality.tool;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.ResManager;

public class LocationHelper {
  public static URI getLocation(TreeNode node){
    try {
      String qid = node.getQualifiedId();
      if(qid.equals("/"))
        qid = "";
      return new URI(ResManager.getManager().getLocation(node.getTreeDB())+"/root"+CoreUtils.URLEncode(qid)+".json");
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }
  public static URI getLocation(TreeNode node, String id){
    try {
      return new URI(ResManager.getManager().getLocation(node.getTreeDB())+getTreeRelatedLocation(node, id));
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }
  public static String getStringLocation(TreeDB db){
    URI uri = getLocation(db, false);
    if(uri == null)
      return null;
    File f = new File(uri);
    if(f!=null&&f.exists())
      return f.getAbsolutePath();
    return null;
  }
  public static URI getLocation(TreeDB db, boolean noCache){
    if(db == null)
      return null;
    String spath = db.getTracker().getLocation(db, noCache);
    if(spath == null)
      return null;
    db.cachedLocation = spath;
    return URI.create(spath);
  }
  public static String getTreeRelatedLocation(TreeNode par, String id) {
    StringBuilder sb = new StringBuilder();
    sb.append("/root");
    sb.append(par.getQualifiedId());
    sb.append("/");
    sb.append(id);
    sb.append(".json");
    return CoreUtils.URLEncode(sb.toString());
  }
  public static String getTreeRelatedLocation(TreeNode node) {
    StringBuilder sb = new StringBuilder();
    sb.append("/root");
    sb.append(node.getQualifiedId());
    sb.append(".json");
    return CoreUtils.URLEncode(sb.toString());
  }
  public static String getRelativePath(URI uri, URI uri2) {
    String s1 = uri.toString(),s2 = uri2.toString();
    FileSystem fs = null;
    try{
      fs = new File(uri).toPath().getFileSystem();
      String rp = CoreUtils.getRelativePath(fs,s1,s2);
      return clearURIChars(rp);
    }finally {
       if(fs!=null) {
         try {fs.close();}catch(Exception e) {}
       }
	}
  }
  public static String clearURIChars(String s){
    return CoreUtils.clearURIChars(s);
  }
}

