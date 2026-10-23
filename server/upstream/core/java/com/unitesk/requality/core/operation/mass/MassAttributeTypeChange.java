package com.unitesk.requality.core.operation.mass;

import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;

public class MassAttributeTypeChange extends AbstractMassOperation {
  private String aname = null;
  private String newAName = null;
  private AttributeType atype = null;

  public MassAttributeTypeChange() {
    super(null);
  }

  public MassAttributeTypeChange(String attrName, String newAttrName, AttributeType at) {
    this();
    Map<String, String> map = new HashMap<>();
    map.put(MassOppsArguments.Argument.NEW_ATTRIBUTE_NAME.name(), newAttrName);
    map.put(MassOppsArguments.Argument.ATTRIBUTE_NAME.name(), attrName);
    map.put(MassOppsArguments.Argument.ATTRIBUTE_TYPE.name(), at.name());
    applySettings(map);
  }

  public void performSingleAction(TreeNode root) {
    Attribute a = root.attrs().getAttribute(aname);
   
    AttributeType atype = this.atype;
    if (a != null) {
      if(!a.getOrigin().equals(root.getUUId()))
        return;
      AttributeDefinition newA;
      boolean changes = false;
      String key = a.getKey();
      if (newAName == null || newAName.isEmpty() || newAName.equals(aname)) newA = null;
      else {
        key = newAName;
        newA = new AttributeDefinition(a.getOrigin(), newAName, a.getAvailability(),
            a.getDefinition().getValueProvider(),a.getDefinition().getFlags());
//            root.attrs()
//                .createAttribute(
//                    a.getOrigin(),
//                    a.getRawType(),
//                    newAName,
//                    null,
//                    a.getRawValue(),
//                    a.getGenerator());
//        newA.setAvailability(a.getAvailability());
        root.attrs().removeAttribute(aname);
        root.attrs().setAttribute(newA);
        changes = true;
      }
      if(atype == null)
        atype = a.getType();
      if (a.getType() != atype) { // &&
        
        // root.getUUId().equals(a.getOrigin())){
        Object val = null;
        try {
          val =
              Attribute.convertValue(
                  root, a.getType(), atype, a.getValue(), null);
        } catch (Exception e) {
          e.printStackTrace();
        }
        if (val == null) {
          // TODO error
          RLogger.logError(
              "Cannot convert '"
                  + aname
                  + "' on '"
                  + root.getUserVisibleName(true)
                  + "' to "
                  + atype);
          return;
        }
        root.attrs().setAttribute(new AttributeDefinition(a.getOrigin(), key, a.getAvailability(),
          AttributeDefinition.detectValueProvider(key, atype, val, TempFolderFilesProvider.getNewNotInited(atype)),
            a.getDefinition().getFlags()));
        //root.attrs().setAttribute(a.getOrigin(), atype, newA.getKey(), val);
        changes = true;
      }
      if (changes) root.commitAttributes();
    }
  }

  @Override
  public String getName() {
    return "Change attribute type of " + aname + " to " + atype;
  }

  @Override
  public void applySettings(Map<String, String> args) {
    String at = args.get(MassOppsArguments.Argument.ATTRIBUTE_TYPE.toString());
    if(at!=null && !at.isEmpty())
      atype = AttributeType.valueOf(at);
    aname = args.get(MassOppsArguments.Argument.ATTRIBUTE_NAME.toString());
    newAName = args.get(MassOppsArguments.Argument.NEW_ATTRIBUTE_NAME.toString());
  }
}
