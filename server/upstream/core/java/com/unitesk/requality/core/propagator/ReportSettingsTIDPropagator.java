package com.unitesk.requality.core.propagator;

import com.unitesk.requality.core.api.IAttributeChangePropagator;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplatesManager;

public class ReportSettingsTIDPropagator implements IAttributeChangePropagator {

  @Override
  public boolean shouldBeApplied(
      TreeNode node, ChangesSet<Attribute> cs, Moment m) {
    if(m==Moment.AFTER)
      return false;
    if(node.getType().equals(ReportSettings.TYPE_NAME) && cs.getAll().contains(ReportSettings.ATTR_TEMPLATE) && !cs.getAll().contains(TemplatesManager.ATTR_TEMPLATE_STORE))
      return true;
    return false;
  }

  @Override
  public boolean apply(TreeNode node, ChangesSet<Attribute> changes, Moment m) {
    if(node == null || !(node instanceof ReportSettings))
      return false;
    Attribute adn = changes.getNew(ReportSettings.ATTR_TEMPLATE);
    ReportSettings rnode = (ReportSettings) node;
    TreeDB db = node.getTreeDB();
    String newTempl = (adn == null || adn.getValue() == null)?TemplatesManager.DEF_GROUP:adn.getGValue();
    Template talt =  TemplatesManager.getOne(db).get(newTempl);
    String lastGen = node.attrs().getStringAttributeValue(ReportSettings.ATTR_LAST_GENERATED);
    adn = changes.getNew(ReportSettings.ATTR_LAST_GENERATED);
    if(adn!=null && adn.getValue()!=null)
      lastGen = adn.getGValue();
    TreeNode rootReq = node.attrs().getTreeNodeValue(ReportData.ATTR_FIRST_MROOT);
    String altString = TemplatesManager.getOne(db).getReportTemplateString(talt, (Requirement) rootReq ,rnode, lastGen);
    String oldVal = node.attrs().getStringAttributeValue(TemplatesManager.ATTR_TEMPLATE_STORE, "");
    if(!altString.equals(oldVal)) { 
      node.attrs().setConstAttribute(TemplatesManager.ATTR_TEMPLATE_STORE, altString, Flag.HIDDEN);
      node.commitAttributes();//Replace by common handling of true rets
      return true;
    }
    return false;
  }
  static IAttributeChangePropagator one = new ReportSettingsTIDPropagator();
  public static IAttributeChangePropagator getOne() {
    return one;
  }

  @Override
  public int getPriority() {
    return LOW;
  }

  @Override
  public void propagatorInited(TreeNode node) {
    // TODO locations are not inited and label are in not inited state so this is not needed
  }
}
