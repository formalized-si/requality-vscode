package com.unitesk.requality.core.attribute.reference;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.model.TreeDB;

public class ReferenceRenames {

  private static final String PREFERENCE_NAMES = "_com.unitesk.requality.reference_names";
  private static final String PREFERENCE_REPLS = "_com.unitesk.requality.reference_replaces";


  public static Map<String, String> getNamesAcronyms(TreeDB db) {
    Map<String, String> mp = new HashMap<>();
    UUID root = db.getRootNodeUUID();
    if (root != null) {
      Attribute a = db.getRootNode().attrs().getAttribute(PREFERENCE_REPLS);
      if (a != null) {
        //AttributeDefinition a = at.get(PREFERENCE_REPLS);
        ListValue lv = a.getGValue();
        if(lv!=null){
          java.util.List<Object> l = lv.getValues();
          for (int i = 0; i < (l.size() / 2); i++) {
            mp.put(l.get(i * 2).toString(), l.get(i * 2 + 1).toString());
          }
        }
      }
    }
    for (String s : globalRenames.keySet()) if (!mp.containsKey(s)) mp.put(s, globalRenames.get(s));
    //      IPreferenceStore pref = Activator.getDefault().getPreferenceStore();
    //      if( pref.contains(PREFERENCE_REPLS)){
    //          String[]saved = pref.getString(PREFERENCE_REPLS).split("\\|\\|");
    //          for(int i=0;i<saved.length/2;i++)
    //              mp.put(saved[i*2], saved[i*2+1]);
    //      }
    return mp;
  }
  //actions when new selection is performed

  public static void setGlobalRenames(Map<String, String> referenceAcronyms) {
    globalRenames = referenceAcronyms;
  }
  public static Map<String, String> globalRenames = new HashMap<>();
}
