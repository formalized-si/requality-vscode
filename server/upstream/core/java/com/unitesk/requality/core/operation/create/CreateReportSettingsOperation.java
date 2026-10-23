package com.unitesk.requality.core.operation.create;

import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplatesManager;

public class CreateReportSettingsOperation extends CreateElementOperation {
  private static CreateElementOperation one = new CreateReportSettingsOperation();
  public static CreateElementOperation getOne() {
    return one;
  }  
  
  @Override
  public String[] getElementTypes() {
    return new String[] {ReportSettings.TYPE_NAME};
  }

  @Override
  protected void setElementSpecificAttributes(TreeNode nn) {
    nn.attrs().setConstAttribute(ReportSettings.ATTR_TEMPLATE, Template.DEFAULT_ID, Flag.HIDDEN);
    TemplatesManager.getOne(nn.getTreeDB()).saveReportSettings((ReportSettings)nn, null, null);
  }
}
