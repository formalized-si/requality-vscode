package com.unitesk.requality.core.attribute;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.api.IReferenceVersionRuleProvider;
import com.unitesk.requality.core.attribute.reference.IReferenceVersionRule;
import com.unitesk.requality.core.checker.CheckerRulesHolder;
import com.unitesk.requality.core.events.DefaultTreeChangeListener;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;

public class AttributesRulesHolder {
  private TreeNode nodeToStore = null;
  public AttributesRulesHolder(TreeDB treeDB) {
    nodeToStore = treeDB.getRootNode();
    loadRules();
    treeDB.addListener(new DefaultTreeChangeListener(){
      @Override
      public void changed(TreeNode node, ChangesSet<Attribute> params) {
        if(node.isRoot())
        {
          if(RequiredAttributesRule.isChanged(params))
            rar = RequiredAttributesRule.load(node);
        }
      }
    });
    IReferenceVersionRuleProvider rvrp = API.getReferenceValueProvider();
    if(rvrp!=null)
      rvr = rvrp.getRule(treeDB);
  }
  private IReferenceVersionRule rvr;
  private RequiredAttributesRule rar;
  private CheckerRulesHolder crh;
  public void saveRules() {
    if (nodeToStore != null){ 
      RequiredAttributesRule.save(nodeToStore, rar);
    }
  }
  public void loadRules() {
    if (nodeToStore != null){ 
      rar = RequiredAttributesRule.load(nodeToStore);
    }
  }
  public RequiredAttributesRule getRequiredAttrRule(){
    if(rar == null)
      rar = new RequiredAttributesRule();
    return rar;
  }
  public void setRequiredAttrRule(RequiredAttributesRule r){
    rar = r;
  }
  public CheckerRulesHolder getCheckerRules() {
    if(crh == null)
      crh = new CheckerRulesHolder(nodeToStore);
    return crh;
    
  }
  public IReferenceVersionRule getReferenceVersionRule() {
    return rvr;
  }
}
