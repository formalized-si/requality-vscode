package com.unitesk.requality.core.attribute.valueprovider;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.TermsManipulator;

public class TermsValueProvider implements IValueProvider {
  public static String TYPE = "TermsProvider";
  private boolean direct;
  public TermsValueProvider(boolean b) {
    direct = b;
  }

  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    List<Object> l1 = new LinkedList<>((direct?
        (TermsManipulator.getDefToUseTermsNames(as.getNode())):
        	TermsManipulator.getUseToDefTermsNames(as.getNode())));
    return new TypedValue(AttributeType.LIST, new ListValue(AttributeType.STRING,l1));
  }
  private static Set<String>depd, depr;
  static{
    depd = new HashSet<>();
    depd.add(TermsManipulator.USE_TERM_ATTR_NAME);
    depd.add(TermsManipulator.USE_TERM_ALT_ATTR_NAME);
    depr = new HashSet<>();
    depr.add(TermsManipulator.DEF_TERM_ATTR_NAME);
  }
  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    return direct?depd:depr;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public TypedValue getRawValue() {
    return null;
  }

  @Override
  public Map<String, Object> getRawStores() {
    return null;
  }

  private static TermsValueProvider d = new TermsValueProvider(true),
                                    r = new TermsValueProvider(false);
  public static IValueProvider getOne(boolean direct) {
    return direct?d:r;
  }

  @Override
  public void attach(TreeNode node) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void detach(TreeNode node) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void saveJSON(JSONObject vpo) throws JSONException {
    throw new UnsupportedOperationException();
  }

  @Override
  public AttributeType getAttributeType() {
    return AttributeType.LIST;
  }
}
