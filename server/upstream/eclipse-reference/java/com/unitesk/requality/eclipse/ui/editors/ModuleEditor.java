package com.unitesk.requality.eclipse.ui.editors;

import java.util.HashSet;
import java.util.Set;

import com.unitesk.requality.core.attribute.valueprovider.NumericIdProvider;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.service.ResourcesServlet;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;

public class ModuleEditor extends JSEditor {
  public static Set<String> defaultAttrs = new HashSet<>();
  static{
    defaultAttrs.add(TreeNode.ATTR_ID);
    defaultAttrs.add(TreeNode.ATTR_TYPE);
    defaultAttrs.add(TreeNode.ATTR_DESCRIPTION);
    defaultAttrs.add(Requirement.ATTR_LOCATIONS);
    defaultAttrs.add(TreeNode.ATTR_HIDDEN);
    defaultAttrs.add(TreeNode.ATTR_IGNORE_NAME);
    defaultAttrs.add(TreeNode.ATTR_NAME);
    defaultAttrs.add(Requirement.ATTR_STATUS);
    defaultAttrs.add(TextNode.ATTR_TNTYPE);
    defaultAttrs.add(NumericIdProvider.ATTR_KEY);//num id for module editor
    
    //defaultAttrs.add(TestPurpose.);
  }
  public ModuleEditor() {
    super(ResourcesServlet.MODULEEDITOR, defaultAttrs);
    setTabLabel("ModuleEditor");
  }

  public static final String ID = "com.unitesk.requality.editors.ModuleEditor";

  @Override
  public String getEditorKindId() {
    return ID;
  }
}
