/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.unitesk.requality.nodetype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.exception.OrderWasAlreadySetException;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.sorting.TreeNodeComparator;

public class Requirement extends TreeNode implements Comparable<TreeNode> {
  public static final String TYPE_NAME = "Requirement";
  public static final String ATTR_LOCATIONS = "_locations";
  private static final String TYPE_ROOT_QID = "/Requirements";
  public static final String ATTR_STATUS = "coverageStatus";
  //TODO rework transfer
  public static final String ATTR_TRANSF_STATUS = "_transferStatus";
  public static final String ATTR_ORDER = "_order";

  {
    Attribute.registerStatusName(ATTR_STATUS);
    Attribute.registerStatusName(ATTR_TRANSF_STATUS);
    //Attribute.registerHTMLNames(Requirement.ATTR_LOCATIONS);
  }

  public enum TransferStatus {
    FAILED,
    INCOMPLETE,
    COMPLETE;

    public boolean isBetterThan(TransferStatus other) {
      return this.ordinal() > other.ordinal();
    }
  }

  public static String getTypeRootQId() {
    return TYPE_ROOT_QID;
  }

  // Constructor for eclipse instancer
  public Requirement() {
    this(null, null);
  }

  public Requirement(TreeDB db, UUID uuid) {
    super(db, uuid);
    setType(TYPE_NAME);
  }

  @SuppressWarnings("unchecked")
  public List<ReferenceValue> getLocationQIds() {
    Object val = attrs().getListAttributeValue(ATTR_LOCATIONS, AttributeType.REFERENCE);
    if (val == null) {
      return Collections.emptyList();
    }
    if (val instanceof List) {
      return (List<ReferenceValue>) val; // looks faster
      // return Collections.unmodifiableList((List<String>) val);
    }
    throw new IllegalStateException(ATTR_LOCATIONS + " value must be string list");
  }
  @Deprecated
  public List<Location> getLocations() {
    return getLocations(false);
  }

  public List<Location> getLocations(boolean onlyCached) {
    if (isVirtual() && getCloneParentUUId() != null) {//TODO remove tree
      TreeNode cp = getCloneParent();
      if (cp != null) return ((Requirement) cp).getLocations(onlyCached);//нельзя добавить фрагменты к клону
      else return Collections.emptyList();
    }
    List<Location> result = new ArrayList<>();
    for (ReferenceValue locQId : getLocationQIds()) {
      try {
        TreeNode locNode = null;
        if(!locQId.hasErrorsOnResolve(attrs())) {
          TreeNode[]nds = locQId.getNodes(attrs(), !onlyCached);
          if(nds!=null && nds.length>0)
            locNode = nds[0];
        }
        if (locNode != null && locNode instanceof Location) {
          result.add((Location) locNode);
        }
      } catch (RuntimeException e) {
        e.printStackTrace();
      }
    }
    result.sort(TreeNodeComparator.getComparator(getTreeDB()));
    return Collections.unmodifiableList(result);
  }

  public Location getLocation(String QId) {
    if (QId == null) return null;
    Location l = null;
    try {
      l = (Location) getTreeDB().getNode(QId, Path.QID | Path.UV | Path.UUID);
    } catch (NodeNotFoundException re) {

    }
    if (l != null)
      for(ReferenceValue rv:getLocationQIds()){
        UUID[]uids = rv.getNodeUUIDs(attrs());
        if(uids!=null && uids.length>0&& uids[0].equals(l.getUUId()))
          return l;
      }

    return null;
  }

  public void addLocation(UUID nodeQId) {
    Attribute a = attrs().getAttribute(ATTR_LOCATIONS);

    TreeDB db = getTreeDB();

    List<TreeNode> lar = new ArrayList<>();
    if (a != null) {
      ListValue lv = a.getDefinition().getRawValue().getGValue();
      List<Object> l = lv.getValues();
      if (l != null)
        for (Object o : l) {
          try {
            TreeNode ln = db.getNode(o.toString(), Path.QID | Path.UV | Path.UUID);
            if (ln != null) lar.add(ln);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
    }
    try {
      TreeNode ln = db.getNode(nodeQId);
      if (ln != null) {
        lar.add(ln);
      }
      // needs to be in react?
    } catch (Exception e) {
      e.printStackTrace();
    }
    TreeNode[] lars = new TreeNode[lar.size()];
    int i = 0;
    for (TreeNode node : lar) lars[i++] = node;
    Arrays.sort(lars, TreeNodeComparator.getComparator(db));
    List<Object> locations = new ArrayList<>();
    for (TreeNode l : lars) locations.add(l.getSUUId());
    attrs().setAttribute(
        new AttributeDefinition(getUUId(), ATTR_LOCATIONS, 
            new ReferenceValueProvider(
                AttributeType.LIST,
                new ListValue(AttributeType.REFERENCE, locations)),
        Flag.HIDDEN));
  }

  public boolean removeLocation(Location l) {
    //Location l = (Location) getTreeDB().getNode(nodeQId, Path.QID | Path.UV | Path.UUID);
    if (l != null) return removeLocation(l.getSUUId());
    return false;
  }
  public boolean removeLocationByPath(String s) {
    Location l = (Location) getTreeDB().getNode(s, Path.QID | Path.UV | Path.UUID);
    if(l!=null)
      s = l.getSUUId();
    return removeLocation(s);
  }


  public boolean removeLocationNodes(Collection<? extends TreeNode> lnodes) {
    String[]nds = new String[lnodes.size()];
    int i = 0;
    for(TreeNode s:lnodes)
      nds[i++] = s.getSUUId();
    return removeLocations(nds);
  }

  public boolean removeLocationNodeDescs(Collection<UUID> lnodes) {
    String[]nds = new String[lnodes.size()];
    int i = 0;
    for(UUID s:lnodes)
      nds[i++] = s.toString();
    return removeLocations(nds);
  }
  public boolean removeLocations(String ... lnodes) {
    if(lnodes == null)
      return false;
    AttributeDefinition locations = attrs().getWorkAttributeDefinition(ATTR_LOCATIONS);
    if(locations == null)
      return false;
    List<Object> itlocations = new ArrayList<>(((ListValue)(locations.getRawValue().getGValue())).getValues());
    boolean ch = false;
    for(String s:lnodes)
      if(itlocations.remove(s))
        ch = true;
    if(!ch)
      return false;
    if (itlocations.size() > 0) {
      attrs().setAttribute(
          new AttributeDefinition(getUUId(), ATTR_LOCATIONS, 
              new ReferenceValueProvider(
                  AttributeType.LIST,
                  new ListValue(AttributeType.REFERENCE, itlocations)),
          Flag.HIDDEN));
    } else {
      attrs().removeAttribute(ATTR_LOCATIONS);
    }
    return true;
  }
  public boolean removeLocation(String nodeQId) {
    return removeLocations(nodeQId);
  }

  @Override
  public void setId(String id) {
    if (getQualifiedId().equals(Requirement.getTypeRootQId())) {
      throw new UnsupportedOperationException("Can't rename root requirement");//TODO move to tree logic
    }
    super.setId(id);
    // fire id update actions
  }

//  @Override
//  public void updateTreeNodes(TreeNode req, String what, boolean internal) {
//    if (TYPE_NAME.equals(req.getType())
//        && (what.equals(TreeNode.ATTR_NAME) || what.equals(TreeNode.ATTR_ID)))
//      updateLocations(this);
//    super.updateTreeNodes(req, what, internal);
//  }

  public void updateLocations(Requirement req) {
    TreeDB tree = req.getTreeDB();
    tree.startChangeRow();
    try {
      for (ReferenceValue l : req.getLocationQIds()) {
        TreeNode locNode = null;
        if(!l.hasErrorsOnResolve(req.attrs())){
          TreeNode[]nodes = l.getNodes(req.attrs());
          if(nodes.length<1)
            RLogger.logInfo("Broken location for requirement "+req.getUserVisibleName());
          else
            locNode = nodes[0];
        }
        if (locNode != null) { // Can be if node not created still
          ((Location)locNode).resetLabel();
        }
      }
    }finally {
      tree.stopChangeRow();
    }
  }

  public boolean isNameDefined() {
    return attrs().getAttributeValue(ATTR_NAME) != null;
  }

  @Override
  public int compareTo(TreeNode o) {
    if (o instanceof Requirement) {
      Requirement req = (Requirement) o;
      return getNameOrId().compareTo(req.getNameOrId());
    } else {
      TreeNode req = o;
      return getNameOrId().compareTo(req.getId());
    }
  }

  public static String getFullName(TreeNode node, TreeDB db, boolean inner) {
    // TODO mb struct?
    if (node.getShownParent() == null || node.isVRoot()) {
      return "/";
    }
    if (inner
        && node.getType().equals(Requirement.TYPE_NAME)
        && (node.getShownParent().isVRoot() || node.getShownParent().isRoot())
        && node.getName() == null) return "";
    String parentFullName = Requirement.getFullName(node.getShownParent(), db, inner);
    String name;
    // if(node instanceof Requirement) {
    name = (node).getNameOrId();
    // } else {
    // name = node.getId();
    // }
    if (node instanceof VirtualNode) if (((VirtualNode) node).isCollapsed()) return parentFullName;
    if (!parentFullName.equals("/") && !parentFullName.isEmpty()) {
      parentFullName = parentFullName + "/";
    }
    return parentFullName + name;
  }

  public int compareLocationIndexes(Requirement r) {
    //TODO replace by autocalculated index;
    int min1 = 0, min2 = 0;
    Location[] loc1 = getLocations(false).toArray(new Location[0]),
        loc2 = r.getLocations(false).toArray(new Location[0]);
    for (int i = 1; i < loc1.length; i++) if (loc1[i].compareIndexTo(loc1[min1]) < 0) min1 = i;
    for (int i = 1; i < loc2.length; i++) if (loc2[i].compareIndexTo(loc2[min2]) < 0) min2 = i;
    if (loc1.length == 0 || loc2.length == 0) return ((Integer) loc1.length).compareTo(loc2.length);
    return loc1[min1].compareIndexTo(loc2[min2]);
  }

  public void setTransferProblem(TransferStatus status) {
    attrs().setConstAttribute(ATTR_TRANSF_STATUS, status.toString(),Flag.HIDDEN);
    commitAttributes();
  }

  @Override
  public boolean canEditAttributesInTable() {
    return true;
  }

  public void switchType() {
    setType(getType().equals(Requirement.TYPE_NAME) ? TextNode.TYPE_NAME : Requirement.TYPE_NAME);
  }

  @Override
  public boolean isHidden() {
    // TODO check parameters
    return super.isHidden();
  }

  public int getLocationIndex(String location) {
    int j = 0;
    List<ReferenceValue> locs = getLocationQIds();
    for (int i = 0; i < locs.size(); i++) {
      ReferenceValue rv = locs.get(i);
      if(rv.hasErrorsOnResolve(attrs()))
        continue;
      Location l = (Location) rv.getNodes(attrs())[0];
      if(l == null)
        continue;
      if (l.getSUUId().equals(location)) return j;
      if (!l.isDisposed()) j++;
    }
    return -1;
  }
  
  @Override
  public String getDescription() {
    String s = super.getDescription();
    if(s == null && getNodeResourcesPath() != null)
      return getLocatonsDescription(getNodeResourcesPath().toString());
    return s;
  }

  public Location getFirstLocation() {
    List<ReferenceValue> rvs = getLocationQIds();
    for(ReferenceValue r:rvs){
      if(r.hasErrorsOnResolve(attrs()))
        continue;
      Location l = (Location) r.getNodes(attrs())[0];
      if(l!=null && !l.isDisposed())
        return l;
    }
    return null;
  }

  @Override
  public boolean hasHeaderAttribute() {
    if(getType().equals(Requirement.TYPE_NAME))
      return super.hasHeaderAttribute();
    return !attrs().getStringAttributeValue(TextNode.ATTR_TNTYPE, "text").equals("text")
            || super.hasHeaderAttribute();
  }
  //TODO node type problem
  @Override
  public boolean typeRelatedAttributeChangeReactionBefore(ChangesSet<Attribute> cs, byte flag) {
    Attribute ado = cs.getNew(TreeNode.ATTR_TYPE);
    Set<Location> olist = new HashSet<>();
    if(ado != null && ado.getValue() != null){
      String type = ado.getGValue();
      if(type != null && type.equals(Requirement.TYPE_NAME)){
        Attribute old = attrs().getAttribute(TextNode.ATTR_TNTYPE);
        if(old!=null) {
          List<String> ord = cs.getOrder();
          cs.resetOrder();
          if(ord.contains(TextNode.ATTR_TNTYPE))
            ord.remove(TextNode.ATTR_TNTYPE);
          ord.add(TextNode.ATTR_TNTYPE);
          try {
            cs.addSomeChange(TextNode.ATTR_TNTYPE, old, null);
            cs.setOrder(ord);
          }catch (OrderWasAlreadySetException orde){
          }
        }
      }
    }
	return false;
  }

}
