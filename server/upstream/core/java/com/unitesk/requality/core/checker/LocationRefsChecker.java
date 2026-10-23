	package com.unitesk.requality.core.checker;

import java.util.Arrays;
import java.util.List;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;

public class LocationRefsChecker implements INodeChecker {
  private class LocationCheckerProblem extends CheckerTreeProblem{
    public LocationCheckerProblem(TreeNode target,String checkerMessageId, String description, String[]addData, TreeNode[]related) {
      super(target,checkerMessageId, description, addData, related);
    }
  }
  public LocationRefsChecker() {}

  @Override
  public boolean check(TreeNode node, List<IMark> result) {
    // null type is an error
    if (node.isDisposed()
        || node.getType() == null
        || (!node.getType().equals(Requirement.TYPE_NAME)
            && !node.getType().equals(TextNode.TYPE_NAME)
            && !node.getType().equals(Location.TYPE_NAME))) {
      return true;
    }
    // if(node.getType().equals(Document.TYPE_NAME)){
    //
    // Document d = (Document)node;
    // //TreeNode[]reqs = l.getWorkChildren();
    // for(UUID u:d.getLocations()){
    // TreeNode l = (TreeNode) node.getTreeDB().getNode(u);
    // if(l == null)
    // result.add(new CheckerProblem(l.getWorkParent(), "Location with UUID:
    // " + u
    // + " was missing"));
    // }
    //
    // }else
    if (node.getType().equals(Document.TYPE_NAME)) {
      for(TreeNode chi:node.getWorkChildren()){
        if(!chi.canBeCheckedIn(node.getTreeDB().getCheckerContainer()))
          return false;
        IMark[] pr = chi.getCheckerProblems();
        if(pr!=null && pr.length>0)
          result.addAll(Arrays.asList(pr));
      }
    }else
    if (node.getType().equals(Location.TYPE_NAME)) {
      int resBef = result.size();
      boolean locProbs = false;
      TreeNode wp = node.getWorkParent();
      IMark[]oldProbs = node.getCheckerProblems();
      try{
        if(!(node instanceof Location))
        {
          locProbs = true;
          result.add(
              new LocationCheckerProblem(
                  wp, "problem_location_incorrect_type", "Location with QId '%s' has an incorrect java type", new String[] {node.getQualifiedId()}, CheckerTreeProblem.NO_RELATED));
          return true;
        }
        Location l = (Location) node;
        Requirement[] reqs = l.getRequirements(true,false);
        if (reqs == null) return false; // Loading
        if (reqs.length == 0 || reqs[0] == null) {
          reqs = l.getRequirements(false,false);
          locProbs = true;
          result.add(
            new LocationCheckerProblem(
              wp, "problem_location_no_related_requirements", "Location with QId '%s' has no related requirements (orphain)", new String[] {node.getQualifiedId()}, CheckerTreeProblem.NO_RELATED));
        }
        Attribute markup = l.attrs().getAttribute(Location.ATTR_MARKUP);
        if(markup == null)
    	  result.add(
            new LocationCheckerProblem(
              wp, "problem_location_no_related_document_fragments", "Location with QId '%s' has no related document frargments (attribute not exists)", new String[] {node.getQualifiedId()}, CheckerTreeProblem.NO_RELATED));
        else
    	  if(markup.getType()!=AttributeType.LIST)
            result.add(
              new LocationCheckerProblem(
                wp, "problem_location_incorrect_markup_attribute", "Location with QId '%s' has incorrect type of markup attribute", new String[] {node.getQualifiedId()}, CheckerTreeProblem.NO_RELATED));
          else
          	if(((ListValue)markup.getValue()).getValues().size() == 0)
      		result.add(
              new LocationCheckerProblem(
                wp, "problem_location_empty_markup_attribute", "Location with QId '%s' has empty markup attribute (no related document fragments)", new String[] {node.getQualifiedId()},  CheckerTreeProblem.NO_RELATED));
      }finally{
        if(oldProbs.length!=result.size()){
          //TODO it also possible that not all checkers are checked, so add another check
          boolean oldLocP = false;
          for(IMark cp:oldProbs)
            if(cp instanceof LocationCheckerProblem){
              oldLocP = true;
              break;
            }
          if(locProbs!=oldLocP)
            node.getTreeDB().getCheckerContainer().addToCheckQ(wp, false);//updates state of doc errors list
        }
      }
    } else
      try {
        Requirement req = (Requirement) node;
/*        IMark[] probs = req.getTreeDB().getCheckerProblems(req);
        if (probs != null) {
          result.addAll(Arrays.asList(probs));*/
          // Instantiator.getRootTree(req.getTreeDB());
          List<ReferenceValue> locQIds = req.getLocationQIds();
          for (ReferenceValue qid : locQIds) {
            TreeDB db = req.getTreeDB();
            if(qid.hasErrorsOnResolve(req.attrs()))
              continue;
            try {
              TreeNode[]nodes = qid.getNodes(req.attrs());
              TreeNode location = nodes.length>0? nodes[0]: null;
              if (location == null) {
                String fqid = qid.getRaw().toString();
                String locfid = fqid;
                int lastind = fqid.lastIndexOf("/");
                if (lastind>-1) {
                  locfid = fqid.substring(lastind);
                  fqid = fqid.substring(0, lastind);
                }
                TreeNode doc = db.getNode(fqid, Path.QID | Path.UUID);
                if (doc != null)
                  result.add(
                      new CheckerProblem(
                          doc,
                          "problem_location_not_found", "Location with QId: '%s' was not found in '%s'", 
                          new String[] {locfid, req.getQualifiedId()}));
                else
                  result.add(
                      new CheckerProblem(
                          req,
                          "problem_location_document_not_found", "Location with QId: '%s' and related document ('%s') were not found in '%s'",
                          new String[] {locfid, fqid, req.getQualifiedId()}));
              } else if (!location.getType().equals(Location.TYPE_NAME)) {
                result.add(
                    new CheckerProblem(
                        req,
                        "problem_location_wrong_type", "Location with QId: '%s' has wrong type %s",
                        new String[] {location.getQualifiedId(), location.getTypeLabel()}));
              }
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        //}
      } catch (ClassCastException e) {
        result.add(new CheckerProblem(node, "problem_no_class", "Cannot load class for '%s'", new String[] {node.getQualifiedId()}));
      }
    return true;
  }
}
