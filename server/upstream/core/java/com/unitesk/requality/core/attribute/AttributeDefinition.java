package com.unitesk.requality.core.attribute;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.GeneratorValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.HtmlValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.IRelatedFilesProvider;
import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TemplValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.ValueProviderFactory;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;

public class AttributeDefinition {
//  //raw value is the value of attribute before all replaces or value in
//  //Requality specific object
//  private Object rawvalue;
//  //value with replaces or value in "java" format (Lists for LIST type and
//  //so on)
//  private Object newvalue;
  private final IValueProvider valueProvider;
  private final UUID origin;
  private final String key;
  private final AttributeAvailability availability;
  private final byte flags;
  public AttributeDefinition(UUID origin, String key, IValueProvider vp, byte flag){
    this(origin,key,AttributeAvailability.LOCAL,vp,flag);
  }
  public AttributeDefinition(UUID origin, String key, IValueProvider vp, int flag){
    this(origin,key,AttributeAvailability.LOCAL,vp,(byte)flag);
  }
  public AttributeDefinition(UUID origin, String key, IValueProvider vp){
    this(origin,key,vp,detectFlags(key));
  }
  public static byte detectFlags(String key){
    byte res = 0;
    if(key.startsWith("_"))
      res |= Flag.HIDDEN;
    if(ValueProviderFactory.isVirtName(null,key)){
      res |= Flag.VIRTUAL;
      res |= Flag.HIDDEN;
    }
    return res;
  }
  public static byte detectFlags(byte flags2, String key){
    if(key.startsWith("_"))
      flags2 |= Flag.HIDDEN;
    if(ValueProviderFactory.isVirtName(null,key)){
      flags2 |= Flag.VIRTUAL;
      flags2 |= Flag.HIDDEN;
    }
    return flags2;
  }
  public AttributeDefinition(UUID origin, String key, AttributeAvailability av, IValueProvider vp, byte flags){
    this.origin = origin;
    this.availability = av;
    this.valueProvider = vp;
    this.flags = detectFlags(flags, key);
    this.key = key;
  }

  public AttributeDefinition(UUID origin, String key, AttributeAvailability av, IValueProvider vp, int flags){
    this(origin,key,av, vp, (byte)flags);
  }
  public AttributeDefinition(UUID origin, String key, AttributeType type, Object value) {
    this(origin, key, new ConstValueProvider(type, value));
  }
  
  boolean isMoreAVVisibleThan(AttributeDefinition rag2) {
    if (rag2 == null) return true;
    AttributeAvailability pa1 = this.getAvailability(), pa2 = rag2.getAvailability();
    if (pa1.equals(AttributeAvailability.LOCAL)) return !pa2.equals(AttributeAvailability.LOCAL);
    else if (pa1.equals(AttributeAvailability.DIRECT_CHILDREN))
      return pa2.equals(AttributeAvailability.SUBTREE);
    else return false;
  }

  boolean isSameAVVisibleWith(AttributeDefinition rag2) {
    if (rag2 == null) return true;
    AttributeAvailability pa1 = this.getAvailability(), pa2 = rag2.getAvailability();
    return pa1.equals(pa2);
  }

  public boolean isMoreVisible(TreeDB db, AttributeDefinition a, UUID where, boolean samemore) {
    if (a == null) return true;
    if (where.equals(getOrigin())) {
      if (!where.equals(a.getOrigin())) return true;
    } else {
      if (where.equals(a.getOrigin())) return false;
    }
    if (isGenerated()) {
      if (a.isGenerated()) {
      } else return false;
    } else if (a.isGenerated()) {
      return true;
    }
    return this.isMoreAVVisibleThan(a)
        || (this.isSameAVVisibleWith(a) && (getOrigin().equals(a.getOrigin()) || isNotUpperOnTree(db, getOrigin(), a.getOrigin()))); // )
  }
  private boolean isNotUpperOnTree(TreeDB db, UUID origin2, UUID origin3) {
    List<UUID> plist1= new LinkedList<>(), plist2=new LinkedList<>();
    UUID par = origin2;
    while (par!=null){
      plist1.add(par);
      par = db.getStorage().getParent(par);
    }
    par = origin3;
    while (par!=null){
      plist2.add(par);
      par = db.getStorage().getParent(par);
    }
    return plist1.size()>=plist2.size();//TODO also check subtrees?
  }
  public Set<String> getDependencies(AttributeStorage as) {
    if(valueProvider == null)
      return IValueProvider.NULLDEP;
    return valueProvider.getDependencies(as);
  }
  public AttributeAvailability getAvailability() {
    return availability;
  }
  public String getKey() {
    return key;
  }
  public TypedValue getRawValue() {
    if(valueProvider == null)
      return null;
    return valueProvider.getRawValue();
  }
  TypedValue getTValue(AttributeStorage as) {
    if(valueProvider == null)
      return null;
    return valueProvider.getTypedValue(getKey(), as);
  }
  public UUID getOrigin() {
    return origin;
  }
  public boolean isGenerated() {
    if(valueProvider == null)
      return false;
    // TODO Auto-generated method stub
    return valueProvider.getType().equals(GeneratorValueProvider.TYPE);
  }
  public AttributeType getType() {
    return valueProvider.getAttributeType();
  }
  public boolean isVirtual() {
    return (flags&Flag.VIRTUAL)==Flag.VIRTUAL;
  }
//  @Override
//  protected AttributeDefinition clone(){
//    return new AttributeDefinition(origin, key, type, availability, valueProvider.clone(), flags);
//  }
  public IValueProvider getValueProvider() {
    return valueProvider;
  }
  public Object getValue(AttributeStorage as) {
    TypedValue tv = getTValue(as);
    if(tv == null)
      return null;
    return tv.getValue();
  }
  public boolean weakEquals(Object obj) {
    if(obj == null)
      return false;
    if (super.equals(obj)) return true;
    if (!(obj instanceof Attribute)) {
      return false;
    }
    AttributeDefinition attr = (AttributeDefinition) obj;
    if (!key.equals(attr.getKey())) {
      return false;
    }
//    if (type != null && attr.getType() != null && !type.equals(attr.getType())) // TODO
//      // rec
//      return false;
    if (!availability.equals(attr.getAvailability())) return false;
    if (!getValueProvider().getRawValue().equals(attr.getValueProvider().getRawValue()))
      return false;
    return true;
  }
  public AttributeDefinition copy(UUID uuId) {
    if(uuId!=null && !uuId.equals(origin))
      return new AttributeDefinition(uuId, key, availability, valueProvider, flags);
    return this;
  }
  public Attribute toAttribute(TreeNode node) {
    return toAttribute(node.attrs());
  }
  public Attribute toAttribute(AttributeStorage as) {
    Attribute a = new Attribute(this, as);
    if(a.getValue()!=null)
      return a;
    return null;
  }
  public int getFlags() {
    return flags;
  } 
  public static IValueProvider detectValueProvider(String key, AttributeType type, Object val, IRelatedFilesProvider resFolder) {
    if(val instanceof TypedValue)
      return detectValueProvider(key, type, detectFlags(key), ((TypedValue)val).getValue(), resFolder);
    return detectValueProvider(key, type, detectFlags(key), val, resFolder);
  }
  public static IValueProvider detectValueProvider(String key, AttributeType type, int flag, Object val, IRelatedFilesProvider resFolder) {
    if(Attribute.getHTMLNames().contains(key))
      type = AttributeType.HTML;
    if(type == AttributeType.REFERENCE||type == AttributeType.LIST && 
        val!=null && ((ListValue)val).getType()==AttributeType.REFERENCE){
      return new ReferenceValueProvider(
          new TypedValue(type, val));
    }
    if(type == AttributeType.STRING||type == AttributeType.HTML){
      String av = null;
      if(val!=null)
        av = val.toString();
      if(type == AttributeType.HTML)
        return new HtmlValueProvider(type, av, resFolder);
      if(av!=null){
        int s = av.indexOf("{");
        if(s>-1&&av.indexOf("}",s)>-1)
          return new TemplValueProvider(type, av);
      }
    }
    if(type == AttributeType.LIST && val != null){
      if(isTemplStringList(((ListValue)val).getType(),val))
        return new TemplValueProvider(type, val);
    }
    return new ConstValueProvider(type, val);
  }
  private static boolean isTemplStringList(AttributeType type2, Object val) {
    if(type2==AttributeType.STRING||type2==AttributeType.HTML){
      ListValue lv = (ListValue)val;
      for(Object o : lv.getValues())
      {
        if(o == null)
          continue;
        String av = o.toString();
        int s = av.indexOf("{");
        if(s>-1&&av.indexOf("}",s)>-1)
          return true;
      }
    }
    if(type2==AttributeType.LIST){
      ListValue lv = (ListValue)val;
      Iterator<ListValue> it = lv.giterator();
      while(it.hasNext())
      {
        ListValue i = it.next();
        if(isTemplStringList(i.getType(), i))
          return true;
      }
    }
    return false;
  }
  public AttributeGenerator getGenerator() {
    if(valueProvider instanceof GeneratorValueProvider)
      return ((GeneratorValueProvider)valueProvider).getGenerator();
    return null;
  }
  public boolean isHidden() {
    return (flags&Flag.HIDDEN)==Flag.HIDDEN;
  }
  public boolean isReadOnly() {
    return (flags&Flag.READ_ONLY)==Flag.READ_ONLY;
  }
  public boolean changeReadOnly() {
    return (flags&Flag.CHANGE_READ_ONLY)==Flag.CHANGE_READ_ONLY;
  }
  public boolean isAvaiableOn(TreeNode node, boolean checkExOnOrigin) { // does not check av correctness
    // TODO look into
    if (node == null) return false;
    if (getOrigin() == null) return true; // Attr to be added
    if (getOrigin().equals(node.getUUId())) return true;//if it is local then available
    TreeNode or = node.getTreeDB().getNode(getOrigin());
    if (or == null) return false;
    AttributeDefinition ora = or.attrs().getAttributeDefinition(getKey());
    if (checkExOnOrigin && (ora == null || !ora.getOrigin().equals(or.getUUId()))) // removed
        return false; // then required one was removed
    if(ora == null)
      ora = AttributeDefinition.this;
    AttributeAvailability aa = ora.getAvailability();
    UUID paruid = node.getWorkParentUUID();
    if (!aa.equals(AttributeAvailability.LOCAL)) {
      if (paruid!=null && paruid.equals(getOrigin())) return true;
    }
    TreeNode par = node.getWorkParent();
    while(par!=null&&!par.getUUId().equals(or.getUUId())){
      par = par.getWorkParent();
    }
    // if subtree var is visible then all is correct
    return (or.isRoot()
                || or.isVRoot()
                || par!=null&&par.getUUId().equals(or.getUUId()))
            && aa.equals(AttributeAvailability.SUBTREE)
        || isAvaiableOn(node.getCloneParent(),checkExOnOrigin);
  }
  public String toString(AttributeStorage as) {
    StringBuilder sb = new StringBuilder(key);
    sb.append('[');
    sb.append(getType());
    sb.append(']');
    sb.append('=');
    sb.append(Attribute.valueToString(as, getType(), getRawValue().getValue()));
    if(origin!=null)
    {
      sb.append('(');
      sb.append(origin);
      sb.append(')');
    }
    return sb.toString();
  }
  @Override
    public boolean equals(Object obj) {
      if(obj == null)
        return false;
      if(obj instanceof AttributeDefinition){
        AttributeDefinition ad = (AttributeDefinition) obj;
        
        return ((ad.origin==null&&origin == null) || (ad.origin!=null&&origin != null) && ad.origin.equals(origin))
            &&ad.flags==flags&&ad.availability==availability&&
            ad.key.equals(key)&&ad.valueProvider.equals(valueProvider);
      }
      return false;
    }
//  public boolean isReference(AttributeStorage as) {
//    return getType().equals(AttributeType.REFERENCE)
//        || getType().equals(AttributeType.LIST) && 
//            getValue(as)!=null && 
//            ((ListValue)getValue(as)).getType().equals(AttributeType.REFERENCE);
//  }
  public int getSavedFlags() {
    if(changeReadOnly())
      return getFlags()-Flag.CHANGE_READ_ONLY;
    return getFlags();
  }
  public boolean isReference() {
    if(valueProvider instanceof ReferenceValueProvider)
      return true;
    TypedValue tv = valueProvider.getRawValue();//TODO check
    if(tv == null)
      return false;
    return tv.getType().equals(AttributeType.REFERENCE)
        || tv.getType().equals(AttributeType.LIST) && 
            tv.getValue()!=null && 
            ((ListValue)tv.getValue()).getType().equals(AttributeType.REFERENCE);
  }
}
