package com.unitesk.requality.core.attribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.TextNode;

public class AttributesKeywordsRule {
  private static final String PREFERENCE_ATTR_POLICY = "_com.unitesk.requality.ver.policy",
      PREFERENCE_EN_ALL = "_com.unitesk.requality.ver.enall",
      PREFERENCE_IS_ENABLED= "_com.unitesk.requality.ver.isen";
  public static final String VALUE_ILLEGAL = "NONE";
  private static Set<String> legaltypes = new HashSet<>();
  static{
    legaltypes.add(Comment.TYPE_NAME);
    legaltypes.add(Requirement.TYPE_NAME);
    legaltypes.add(TextNode.TYPE_NAME);
    legaltypes.add(TestPurpose.TYPE_NAME);
  }
  private static Map<String, Map<String, String>> loadMap(List<Object> listAttributeValue) {
    Map<String, Map<String, String>> amap = new HashMap<>();
    for(Object o : listAttributeValue){
      if(!(o instanceof ListValue))
        throw new RuntimeException("cannot load attribute versioning settings - incorrect value");
      ListValue lv = (ListValue)o;
      List<Object> lvo = lv.getValues();
      Object fv = lvo.get(0);
      if(fv == null)
        throw new RuntimeException("incorrect value in stored settings");
      String tp = fv.toString();
      amap.put(tp, new HashMap<>());
      for(int i = 1;i<(lvo.size()-1)/2+1;i+=2){
        amap.get(tp).put(lvo.get(i).toString(),lvo.get(i+1).toString());
      }
    }
    return amap;
  }
  private static ListValue saveMap(Map<String, Map<String, String>> mp) {
    List<Object> lvl1 = new LinkedList<>();
    for(Entry<String, Map<String, String>> en:mp.entrySet())
    {
      List<Object> lvl2 = new ArrayList<>();
      lvl2.add(en.getKey());
      Map<String, String>map = mp.get(en.getKey());
      for(String s:map.keySet()){
        lvl2.add(""+s);
        lvl2.add(map.get(s));
      }
      lvl1.add(new ListValue(AttributeType.STRING, lvl2));
    }
    return new ListValue(AttributeType.LIST, lvl1);
  }
  public static void save(TreeNode nodeToStore, AttributesKeywordsRule avr) {
    if(avr == null){
      nodeToStore.attrs().removeAttribute(PREFERENCE_IS_ENABLED);
      nodeToStore.attrs().removeAttribute(PREFERENCE_EN_ALL);
      nodeToStore.attrs().removeAttribute(PREFERENCE_ATTR_POLICY);
    }else{
      nodeToStore.attrs().setConstAttribute(PREFERENCE_IS_ENABLED, true, Flag.HIDDEN);
      nodeToStore.attrs().setConstAttribute(PREFERENCE_EN_ALL, false, Flag.HIDDEN);
    //  nodeToStore.attrs().setConstAttribute(PREFERENCE_ATTR_POLICY, saveMap(avr.getTypedNames()), Flag.HIDDEN);
    }
    nodeToStore.commitAttributes();
  }

  public static boolean isLegal(String type) {
    return legaltypes.contains(type);
  }
}
