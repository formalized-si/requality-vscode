package com.unitesk.requality.core.operation.mass;

import java.util.HashMap;
import java.util.Map;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;

public abstract class AbstractNodeOperation {
  String defcomment = null;
  private boolean ignoreTrans = false;
  private Map<String, String> settings = new HashMap<>();

  public AbstractNodeOperation(String comment) {
    defcomment = comment;
  }

  public abstract void applySettings(Map<String, String> args);

  public boolean execute(TreeNode root) {
    return execute(root, defcomment);
  }

  public boolean execute(TreeNode root, String comment) {
    boolean cont = ignoreTrans;
    boolean started = !cont && root.getTreeDB()
        .startTransaction(
            getName() + " on '" + root.getUserVisibleName(true) + "'" + (comment != null
                ? " " + comment
                : ""));
    cont = cont || started;
    if (cont) {
      try {
        performAction(root);
        if (started) {
          root.getTreeDB().commit();
          started = false;
        }
      } catch (Exception e) {
        RLogger.logError(e);
        return false;
      }finally{
        if(started)
          root.getTreeDB().rollback();
      }
    } else return false;
    return true;
  }

  public abstract String getName();

  public abstract void performAction(TreeNode root);

  public void setIgnoreTransaction(boolean b) {
    ignoreTrans = b;
  }
}
