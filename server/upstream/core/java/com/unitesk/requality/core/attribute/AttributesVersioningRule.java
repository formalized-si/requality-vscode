package com.unitesk.requality.core.attribute;

import java.util.HashSet;
import java.util.Set;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;

public class AttributesVersioningRule {
  //attribute name and legaltypes are stay here for compatability with gui-related calls
  //most methods were moved to corresponding place
  public static final String VALUE_NONE = "NONE", VALUE_ERROR = "NOT_SUPPORTED", VALUE_LOAD_ERROR = "ERROR";
  public static final String ATTR_NODE_VERSION = "_nodeversion";

  private static String ATTR_REF_VERS_LIST="_refValueVersList";
  private static String ATTR_REF_VERS_ENALL="_refValueVersEnAll";
  
  private static Set<String> legaltypes = new HashSet<>();
  static{
    legaltypes.add(Requirement.TYPE_NAME);
    legaltypes.add(TextNode.TYPE_NAME);
  }
  public static boolean isLegal(String type) {
    return legaltypes.contains(type);
  }
  public static boolean isRefVersEnabled(TreeDB db){
	  TreeNode node = db.getRootNode();
	  return node.attrs().containsKey(ATTR_REF_VERS_ENALL)||node.attrs().containsKey(ATTR_REF_VERS_LIST);
  }
  public static boolean isAvailiable() {
    return false;
  }

}
