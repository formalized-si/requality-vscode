package com.unitesk.requality.core;

import java.util.HashSet;
import java.util.Set;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.TextNode;

public class Consts {
  public static byte SHIFT_PRESSED = 0;
  public static final String UniEditorID = "com.unitesk.requality.editors.UniEditor";
  public static final String ReqMarkerID = "com.unitesk.requality.editors.browser.ReqMarker";
  
  public static final String CATALOG_NATURE = "com.unitesk.requality.natures.reqcatalogue";
  public static final String CATALOG_BROKEN_NATURE = "com.unitesk.requality.natures.brokenreqcatalogue";
  
  public static final Set<String> REVIEW_CONSTS = new HashSet<>();
  public static final Set<String> MODULEEDITOR = new HashSet<>();
  public static final Set<String> UNIEDITOR = new HashSet<>();
  public static final String TEMPLATES_SELECTION_DIALOG_NAME = "TemplatesSelectionDialog";
  public static final String EDIT_ELEMENT_DIALOG_NAME = "EditElementDialog";
  public static final String SELECT_TEST_PURPOSE_DIALOG_NAME = "SelectTestPurposeDialog";
  public static final String CONNECTION_LIMIT_DIALOG_NAME = "ConnectionLimitDialog";
  public static final String TEST_DIALOG_PROVIDER_CREATE_NAME = "TestDialogProviderCreate";
  
  public static final String BROKEN_POSTFIX = "broken";
  public static final String LOADING_POSTFIX = "loading...";
  static{
    REVIEW_CONSTS.add(TreeNode.ATTR_ID);
    REVIEW_CONSTS.add(TreeNode.ATTR_TYPE);
    REVIEW_CONSTS.add(TreeNode.ATTR_DESCRIPTION);
    REVIEW_CONSTS.add(Requirement.ATTR_LOCATIONS);
    REVIEW_CONSTS.add(TreeNode.ATTR_HIDDEN);
    REVIEW_CONSTS.add(TreeNode.ATTR_IGNORE_NAME);
    REVIEW_CONSTS.add(TreeNode.ATTR_NAME);
    REVIEW_CONSTS.add(Requirement.ATTR_STATUS);
    REVIEW_CONSTS.add(TestPurpose.ATTR_EXPECTED_RESULTS);
    REVIEW_CONSTS.add(TestPurpose.ATTR_STATUS);
    REVIEW_CONSTS.add(TextNode.ATTR_TNTYPE);
  };
}
