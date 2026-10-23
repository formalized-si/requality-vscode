package com.unitesk.requality.core.operation.mass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import com.unitesk.requality.core.attribute.AppliedEnumValue;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.EnumDefinitionValue;
import com.unitesk.requality.core.attribute.EnumValue;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.valueprovider.ReferenceValueProvider;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;

public class LibreOfficeAfterProcessing extends AbstractMassOperation {
  int step = 0;
  Set<TreeNode> tosave = new HashSet<>();
  public StringBuilder messages;

  public LibreOfficeAfterProcessing() {
    super("LibreAfterProc");
    messages = new StringBuilder();
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    return "After-import processing for libreoffice html documents";
  }

  @Override
  public boolean execute(TreeNode root) {
    step = 0;
    TreeNode store = root.getTreeDB().getRootNode();
    Attribute prim = store.attrs().getAttribute("Производное");
    if (prim == null) {
      List<EnumValue> lv = new ArrayList<>();
      lv.add(new EnumValue(" ", null));
      lv.add(new EnumValue("Пр", null));
      EnumDefinitionValue edv = new EnumDefinitionValue(AttributeType.STRING, lv);
      store.attrs().setConstAttribute("Производное", edv,0);
      store.commitAttributes();
    }
    root.getTreeDB().clearTagsCache();
    root = root.getTreeDB().getNode(Requirement.getTypeRootQId());
    boolean b = super.execute(root);
    if (!b) return false;
    for (TreeNode node : tosave) node.commitAttributes();
    tosave.clear();
    return b;
  }

  @Override
  public void performSingleAction(TreeNode node) {
    if (node.isDisposed()) // removed;
    return;
    if (step == 0) {
      boolean ch = false;
      for (Entry<String, Attribute> en : node.attrs().getLocalAttributes().entrySet()) {
        Attribute a = en.getValue();
        if (a == null || !a.getOrigin().equals(node.getUUId())) continue;
        String key = a.getKey().toLowerCase();
        if (a.getListType() == AttributeType.REFERENCE || a.getType() == AttributeType.REFERENCE)
          ch = true;
        else if (key.toLowerCase().equals("ref")
            || key.toLowerCase().endsWith("-ref")
            || key.toLowerCase().equals("refines")
            || key.toLowerCase().startsWith("relates-")) {
          ch = true;
          if (a.getType() == AttributeType.LIST) {
            if (a.getListType() == null || a.getListType() != AttributeType.REFERENCE) {
              List<Object> refs = ((ListValue)a.getGValue()).getValues();
              node.attrs()
              .setAttribute(new AttributeDefinition(node.getUUId(), en.getKey(),
                  new ReferenceValueProvider(AttributeType.LIST, new ListValue(AttributeType.REFERENCE, refs)),
                      AttributeDefinition.detectFlags(en.getKey())));
              ch = true;
            }
          } else {
            if (a.getType() != AttributeType.REFERENCE) {
              node.attrs()
              .setAttribute(new AttributeDefinition(node.getUUId(),
                  en.getKey(), new ReferenceValueProvider(AttributeType.REFERENCE, Attribute.valueToString(node.attrs(),a.getType(),a.getValue().toString())), 
                      AttributeDefinition.detectFlags(en.getKey())));
              ch = true;
            }
          }
        }
      }

      String v = "Пр";

      Attribute a = node.attrs().getAttribute("Производное");
      Object ao = a == null ? null : a.getValue();
      if (ao == null
          || !ao.toString().toLowerCase().trim().equals("true")
              && !ao.toString().toLowerCase().trim().equals("пр")) v = " ";
      AppliedEnumValue aev = new AppliedEnumValue(v, null, AttributeType.STRING, "Производное");
      if (ao == null || !ao.toString().trim().toLowerCase().equals(v.toLowerCase())) {
        if (!aev.equals(ao)) {
          node.attrs().setConstAttribute("Производное", aev,0);
          tosave.add(node);
          ch = true;
        }
      }
      // Attribute term =
      // node.attrs().getAttribute(UpdateFromHTMLModule.DEF_TERM);
      String desc = node.getRawDescription();
      if (desc != null) {
        desc = CoreUtils.reworkLibreDesc(node, desc, false, tosave, null);
      }
      if (ch) node.commitAttributes();
    } 
  }


  @Override
  public void applySettings(Map<String, String> args) {
    // TODO Auto-generated method stub

  }
}
