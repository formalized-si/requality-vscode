package com.unitesk.requality.core.storage.changelog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.core.model.Pair;

public class ChangeLogTransformation {
  public ChangeLogSource source = null;
  public Boolean useVerionsFromGit = false;
  public Map<String, Pair<String, String>> authorNameNormalization = null;
  public Map<String, Pair<String, String>> authorEmailNormalization = null;
  public Set<String> ignoreHash = null;
  public Map<String, String> commentReplace = null;
  public List<ChangeLogRecord> transform(List<ChangeLogRecord> recs){
    List<ChangeLogRecord> ret = new LinkedList<>();
    for(ChangeLogRecord r: recs) {
      boolean br = false;
      if(ignoreHash!=null)
        for(String s:ignoreHash)
          if(!s.isEmpty() && r.commit!=null && r.commit.startsWith(s)) {
            br = true;
            break;
          }
      if(br)
        break;
      ChangeLogRecord rclone = r.clone();
      String crepl = null;
      if(commentReplace!=null) 
        crepl = commentReplace.get(r.commit);
      if(crepl!=null)
        rclone.message = crepl;
      Pair<String, String> authRepl = 
    		  authorEmailNormalization==null ? null :
    		  authorEmailNormalization.get(r.authorEmail);
      if(authRepl == null && authorNameNormalization != null)
        authRepl = authorNameNormalization.get(r.authorName);
      if(authRepl!=null) {
        rclone.authorName = authRepl.getFirst();
        rclone.authorEmail = authRepl.getSecond();
      }
      ret.add(rclone);
    }
	return ret;
  }
  public static ChangeLogTransformation merge(ChangeLogTransformation t1, ChangeLogTransformation t2) {
    if(t1 == null)
      return t2;
    if(t2 == null)
      return t1;
    ChangeLogTransformation ret = new ChangeLogTransformation();
    ret.authorEmailNormalization = t1.authorEmailNormalization == null ? t2.authorEmailNormalization :
    	t2.authorEmailNormalization == null ? 
    			t1.authorEmailNormalization :
    			getMergedMap(t1.authorEmailNormalization, t2.authorEmailNormalization);
    ret.authorNameNormalization = t1.authorNameNormalization == null ? t2.authorNameNormalization :
    	t2.authorNameNormalization == null ? 
    			t1.authorNameNormalization : 
    			getMergedMap(t1.authorNameNormalization, t2.authorNameNormalization);
    ret.commentReplace = t1.commentReplace == null ? t2.commentReplace :
    	t2.commentReplace == null ? 
    			t1.commentReplace :
    			getMergedMap(t1.commentReplace, t2.commentReplace);
    ret.ignoreHash = t1.ignoreHash == null ? t2.ignoreHash :
    	t2.ignoreHash == null ? 
    			t1.ignoreHash :
    			getMergedSet(t1.ignoreHash, t2.ignoreHash);
    ret.useVerionsFromGit = t2.useVerionsFromGit!=null?t2.useVerionsFromGit:t1.useVerionsFromGit;
    ret.source = t2.source!=null?t2.source:t1.source;
	return ret;
  }
  private static <T> Map<String, T> getMergedMap(Map<String, T> m1,
      Map<String, T> m2) {
    Map<String, T> ret = new HashMap<>(m1);
    ret.putAll(m2);
	return ret;
  }
  private static <T> Set<T> getMergedSet(Set<T> m1,
      Set<T> m2) {
    Set<T> ret = new HashSet<>(m1);
    ret.addAll(m2);
    return ret;
  }
}
