package com.unitesk.requality.core.checker;

import com.unitesk.requality.core.localization.Localization;
import com.unitesk.requality.core.localization.StringsSource;
import com.unitesk.requality.core.model.TreeNode;

/** Class represents problems over Tree Nodes */
public class CheckerProblem implements INodeMark, ILocalizedStringSource {
  private TreeNode target;
  private String description;
  private String checkerMessageId;
  private String[] addData;

  public CheckerProblem(TreeNode target, String checkerMessageId, String description, String[]addData) {
    this.target = target;
    this.checkerMessageId = checkerMessageId;
    this.description = description;
    this.addData = addData;
  }

  /**
   * Get description of problem
   *
   * @return text of problems description
   */
  public String getDescription() {
    return getLocalizedString();
  }

  /**
   * Get node where problem appears
   *
   * @return tree node
   */
  public Object getTarget() {
    return target;
  }

  @Override
  public String toString() {
    return target.toString() + ": " + getDescription();
  }

  @Override
  public int compareTo(Object o) {
    if(o == null || !(o instanceof CheckerProblem))
      return -1;
    CheckerProblem cp = ((CheckerProblem)o);
    TreeNode trg = ((TreeNode)this.getTarget()),
    		trg2=(TreeNode)cp.getTarget();
    if(trg == null)
    	return trg2==null?0:-1;
    if(trg2==null)
    	return 1;
    int c = trg.compareTo(trg2);
    if(c != 0)
    	return c;
    if(description == null) 
    	return cp.description == null ? 0 : -1;
    if(cp.description == null)
    	return 1;
    return description.compareTo(cp.description);
  }

  @Override
  public String getLocalizedString() {
	return Localization.getLocalizedString(getLocalizationCategory(), checkerMessageId, description, addData);
  }

  @Override
  public StringsSource getLocalizationCategory() {
    return StringsSource.ERROR;
  }

  @Override
  public String[] getAdditionalData() {
    return addData;
  }

  @Override
  public String getCheckerMessageId() {
    return checkerMessageId;
  }
}
