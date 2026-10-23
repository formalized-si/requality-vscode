package com.unitesk.requality.core.operation.mass;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.RequiredAttributesRule;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class SetRequiredAttributes extends AbstractNodeOperation {

  private RequiredAttributesRule rule;

  public SetRequiredAttributes() {
    super("Set values of required attributes");
    
    // TODO Auto-generated constructor stub
  }

  @Override
  public String getName() {
    return "Required attributes setter";
  }

  @Override
  public boolean execute(TreeNode root) {
    rule = root.getTreeDB().
        getAttributeRules().getRequiredAttrRule();
    return super.execute(root);
  }
  
  public void performAction(TreeNode root) {
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(root, true, true, false);
    for (Iterator<TreeNode> i = dftw.iterator(); i.hasNext(); ) {
      TreeNode tn = i.next();
      Map<String, AttributeDefinition> map = rule.getMap().get(tn.getType());
      boolean changed = false;
      if(map!=null)
      {
        for(Entry<String, AttributeDefinition> en:map.entrySet()){
          Attribute old = tn.attrs().getAttribute(en.getKey());
          AttributeDefinition ad = en.getValue();
          if(old==null){
            changed = true;
            tn.attrs().setAttribute(new AttributeDefinition(tn.getUUId(), 
                en.getKey(), AttributeDefinition.detectValueProvider(en.getKey(),
                    ad.getType(), ad.getRawValue().getValue(),
                    TempFolderFilesProvider.getNewNotInited(ad.getType()))));
          }
        }
      }
      if(changed)
        tn.commitAttributes();
    }
  }

  @Override
  public void applySettings(Map<String, String> args) {
    // TODO Auto-generated method stub

  }
}
