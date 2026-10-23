package com.unitesk.requality.core.operation.mass;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;

public class MassLocationsFixer extends AbstractMassOperation {

  public MassLocationsFixer() {
    super("MassLocationsFixer");
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    return "Remove empty locations";
  }

  @Override
  public void performSingleAction(TreeNode node) {
    if (Document.TYPE_NAME.equals(node.getType())) {
      try {
        Document doc = (Document) node;
        // Map<UUID,String> descs = doc.g();
        List<UUID> uids = doc.getChildrenLocations();
        // if(descs == null)
        // return;
        for (UUID uid : new HashSet<>(uids)) {
          Location loc = null;
          // if(descs.containsKey(uid)){
          try {
            loc =
                (Location)
                    node.getTreeDB()
                        .getNode(doc.getQualifiedId() + "/" + uid.toString(), Path.QID | Path.UV);
          } catch (Exception e) {
            // log?
          }
          // }
          if (loc == null) // missing{
          {
            Requirement[] reqs;
            //TODO think how to fix
            reqs = null;// Location.getRequirementsFor(node.getTreeDB(), doc.getQualifiedId() + "/" + uid);
//            if (reqs != null && reqs.length > 0) {
//              // find, recreate loc node
//              loc = (Location) doc.createChild(uid.toString(), Location.TYPE_NAME);
//              RLogger.logInfo("fixes " + uid.toString());
//            } else {
              tosave.add(doc);
              // TODO message
              // doc.removeLocationRegion(uid.toString());
//              IMark[] old = doc.getTreeDB().getChprob().get(doc.getUUId());
//              IMark[] toa;
//              if (old != null && old.length > 0) toa = new IMark[old.length + 1];
//              else toa = new IMark[1];
//              toa[toa.length - 1] =
//                  new CheckerProblem(doc, "Location is missing! " + uid.toString());
//              doc.getTreeDB().getChprob().put(doc.getUUId(), toa);
              doc.getTreeDB().getCheckerContainer().addToCheckQ(doc,true);
              RLogger.logInfo("removed from doc " + uid.toString() + " " + doc.getQualifiedId());
              // not found... delete?
            //}
          }
          if (loc != null) {
            // if(!descs.containsKey(uid)){
            // descs = doc.getLocationsDescriptions();
            // doc.resetCache();
            // }
            // String newd=descs.get(uid);
            boolean changed = false;
            UUID newid = loc.getUUId();
            if (!loc.getSUUId().equals(loc.getRawId())) {
              for (Requirement r : loc.getRequirements()) {
                List<ReferenceValue> locs = r.getLocationQIds();
                if (locs != null) {
                  Iterator<ReferenceValue> it = locs.iterator();
                  while (!it.hasNext()) {
                    ReferenceValue path = it.next();
                    if(path.hasErrorsOnResolve(r.attrs()))
                      continue;//TODO think
                    TreeNode node2 = path.getNodes(r.attrs())[0];
                    if (node2 != null
                        && node2.getUUId().equals(loc.getUUId())
                        && !newid.toString().equals(path.getRaw().toString())) {
                      it.remove();
                      changed = true;
                      r.removeLocationByPath(path.getRaw().toString());
                    }
                  }
                }
                if (changed) {
                  r.addLocation(newid);
                  r.commitAttributes();
                }
              }
              if (!loc.getId().equals(loc.getSUUId()))
                loc.setId(loc.getSUUId());
              loc.commitAttributes();
            }
            // if(loc.getDescription()==null ||
            // !loc.getDescription().equals(newd)){
            // ups
            // loc.setDescription(newd);
            // loc.saveAttributes();
            // }

          }
        }

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  Set<Document> tosave = new HashSet<>();

  @Override
  public boolean execute(TreeNode root) {
    tosave.clear();
    boolean b = super.execute(root);
    if (b) for (Document d : tosave) d.saveDOMFromXMLCache();
    return b;
  }

  @Override
  public void applySettings(Map<String, String> args) {
    // TODO Auto-generated method stub

  }
}
