package com.unitesk.requality.core.attribute.valueprovider;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.api.IValueProviderProvider;
import com.unitesk.requality.core.attribute.AttributesVersioningRule;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.ReportSettings;
public class ValueProviderFactory {
  private static ValueProviderFactory fact = new ValueProviderFactory();
  
  private static Map<String, IValueProvider> staticVirtual = new HashMap<>();
  private static Map<String, IValueProvider> staticProviders = new HashMap<>();
  private static Map<String, IValueProviderProvider> dynamicProviders = new HashMap<>();

  public static Set<String> getVirtualAttrNames() {
    return virtualProvidersNames;
  }
  private static TreeSet<String> virtualProvidersNames = new TreeSet<>();
  
  static {
    addStaticValueProvider(TreeNode.ATTR_NAMEORID, NameOrIdProvider.getOne(), true);
    addStaticValueProvider(TreeNode.ATTR_LABEL, LabelProvider.getOne(), true);
    addStaticValueProvider(TreeNode.ATTR_USERVISIBLEID, UserVisibleIdProvider.getOne(), true);
    addStaticValueProvider(UUIDProvider.ATTR_UUID, UUIDProvider.getOne(), true);
    addStaticValueProvider(TreeNode.ATTR_TERMS, TermsValueProvider.getOne(true), true);
    addStaticValueProvider(TreeNode.ATTR_TERMS_REV, TermsValueProvider.getOne(false), true);
    addStaticValueProvider(TreeNode.ATTR_CHILDREN, WorkChildrenProvider.getOne(), true);
    addStaticValueProvider(TreeNode.ATTR_CHILDREN_UUIDS, WorkChildrenUUIDsProvider.getOne(), true);
    addStaticValueProvider(TreeNode.ATTR_SHOWN_CHILDREN, ShownChildrenProvider.getOne(), true);
    addStaticValueProvider(TreeNode.ATTR_PARENT, ParentProvider.getOne(), true);
    addStaticValueProvider(TreeNode.ATTR_LOCATIONS_DESCRIPTION, LocationsDescriptionProvider.getOne(), true);
    addStaticValueProvider(AttributesVersioningRule.ATTR_NODE_VERSION, VersionValueProvider.getOne(), false);
    
    addStaticValueProvider(ChildIndexProvider.ATTR_KEY, ChildIndexProvider.getOne(), true);
    addStaticValueProvider(NumericIdProvider.ATTR_KEY, NumericIdProvider.getOne(), false);
    addStaticValueProvider(ChildPositionsProvider.ATTR_KEY, ChildPositionsProvider.getOne(), true);
    
    addStaticValueProvider(ChildStructuredPositionsProvider.ATTR_KEY, ChildStructuredPositionsProvider.getOne(), true);
    addStaticValueProvider(StructuredIndexProvider.ATTR_KEY, StructuredIndexProvider.getOne(), true);
    
    addDynamicValueProvider(TreeNode.ATTR_REPO_STATE, new IValueProviderProvider(){
      @Override
      public IGenericValueProvider getProvider(TreeDB db, UUID uid, String key) {
        return RepoLocalProvider.getOne(uid);
      }
      
    }, true);
    
    addStaticValueProvider(ReportSettings.ATTR_GEN, null, true);//formal placeholder
    
    
    Map<String, Pair<IValueProvider, Boolean>> vp = API.getStaticValueProviders();
    for(Entry<String, Pair<IValueProvider, Boolean>> en:vp.entrySet())
      addStaticValueProvider(en.getKey(), en.getValue().getFirst(), en.getValue().getSecond());
    Map<String, Pair<IValueProviderProvider, Boolean>> vpp = API.getDynamicValueProviders();
    for(Entry<String, Pair<IValueProviderProvider, Boolean>> en:vpp.entrySet())
      addDynamicValueProvider(en.getKey(), en.getValue().getFirst(), en.getValue().getSecond());
  }
  
  public static void disableVirtualProviders(){
    for(Entry<String, IValueProvider> en: staticVirtual.entrySet()) {
      removeStaticValueProvider(en.getKey(), en.getValue());
    }
  }

  public static void enableVirtualProviders(){
    for(Entry<String, IValueProvider> en: staticVirtual.entrySet()) {
      addStaticValueProvider(en.getKey(), en.getValue(), true);
    }
  }
  
  public static Set<String> getVirtAttrsNames() {
    return virtualProvidersNames;
  }
  public static Set<String> getVirtAttrsNames(TreeNode node) {
    return getVirtAttrsNames();
  }  //private static AttributeDefinition idD, pathD;

  
  public static IGenericValueProvider getVirtualProvider(TreeDB db, String s, UUID uid) {
    return ValueProviderFactory.getOne().getProvider(db, s, uid);
  }
  synchronized IValueProvider getStaticValueProvider(String name)
  {
    return staticProviders.get(name); 
  }
  synchronized static void addStaticValueProvider(String name, IValueProvider p, boolean virt){
    staticProviders.put(name, p);
    if(virt){
      virtualProvidersNames.add(name);
      staticVirtual.put(name, p);
    }
    
  }
  synchronized static void removeStaticValueProvider(String name, IValueProvider p){
    IValueProvider s = staticProviders.get(name);
    if(s == p){
      staticProviders.remove(name);
      if(virtualProvidersNames.contains(name))
        virtualProvidersNames.remove(name);
    }
    s = staticVirtual.get(name);
    if(s == p)
      staticVirtual.remove(name);
  }

  private static void addDynamicValueProvider(
      String name, IValueProviderProvider p, boolean virt) {
    dynamicProviders.put(name, p);
    if(virt)
      virtualProvidersNames.add(name);
  }
  public static ValueProviderFactory getOne() {
    return fact;
  }
  public IGenericValueProvider getProvider(TreeDB db, String id, UUID node){
    IGenericValueProvider vp = staticProviders.get(id);
    if(vp == null)
    {
      IValueProviderProvider vpp = dynamicProviders.get(id);
      if(vpp!=null){
        vp = vpp.getProvider(db, node, id);
      }
    }
    return vp;
  }
  public static boolean isVirtName(TreeNode object, String key) {
    return virtualProvidersNames.contains(key);
  }
  
}
