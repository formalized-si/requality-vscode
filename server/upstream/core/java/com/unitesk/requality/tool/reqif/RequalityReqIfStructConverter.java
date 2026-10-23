package com.unitesk.requality.tool.reqif;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.fid.FIDUpdater;
import com.unitesk.requality.core.filter.IObjectFilter;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.IDMechanics;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.Index;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class RequalityReqIfStructConverter {
  private static final IObjectFilter<String> doorsFilter =
      new IObjectFilter<String>() {

        @Override
        public boolean filter(String object) {
          if (object == null) return false;
          if (object.toLowerCase().equals("примечание")) return false;
          return true;
        }
      };
  private RequalityReqIfAttributesConverter conv;

  public RequalityReqIfStructConverter(RequalityReqIfAttributesConverter conv) {
    this.conv = conv;
  }

  public void convertReqToDoors(
      Map<Specification, TreeDB> mapa, ReqIFModel model, Map<String, Boolean> ignored) {
    model.header.map.put("CREATION-TIME", ReqIFModel.getTime());
    model.buildOriginMaps(mapa, ReqIFModel.FOREIGN);
    for (Specification s : mapa.keySet()) {
      TreeDB db = mapa.get(s);
      UUID rid = db.getRootNode().getUUId();
      s.attributes.put(
          ReqIFDefinition.CHANGE_PARAM, new Pair<String, String>(null, ReqIFModel.getTime()));
      // TODO mark links

      SpecHierarchyContainer shc = s.getChildren();
      TreeNode reqroot = db.getNode(Requirement.getTypeRootQId(), Path.QID);
      Pair<Set<SpecObject>, Map<SpecObject, TreeNode>> res =
          reqToDoorsStruct(s, shc, reqroot, db, model); // build
      // structure
      // model.buildOriginMaps(db);//rebuild origin
      for (SpecObject so : res.getFirst())
        if (!model.getRMapping(db).containsKey(so.getId()))
          model.getDoorsExt().getDeleted().addDeleted(so);
        else model.getDoorsExt().getDeleted().removeDeleted(so);
      for (SpecObject so : res.getSecond().keySet()) {
        TreeNode node = res.getSecond().get(so);
        // TODO comparing
        conv.convertReqToDoors(node, so, model); // convert attributes
        ignored.remove(so.getId());
        // if(soc.isValuesSimilarTo(so)){
        // model.objectById.remove(so.getId());
        // cnt++;
        // }else
        // cupnt++;
        // for(SpecObject so:model.getDeleted())
        // model.objectById.remove(so.getId());
        // RLogger.logInfo("NotChanged: "+cnt+" updated: "+cupnt);

        //
        // SpecRelationType srt;
        // Iterator<SpecRelationType> it =
        // model.specRelationTypesById.values().iterator();
        // if(it.hasNext())
        // srt = it.next();//TODO many types
        // else
        // srt =SpecRelationType.create(model);
      }
      // model.specRelationTypesById.clear();
      // model.specRelationTypesById.put(srt.getId(), srt);

      // String gid = "_"+UUID.randomUUID().toString();
      // Map<String,Pair<String,String>> attrs = new
      // HashMap<String,Pair<String,String>>();
      // attrs.put(ReqIFDefinition.ID_PARAM, new
      // Pair<String,String>(null,gid));
      // attrs.put(ReqIFDefinition.CHANGE_PARAM, new
      // Pair<String,String>(null,ReqIFModel.getTime()));
      // attrs.put(ReqIFDefinition.NAME_PARAM, new
      // Pair<String,String>(null,map.getName()+"_Links"));
      //
      // attrs.put(ReqIFDefinition.ATTR_DESC, new
      // Pair<String,String>(null,"This module was created in
      // Requality"));
      // RelationGroup rg = new RelationGroup(attrs, srt, s,s);
      // for(String s2:model.specRelationById.keySet())
      // model.getDeleted().add(model.specRelationById.get(s2));
      // model.specRelationById.clear();
      // sg.groups.put(rg.getId(), rg);
      DoorsRIFDefinition drd =
          ((DOORSToolExtension) model.ext.getExtensions().iterator().next()).drifd;
      ModuleDefinition nm = null;
      // if(!drd.hasModule(srt)){
      // nm = new ModuleDefinition("NO_DDC",
      // srt);
      // drd.modules.add(nm);
      // }

    }
    for (Specification s : mapa.keySet()) {
      TreeDB db = mapa.get(s);
      if (db == null) continue;
      TreeNode reqroot = db.getNode(Requirement.getTypeRootQId(), Path.QID);
      if (!recRelAdd(reqroot, s, mapa, model, ignored)) {}
    }
    // if(nm!=null)
    // // drd.modules.remove(nm);
    // sg.groups.remove(rg.getId());
    // for(NodeAttributeConverter sac2:converters)
    // sac2.requality2Doors(node, object,model);
  }

  private boolean recRelAdd(
      TreeNode node,
      Specification s,
      Map<Specification, TreeDB> mp,
      ReqIFModel model,
      Map<String, Boolean> ignored) {
    boolean ret = false;
    String vid = node.attrs().getStringAttributeValue("Вид");
    if (node.getWorkChildren().size() == 0 && vid != null && vid.toLowerCase().equals("примечание"))
      return true;
    List<Pair<String, TreeNode>> list = node.getRelations(null);
    String src = model.getMapping(node.getTreeDB()).get(node.getUUId());
    SpecObject so = model.objectById.get(src);
    Specification ssource = model.objectToSpec.get(src);
    for (Pair<String, TreeNode> p : list) {
      TreeNode sec = p.getSecond();
      if(sec == null)
        continue;
      String vid2 = sec.attrs().getStringAttributeValue("Вид");
      if (sec.getWorkChildren().size() == 0
              && vid2 != null
              && vid2.toLowerCase().equals("примечание")) continue;
      Map<String, Pair<String, String>> attrs = new HashMap<>();
      attrs.put(
          ReqIFDefinition.ID_PARAM,
          new Pair<String, String>(null, "_" + UUID.randomUUID().toString()));
      attrs.put(ReqIFDefinition.CHANGE_PARAM, new Pair<String, String>(null, ReqIFModel.getTime()));
      // attrs.put("ReqIF.Name", new
      // Pair<String,String>(null,p.getFirst()));

      Map<UUID, String> amap = model.getMapping(sec.getTreeDB());
      if (amap == null) continue; // Links module was not loading

      String trg = amap.get(sec.getUUId());
      SpecObject to = model.objectById.get(trg);
      Specification starg = model.objectToSpec.get(trg);
      if (starg != null) {
        RelationGroup rg = detectGroupAndGetOrCreateSRT(model, ssource, starg);
        SpecRelationType srt = rg.relationType;

        for (SpecRelation sr : rg.relationIds) {
          if (sr.sourceS == null || sr.targetS == null) {
            ignored.put(sr.getId(), true);
            continue;
          }
          if (sr.sourceS.equals(src) && sr.targetS.equals(trg)) {
            ignored.remove(sr.getId());
            continue;
          }
        }
        SpecRelation sr = new SpecRelation(attrs, srt, so, to, src, trg);
        AttributeDefinition ad = sr.getAttributeDefinitionByName("ReqIF.ForeignCreatedOn");
        if (ad != null) { // TODO handle else
          sr.setValue(ad.getId(), new DefaultAttributeValue(ad, "DATE", ReqIFModel.getTime()));
        }
        ad = sr.getAttributeDefinitionByName(ReqIFDefinition.LAST_MOD);
        if (ad != null) { // TODO handle else
          sr.setValue(ad.getId(), new DefaultAttributeValue(ad, "DATE", ReqIFModel.getTime()));
        }
        ad = sr.getAttributeDefinitionByName(ReqIFDefinition.CREATED_PARAM);
        if (ad != null) { // TODO handle else
          sr.setValue(ad.getId(), new DefaultAttributeValue(ad, "DATE", ReqIFModel.getTime()));
        }
        model.specRelationById.put(sr.getId(), sr);
        rg.addRelation(sr);
        ret = true;
      }
    }
    for (TreeNode chi : node.getWorkChildren()) ret = recRelAdd(chi, s, mp, model, ignored) || ret;
    return ret;
  }

  private RelationGroup detectGroupAndGetOrCreateSRT(
      ReqIFModel model, Specification so, Specification st) {
    // many groups?
    if (so == null || st == null) return null;
    SpecRelationType srt = null;
    ;
    // TODO do not return group without rel type
    SpecGroup sg = model.specRelationGroupsById.get(so.getId());
    if (sg != null)
      for (RelationGroup rg : sg.groups.values()) {
        srt = rg.relationType;
        if (rg.targetSpecId == null)
          RLogger.logInfo("null spec id while processing "+model.getPath());
        if (rg.sourceSpecId.equals(so.getId()) && rg.targetSpecId.equals(st.getId())) {
          return rg;
        }
      }
    else
      return null;
    if (srt == null) srt = model.createRelationType();
    Map<String, Pair<String, String>> attrs = new HashMap<>();

    attrs.put(
        ReqIFDefinition.ID_PARAM,
        new Pair<String, String>(null, "_" + UUID.randomUUID().toString()));
    attrs.put(ReqIFDefinition.CHANGE_PARAM, new Pair<String, String>(null, ReqIFModel.getTime()));
    attrs.put(
        ReqIFDefinition.ATTR_DESC,
        new Pair<String, String>(null, "Модуль связи создан программой Requality автоматически"));
    attrs.put(ReqIFDefinition.NAME_PARAM, new Pair<String, String>(null, sg.getName()));

    RelationGroup rg = new RelationGroup(attrs, srt, so, st, so.getId(), st.getId());
    sg.add(rg);
    return rg;
  }

  protected static int getMax(ReqIFModel model) {
    // for(Specification spec:model.getSpecifications())
    // for(SpecObject so:spec.getSpecObjects())
    // {
    // String val = so.getValueByName("Absolute Number");
    // if(val == null||val.isEmpty())
    // val = so.getValueByName("ReqIF.ForeignID");
    // if(val!=null)
    // try{
    // max = Math.max(max, Integer.parseInt(val));
    // }
    // catch(Exception e){
    // e.printStackTrace();
    // }
    // }
    return -1; // new ids will be generated in doors
  }

  private Set<SpecObject> recDel(SpecHierarchyContainer shc, TreeDB db, ReqIFModel model) {
    if (shc == null) return Collections.emptySet();
    Set<SpecObject> del = new HashSet<>(model.getDoorsExt().getDeleted().getDeleted());
    Iterator<SpecHierarchy> it = shc.iterator();
    while (it.hasNext()) {
      SpecHierarchy sh = it.next();
      SpecObject sob = sh.getSpecObject();
      if (!model.getRMapping(db).containsKey(sob.getId())) { // there is no
        // tree node
        // relative
        // to this
        // specobj
        // -> it was
        // deleted
        del.add(sob);
      } else { // There is related tree node -> it will be updated in
        // future
        // conv.convertReqToDoors(db.getNode(model.getRMapping().get(sob.getId())),
        // sob, model);
        it.remove(); // remove old spec high
      }
      del.addAll(recDel(sh.children, db, model));
    }
    return del;
  }

  private Map<SpecObject, TreeNode> recAdd(
      Specification spec, SpecHierarchyContainer shc, TreeNode node, TreeDB db, ReqIFModel model) {
    Map<SpecObject, TreeNode> map = new HashMap<>();
    for (TreeNode chi : node.getSortedWorkChildren()) {
      SpecObject so;
      if (model.getMapping(chi.getTreeDB()).containsKey(chi.getUUId())) { // old
        // node
        so = model.objectById.get(model.getMapping(chi.getTreeDB()).get(chi.getUUId()));
        
        AttributeDefinition ad = so.getAttributeDefinitionByName("ReqIF.ForeignID");
        if (ad != null && model.getFidMapping(chi.getTreeDB()).get(chi.getUUId()) != null) { // TODO
          // handle
          // else
          so.setValue(
              ad.getId(),
              new DefaultAttributeValue(
                  ad, "INTEGER", model.getFidMapping(chi.getTreeDB()).get(chi.getUUId())));
        }
        ad = so.getAttributeDefinitionByName("Origin");
        if (ad != null) // TODO handle else
        so.setValue(
              ad.getId(),
              new DefaultAttributeValue(
                  ad,
                  "STRING",
                  "u:"
                      + chi.getUserVisibleName(false)
                      + "|i:"
                      + chi.getUUId()
                      + "|q:"
                      + chi.getQualifiedId()));

        ad = so.getAttributeDefinitionByName(ReqIFDefinition.LAST_MOD);
        if (ad != null) { // TODO handle else
          so.setValue(ad.getId(), new DefaultAttributeValue(ad, "DATE", ReqIFModel.getTime()));
        }
        // conv.convertReqToDoors(chi, so, model);
      } else { // new one?
        String newOne = IDMechanics.getShownIdPrefix(chi.getTreeDB())+Integer.toString(model.maxind); // getMax(reqtodoors);
        if (node.getExternalForeignID() != null) newOne = node.getExternalForeignID();
        String id = "_" + UUID.randomUUID().toString();
        if (newOne != null && !newOne.equals("0")) id = "_" + newOne + id;
        so = model.createSpecObject(spec, id);
        model.getMapping(chi.getTreeDB()).put(chi.getUUId(), so.getId());
        model.getRMapping(chi.getTreeDB()).put(so.getId(), chi.getUUId());
        for (AttributeDefinition ad : so.definition.getAttributeDefinitions()) {
          so.setValue(ad.getId(), ad.datatype.getDefaultValue(ad));
        }

        AttributeDefinition ad = so.getAttributeDefinitionByName("ReqIF.ForeignCreatedOn");
        if (ad != null) { // TODO handle else
          so.setValue(ad.getId(), new DefaultAttributeValue(ad, "DATE", ReqIFModel.getTime()));
        }
        ad = so.getAttributeDefinitionByName(ReqIFDefinition.LAST_MOD);
        if (ad != null) { // TODO handle else
          so.setValue(ad.getId(), new DefaultAttributeValue(ad, "DATE", ReqIFModel.getTime()));
        }
        ad = so.getAttributeDefinitionByName(ReqIFDefinition.CREATED_PARAM);
        if (ad != null) { // TODO handle else
          so.setValue(ad.getId(), new DefaultAttributeValue(ad, "DATE", ReqIFModel.getTime()));
        }

        ad = so.getAttributeDefinitionByName("ReqIF.ForeignID");
        if (ad != null) { // TODO handle else
          so.setValue(
              ad.getId(),
              new DefaultAttributeValue(
                  ad,
                  "INTEGER",
                  node.attrs().getStringAttributeValue(FIDUpdater.getFIDName(node.getTreeDB()), "0")));
        }
        ad = so.getAttributeDefinitionByName("Origin");
        if (ad != null) // TODO handle else
        so.setValue(
              ad.getId(),
              new DefaultAttributeValue(
                  ad,
                  "STRING",
                  "u:"
                      + chi.getUserVisibleName(false)
                      + "|i:"
                      + chi.getUUId()
                      + "|q:"
                      + chi.getQualifiedId()));
        // model.objectById.put(so.getId(), so);
        // conv.convertReqToDoors(chi, so, model);
      }
      map.put(so, chi);
      Map<String, Pair<String, String>> attrs = new HashMap<>();
      attrs.put(
          ReqIFDefinition.ID_PARAM,
          new Pair<String, String>(null, "_" + UUID.randomUUID().toString()));
      attrs.put(ReqIFDefinition.CHANGE_PARAM, new Pair<String, String>(null, ReqIFModel.getTime()));
      attrs.put("IS-EDITABLE", new Pair<String, String>(null, "true"));
      SpecHierarchy sh = new SpecHierarchy(attrs, so);
      shc.add(sh);
      model.objectToSpec.put(so.getId(), spec);
      if (chi.getWorkChildren().size() > 0) {
        sh.children = new SpecHierarchyContainer();
        Map<SpecObject, TreeNode> nmap = recAdd(spec, sh.children, chi, db, model);
        if (nmap != null) map.putAll(nmap);
      }
    }
    return map;
  }

  private Pair<Set<SpecObject>, Map<SpecObject, TreeNode>> reqToDoorsStruct(
      Specification spec, SpecHierarchyContainer shc, TreeNode node, TreeDB db, ReqIFModel model) {
    return new Pair<>(
        recDel(shc, db, model), recAdd(spec, shc, node, db, model));
  }

  public void convertDoorsToReq(Specification s, TreeDB db, Map<String, String> attrrenames, ReqIFModel model) {
    TreeNode node = db.getNode(Requirement.getTypeRootQId(), Path.QID);
    doorsToRecStruct(s, s.getChildren(), node,attrrenames, model);
    // model.buildOriginMaps(node.getTreeDB());
    // for(NodeAttributeConverter sac2:converters)
    // sac2.doors2Requality(object,node,model);
  }

  private LinkedList<TreeNode> recAdd(TreeNode node) {
    LinkedList<TreeNode> ext = new LinkedList<>();
    for (TreeNode chi : node.getWorkChildren()) {
      ext.add(chi);
      ext.addAll(recAdd(chi));
    }
    return ext;
  }

  private Pair<Map<TreeNode, String>, List<Pair<SpecObject, TreeNode>>> createStruct(
      List<TreeNode> ext, SpecHierarchyContainer children, TreeNode node, Map<String, String> attrrenames, ReqIFModel model) {
    if (children == null) return null;
    List<Pair<SpecObject, TreeNode>> map = new LinkedList<>();
    Map<TreeNode, String> ids = new HashMap<>();
    int ind = 1;
    for (SpecHierarchy sh : children) {
      if (model.getDeleted().contains(sh.getSpecObject())) continue; // ignore deleted
      TreeNode chi = null;
      if (model.getRMapping(node.getTreeDB()).containsKey(sh.getSpecObject().getId())) {
        UUID uid = model.getRMapping(node.getTreeDB()).get(sh.getSpecObject().getId());
        if (uid != null) {
          chi = node.getTreeDB().getNode(uid);
          ext.remove(chi);
        }
      }
      boolean newone = chi==null;
      if (chi == null) {
        chi = node.createChild(RequalityCLI.getNextId(node, Requirement.TYPE_NAME, null));
        RLogger.logInfo("create " + chi.getQualifiedId());
      }
      conv.convertDoorsToReq(sh.getSpecObject(), chi, attrrenames, model);
      // AbstractAttributeValue name =
      // sh.getSpecObject().getAttributeValueByName("ReqIF.ChapterName");
      // if(name!=null&&name.getString()!=null&&!name.getString().isEmpty()){
      // ids.put(chi, name.getString().trim());
      //// //chi.setName(name.getString());
      // }
      // else

      ids.put(chi, chi.attrs().getStringAttributeValue("ReqIF.ForeignID", chi.getId()));

      if (!chi.getWorkParentUUID().equals(node.getUUId())) {
        Set<String> nids = new HashSet<>();
        for (TreeNode s : node.getWorkChildren()) nids.add(s.getId());
        for (TreeNode s : chi.getWorkParent().getWorkChildren()) nids.add(s.getId());
        chi.setId(RequalityCLI.getNextId(nids));

        RLogger.logInfo("move " + chi.getQualifiedId());
        try {
          chi.setParent(node);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      ind++;
      if (!Index.create(Integer.toString(ind)).equals(chi.getIndex()))
        chi.setIndex(Index.create(Integer.toString(ind)),!newone);
      // AbstractAttributeValue desc =
      // sh.getSpecObject().getAttributeValueByName("ReqIF.Text");
      //
      // String sval =
      // sh.getSpecObject().getValueByName("ReqIF.ForeignID");
      // if(sval!=null)
      // chi.attrs().setAttribute(TreeNode.ATTR_DOORS_ID, sval);
      // if(desc!=null){
      //// conv.convertDoorsToReq(sh.oref, node, model);
      // String val = desc.getString();
      // if(val!=null&&!val.isEmpty()){
      // String old = chi.getDescription();
      // if(old==null || (!old.contains("<img")&&!old.contains("<table")))
      // {
      // if(!val.equals(chi.getDescription())){
      // RLogger.logInfo("description of "+chi.getQualifiedId()+"
      // changes from "+chi.getDescription()+" to "+val);
      // chi.setDescription(val);
      // }
      // }else
      // RLogger.logInfo("description of "+chi.getQualifiedId()+" is
      // not modified");
      // }
      // }
      chi.commitAttributes();
      map.add(new Pair<>(sh.getSpecObject(), chi));
      Pair<Map<TreeNode, String>, List<Pair<SpecObject, TreeNode>>> ad =
          createStruct(ext, sh.children, chi, attrrenames, model);
      if (ad != null) {
        map.addAll(ad.getSecond());
        ids.putAll(ad.getFirst());
      }
    }
    return new Pair<>(ids, map);
  }

  private void doorsToRecStruct(
      Specification s, SpecHierarchyContainer children, TreeNode node, Map<String, String> attrrenames, ReqIFModel model) {
    List<Pair<SpecObject, TreeNode>> list = new LinkedList<>();
    List<TreeNode> ext = recAdd(node);
    model.buildOriginMaps(s, node.getTreeDB(), ReqIFModel.FOREIGN); // TODO
    // mult
    // s
    Pair<Map<TreeNode, String>, List<Pair<SpecObject, TreeNode>>> st =
        createStruct(ext, children, node,attrrenames, model);
    list = st.getSecond();
    Map<TreeNode, String> ids = st.getFirst();
    Set<TreeNode> exists = new HashSet<>();
    for (Pair<SpecObject, TreeNode> so : list) {
      if (model.getDeleted().contains(so.getFirst())) {
        try {
          ext.add(so.getSecond());
        } catch (Exception e) {
          // already deleted
        }
      } else {
        exists.add(so.getSecond());
        // conv.convertDoorsToReq(so.getFirst(), so.getSecond(), model);
        so.getSecond().commitAttributes();
      }
    }
    ext.removeAll(exists);
    for (TreeNode rm : ext)
      try {
        if (!rm.isDisposed()) {
          RLogger.logInfo("remove " + rm.getQualifiedId());
          rm.delete(); // TODO look into
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    for (TreeNode nde : ids.keySet()) {
      if (!nde.isDisposed()) {
        // nde.setName(ids.get(nde));
        if (nde.getWorkParent().findWorkChild(ids.get(nde)) == null) {
          nde.setId(ids.get(nde));
          nde.commitAttributes();
        }
      }
    }
    model.buildOriginMaps(s, node.getTreeDB(), ReqIFModel.FOREIGN); // TODO
    // mult
    // s
    // TODO spec
    for (SpecRelation sr : model.getSpecRelations()) {
      UUID src = model.getRMapping(node.getTreeDB()).get(sr.source.getId());
      UUID trg = model.getRMapping(node.getTreeDB()).get(sr.target.getId());
      if (src != null && trg != null) {
        TreeNode srcn = node.getTreeDB().getNode(src);
        TreeNode trgn = node.getTreeDB().getNode(trg);
        if (srcn != null && trgn != null) {
          if (!srcn.getRelatedNodes(doorsFilter).contains(trgn)) {
            String key = "relates-";
            int cnt = 0;
            while (srcn.attrs().getAttribute(key + cnt) != null) {
              cnt++;
            }
            //TODO complex versioning
            srcn.attrs().setAttribute(new com.unitesk.requality.core.attribute.AttributeDefinition(
                srcn.getUUId(), key + cnt, new ReferenceValueProvider(
                    AttributeType.REFERENCE, trgn.getQualifiedId()),
                com.unitesk.requality.core.attribute.AttributeDefinition.detectFlags(key+cnt)));
            srcn.commitAttributes();
            trgn.commitAttributes();
            RLogger.logInfo(
                "add reference from " + srcn.getQualifiedId() + " to " + trgn.getQualifiedId());
          }
        }
      }
    }
  }
}
