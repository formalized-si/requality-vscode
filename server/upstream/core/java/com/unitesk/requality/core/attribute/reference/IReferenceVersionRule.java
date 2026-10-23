package com.unitesk.requality.core.attribute.reference;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributesVersioningRule;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;

public interface IReferenceVersionRule {

  boolean checkTo(TreeNode from, TreeNode to, String name);

  boolean checkFrom(TreeNode node, String name);

  boolean isEnabled();

  void setSettings(Collection<String> names, boolean isEnAll, boolean isEn);

  boolean isEnAll();

  Set<String> getNames();

  void initNodeVersion(ReferenceValue rv, String key, AttributeStorage where, Map<UUID, String> storedNodeVers, TreeNode node);

  void checkNodeVersion(ReferenceValue rv, String key, AttributeStorage where, Map<UUID, String> storedNodeVers, TreeNode node, StringBuilder sb);

  public static String getVersionString(String old, String versS, String prefix) {
    if(versS==null)
      return "";
    if(versS.equals(AttributesVersioningRule.VALUE_NONE))
      versS = "N";
    if(old == null || old.equals(AttributesVersioningRule.VALUE_NONE))
      old = "N";
    return prefix+"[v"+old+(!old.equals(versS)&&!old.equals(versS+"*")?"←"+versS:"")+"]";
  }

  Pair<ChangesSet<Attribute>, ChangesSet<Attribute>> filterRefChanges(ChangesSet<Attribute> cs);

}
