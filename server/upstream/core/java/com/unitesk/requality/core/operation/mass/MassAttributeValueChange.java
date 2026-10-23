package com.unitesk.requality.core.operation.mass;

import java.util.Map;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.HtmlValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.attribute.valueprovider.TemplValueProvider;
import com.unitesk.requality.core.model.TreeNode;

public class MassAttributeValueChange extends AbstractMassOperation {
  private String aname = null;
  private AttributeType atype = AttributeType.STRING;
  private Object newVal;

  public MassAttributeValueChange() {
    super("MassAttributeValueChange");
  }

  @Override
  public void performSingleAction(TreeNode root) {
    Attribute a = root.attrs().getAttribute(aname);
    if (a != null && a.getOrigin().equals(root.getUUId()) && (atype == null || a.getType() == atype)) { // &&
      // root.getUUId().equals(a.getOrigin())){
      //Object val = newVal;
      AttributeType atype = this.atype; 
      if (atype == null) atype = a.getType(); // TODO more proper
      IValueProvider vp = a.getDefinition().getValueProvider();
      boolean changed = false;
      newVal = Attribute.convertValue(root, AttributeType.STRING, atype, newVal, null);
      if(vp.getType().equals(ConstValueProvider.TYPE)){
        vp = new ConstValueProvider(atype, newVal);
        changed = true;
      }
      else
        if(vp.getType().equals(TemplValueProvider.TYPE)){
          vp = new TemplValueProvider(atype, newVal);
          changed = true;
        }
      else
        if(vp.getType().equals(HtmlValueProvider.TYPE)){
          vp = new HtmlValueProvider(atype, newVal, TempFolderFilesProvider.getOne(null, false));
          changed = true;
        }
      if(changed){
        root.attrs().setAttribute(new AttributeDefinition(root.getUUId(),aname, a.getAvailability(),vp, a.getDefinition().getFlags()));
        root.commitAttributes();
      }
    }
  }

  @Override
  public String getName() {
    return "Change attribute value of " + aname + "[" + atype + "] to " + newVal;
  }

  @Override
  public void applySettings(Map<String, String> args) {
    String s = args.get(MassOppsArguments.Argument.ATTRIBUTE_TYPE.toString());
    atype = s == null ? null : AttributeType.valueOf(s);
    aname = args.get(MassOppsArguments.Argument.ATTRIBUTE_NAME.toString());
    newVal = args.get(MassOppsArguments.Argument.NEW_VALUE.toString());
  }
}
