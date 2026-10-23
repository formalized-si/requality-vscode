package com.unitesk.requality.core.checker;

import java.util.List;
import java.util.Map.Entry;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;

public class NodeAttribChecker implements INodeChecker {

  public NodeAttribChecker() {}

  @Override
  public boolean check(TreeNode node, List<IMark> result) {
    // Set<String> attrs = node.getAttributes().getAttributeKeys();
    if (node.isRoot()) // ignore attrs stored in root
    return true;
    for (Entry<String, Attribute> en : node.attrs().getLMap().entrySet()) {
      String key = en.getKey();
      Attribute attr = en.getValue();
      if (attr == null) {
        result.add(new CheckerProblem(
                node, "problem_attribute_value_null", "Attribute \"%s\" value is null", new String[] {key}));
        continue;
      }
      if (!Attribute.checkNameIsValid(key, true)) {
        result.add(new CheckerProblem(
                node, "problem_attribute_incorrect_key", "Attribute \"%s\" has incorrect key", new String[] {key}));
        continue;
      }
      String value = null;
      TypedValue tv = attr.getTValue();
      Object val = tv.getValue();
      if (val == null) {
        if (node.getUUId().equals(attr.getOrigin()))
          result.add(new CheckerProblem(
                  node, "problem_attribute_value_null", "Attribute \"%s\" value is null", new String[] {key}));
        continue;
      }
      
      //String value = attr.getRawValue().toString();
      // TODO check another non-result problems
      switch (tv.getType()) {
        case INT:
          value = val.toString();
          try {
            Integer.parseInt(value);
          } catch (NumberFormatException e) {
            result.add(
                new CheckerProblem(
                    node,
                    "problem_attribute_wrong_type", 
                    "Attribute '%s' of type %s has wrong value: '%s'",
                    new String [] {key, AttributeType.INT.toString(), value}));
          }
          break;
        case FLOAT:
          value = val.toString();
          try {
            Float.parseFloat(value);
          } catch (NumberFormatException e) {
            result.add(
              new CheckerProblem(
                  node,
                  "problem_attribute_wrong_type", 
                  "Attribute '%s' of type %s has wrong value: '%s'",
                  new String [] {key, AttributeType.FLOAT.toString(), value}));
          }
          break;
        case BOOL:
          value = val.toString();
          if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            result.add(
              new CheckerProblem(
                node,
                "problem_attribute_wrong_type", 
                "Attribute '%s' of type %s has wrong value: '%s'",
                new String [] {key, AttributeType.BOOL.toString(), value}));
          }
          break;
        case STRING:
          // TODO add string check?
          // if(!Attribute.checkNameIsValid(key, true))
          // result.add(new CheckerProblem(node, "Attribute \"" + key +
          // "\" of type STRING has wrong value: \"" + value + "\""));
        default:
          break;
      }
    }
    return true;
  }
}
