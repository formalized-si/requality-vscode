package com.unitesk.requality.eclipse.ui.editors;

import java.util.Set;

import org.eclipse.swt.widgets.Composite;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.service.ResourcesServlet;

public class ReviewEditor extends JSEditor {
  public static final String ID = "com.unitesk.requality.editors.ReviewEditor";
  public static Set<String> defaultAttrs = Consts.REVIEW_CONSTS;
  public ReviewEditor() {
    super(ResourcesServlet.UNIEDITOR,defaultAttrs);
    setTabLabel("Review");
  }

  @Override
  public void createPartControl(Composite parent) {
    super.createPartControl(parent);
    setEditMode(EDIT_MODE_REQ_RO | EDIT_MODE_TESTPURPOSE_RO | EDIT_MODE_TN_RO | EDIT_MODE_TESTPROCEDURE_RO | EDIT_MODE_TESTSTEP_RO | EDIT_MODE_NOT_EDIT_VIRTUAL);
  }

  @Override
  public String getEditorKindId() {
    return ID;
  }
}
