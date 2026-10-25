package com.unitesk.requality.core.operation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;

public class CommonRemoveLocation {

  private static CommonRemoveLocation clr;

  public static void init(CommonRemoveLocation clre) {
    clr = clre;
  }

  public static CommonRemoveLocation getOne() {
    if (clr == null) init(new CommonRemoveLocation());
    return clr;
  }

  private void doRemoveLocation(Location loc, Requirement req,String locid) {
    if(req.removeLocation(locid))
      req.commitAttributes();
  }
  private void doRemoveLocation(String loc, Requirement req) {
    if(req.removeLocationByPath(loc))
      req.commitAttributes();
  }

  protected void doRemoveLocations(Map<Requirement, Set<Location>> locs) {
    for (Requirement req : locs.keySet()) {
      boolean changed = false;
      //for (Location loc : locs.get(req)) {
      changed = changed || req.removeLocationNodes(locs.get(req));
      //}
      if (changed&&!req.isDisposed()) req.commitAttributes();
    }
  }

  public void removeLocation(
      Location loc, List<Requirement> reqs, /* ReqMarker reqMarker, */ boolean batch) {
    removeLocation(loc, reqs /* ,reqMarker */, batch, null, null);
  }

  public boolean openConfirm(String title, String message) {
    // to be override
    return true;
  }

  public void openError(String title, String message) {
    // to be override
  }

  public void removeLocation(
      Location loc,
      List<Requirement> reqs,
      /* ReqMarker reqMarker, */ boolean batch,
      String locID /* for deleted */,
      TreeDB db) {
    if (loc == null && locID == null) {
      return;
    }
    if (loc != null) db = loc.getTreeDB();
    boolean started = false;
    if (!batch) {
      if (!openConfirm("Delete Location", "Delete selected Location?")) {
        return;
      }
      started = db.startTransaction("Delete Location", true);
      if (!started) {
        openError("Delete Location Error", "Some actions are already performing");
        return;
      }
    }
    try {
      removeRequirementLocation(loc, reqs, batch, locID);
      if (reqs == null || loc!=null && loc.getRequirements(true,false).length == 0) {
       // removeFragment(loc, reqs, /* reqMarker, */ batch, locID, db);
        if (loc != null) loc.delete();
      }
      if (!batch) {
        if (started) {
          db.commit();
          started = false;
        }
      }
    }finally{
      if(started)
        db.rollback();
    }
  }

  public void removeRequirementLocation(
      Location loc, List<Requirement> reqs, boolean batch, String locID) {
    if (reqs != null) {
      for (Requirement r : reqs) doRemoveLocation(loc, r, locID);
    } else {
      if (loc != null) {
        Requirement[] recs = loc.getRequirements();
        for (Requirement _req : recs) doRemoveLocation(loc, _req, locID);
      }
    }
  }
  public void removeRequirementLocations(
      Map<Location, List<Requirement>> map, boolean noGui) {
    Map<Requirement, Set<Location>>revm = new HashMap<>();
    for(Entry<Location, List<Requirement>> l:map.entrySet())
    {
      List<Requirement>req = l.getValue();
      if(req == null) req = Arrays.asList(l.getKey().getRequirements());
      for(Requirement r:req){
        if(!revm.containsKey(r))
          revm.put(r, new HashSet<>());
        revm.get(r).add(l.getKey());
      }
      
    }
    doRemoveLocations(revm);
  }
//
//  public void RemoveLocations(
//      Map<Requirement, Set<Location>> locs, /* ReqMarker reqMarker, */ boolean batch) {
//    if (locs == null) {
//      return;
//    }
//    TreeDB db = null;
//    if (!locs.isEmpty()) db = locs.keySet().iterator().next().getTreeDB();
//    if (!batch) {
//      if (!openConfirm("Delete Locations", "Delete selected Locations?")) {
//        return;
//      }
//      if (db!=null && !db.startTransaction("Delete Locations", true)) {
//        openError("Delete Locations Error", "Some actions are already performing");
//        return;
//      }
//    }
//
//    doRemoveLocations(locs);
//    String jsData = null;
//    Map<Document, Set<String>> toDel = new HashMap<>();
//    Map<Location, Set<Requirement>> stdel = new HashMap<>();
//    for (Requirement req : locs.keySet())
//      for (Location loc : locs.get(req)) {
//        if (!stdel.containsKey(loc)) stdel.put(loc, new HashSet<Requirement>());
//        stdel.get(loc).add(req);
//      }
//    for (Location l : stdel.keySet())
//      if (stdel.get(l).iterator().next() == null || l.getRequirements().length == 0) {
//        Document doc = (Document) l.getWorkParent();
//        if (doc != null) {
//          if (!toDel.containsKey(doc)) toDel.put(doc, new HashSet<String>());
//          toDel.get(doc).add(l.getSUUId());
//          toDel.get(doc).add(l.getRawId());
//        }
//      }
//
//    for (Document doc : toDel.keySet()) {
//      /*
//       * if (reqMarker == null) { IEditorReference eRef = null; try { eRef
//       * = RequalityPlugin.findMarkEditorForDocument(
//       * doc.getQualifiedId()); } catch (PartInitException e) {
//       * e.printStackTrace(); } if (eRef != null && eRef.getEditor(true)
//       * != null && (eRef.getEditor(true) instanceof ReqMarker)) {
//       * reqMarker = (ReqMarker) eRef.getEditor(true); } } if (reqMarker
//       * != null) { try { Set<String>locidmap = new HashSet<String>();
//       * for(String lc:toDel.get(doc)) locidmap.add(lc); jsData =
//       * reqMarker.getBrowser().deleteLocations(locidmap);
//       * reqMarker.saveDocument(jsData); } catch (CoreException e) {
//       * e.printStackTrace(); } } else
//       */ {
//        try {
//          doc.getDOM();
//        } catch (Exception e) {
//          e.printStackTrace();
//        }
//        String data = doc.removeLocationRegions(toDel.get(doc));
//        doc.saveDOM(data, doc.getXmlDoc());
//      }
//    }
//    for (Location l : stdel.keySet()) {
//      if (stdel.get(l).iterator().next() == null || l.getRequirements().length == 0) l.delete();
//    }
//    if (!batch && db!=null) {
//      db.commit();
//    }
//  }
  
  /**
   * VSCODE-PATCH: the UUID of a location reference of a requirement. The catalogs converted from an older format
   * reference a location by its path ("/Documents/a.xhtml/&lt;uuid&gt;"), which UUID.fromString rejected, so the
   * requirement could not be deleted.
   */
  static UUID locationUUID(String ref) {
    try {
      return UUID.fromString(ref);
    } catch (IllegalArgumentException e) {
      int slash = ref.lastIndexOf('/');
      if (slash >= 0)
        try {
          return UUID.fromString(ref.substring(slash + 1));
        } catch (IllegalArgumentException e2) {
          // not a location reference
        }
      return null;
    }
  }

  public void reactOnRequirementRemove(TreeDB db, NodeDesc req, byte flag, boolean guiMessages, RemoveRequirementLocationCollection r) {
    AttributeDefinition ad = req.getAttributes().get(Requirement.ATTR_LOCATIONS);
    if(ad==null)
      return;
    boolean started = false; 
    if (guiMessages) {
      if (!openConfirm("Delete Locations", "Delete selected Locations?")) {
        return;
      }
      
      if (db!=null) {
        started = db.startTransaction("Delete Locations", true);
        if(!started) {
          openError("Delete Locations Error", "Some actions are already performing");
          return;
        }
      }else
        openError("Delete Locations Error", "Provided tree is null");
    }
    try {
      TypedValue tv = ad.getRawValue();
      if(tv == null || tv.getType() != AttributeType.LIST)
        return;
      if(db == null)
        return;
      ListValue lv = tv.getGValue();
      List<Object> val = lv.getValues();//locs
      List<Location> cachedLocs = null;
      List<UUID> toRem = null;
      for(Object o: val) {
        String locId = (String) o;
        UUID loc = locationUUID(locId); // VSCODE-PATCH: a location addressed by its path
        if (loc == null)
          continue;
        Map<UUID, String> reqs = RelManager.getRevertLinks(db, loc, false, Requirement.ATTR_LOCATIONS);
        reqs.remove(req.getUUId());//remove removed req
        boolean rem = false;
        if(reqs.size() == 0) {
          if(toRem == null)
            toRem = new LinkedList<>();
          toRem.add(loc);
          rem = true;
        }
        if(!rem) {
          TreeNode locn = db.getNode(loc, TreeDB.DEFAULT_DEPTH, true, false);
          if(locn!=null) 
            r.registerToUpdate(locn);
        }
      }
      if(toRem!=null)
        for(UUID loc:toRem) {
          TreeNode tloc = db.getNode(loc);
          if(tloc!=null)
            r.registerToRemove(tloc);
          else 
            RLogger.logDebug("Attempt to delete requirement with lost location "+req.getUUId());
        }
      if(cachedLocs!=null)
        for(Location l:cachedLocs)
          l.resetLabel();
      if(started) {
        started = false;
        db.commit();
      }
    }finally {
      if(started && db!=null)
        db.rollback();
    }
  }

  public void reactOnLocationRemove(TreeDB db, UUID loc, byte flag, boolean batch, RemoveRequirementLocationCollection r) {
    Map<UUID, String> reqs = RelManager.getRevertLinks(db, loc, false, Requirement.ATTR_LOCATIONS);

    if(reqs!=null){
      for(UUID uid:reqs.keySet()) {
        TreeNode req = db.getNode(uid);
        if(req!=null)
          r.registerRemoveLocation(req, loc);
      }
//                          .removeFragment(
//                              (Location) fnode, null, true, fnode.getSUUId(), TreeDB.this);
    }
  }

  public void removeLocations(Map<Location, List<Requirement>> map, byte flag, boolean noGui) {
    TreeDB db = null;
    for(Location loc:map.keySet()){
      if (loc == null) 
        continue;
      db = loc.getTreeDB();
    }
    if(db == null&&!noGui)
      throw new RuntimeException("Tries to delete empty locs");
    boolean started = false;
    if (!noGui) {
      if (!openConfirm("Delete Locations", "Delete selected Locations?")) {
        return;
      }
      if (db!=null) {
        started = db.startTransaction("Delete Locations", true);
        if(!started) {
          openError("Delete Locations Error", "Some actions are already performing");
          return;
        }
      }
    }
    try {
      removeRequirementLocations(map, noGui);
      Map<Location,List<Requirement>> tor= new HashMap<>();
      for(Entry<Location, List<Requirement>> l:map.entrySet()){
        if (l == null || l.getKey() == null)
          continue;
        if (l.getValue() == null || l.getKey()!=null && l.getKey().getRequirements(true,false).length == 0) {
          tor.put(l.getKey(), null);
        }
      }
      if (!tor.isEmpty()) {
        for(Location loc:tor.keySet())
          if (loc != null) loc.delete();
      }
      if (started) {
        if (db != null) db.commit();
        started = false;
      }
    }finally {
      if(started && db!=null)
        db.rollback();
    }
  }


}
