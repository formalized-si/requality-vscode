package com.unitesk.requality.core.checker;

import com.unitesk.requality.core.localization.Localization;
import com.unitesk.requality.core.localization.StringsSource;
import com.unitesk.requality.core.model.TreeNode;

/** Class represents problems over Tree Nodes */
public class CheckerWarning implements INodeMark, ILocalizedStringSource {
  private TreeNode target;
  private String description;
  private String checkerMessageId;
  private String[] addData;

  public CheckerWarning(TreeNode target, String checkerMessageId, String description, String[]addData) {
    this.target = target;
    this.description = description;
    this.checkerMessageId = checkerMessageId;
    this.addData = addData;
  }

  /*
   * (non-Javadoc)
   *
   * @see com.unitesk.requality.core.checkers.IMark#getDescription()
   */
  public String[] getAdditionalData() {
    return addData;
  }
  public String getCheckerMessageId() {
    return checkerMessageId;
  }
  @Override
  public String getDescription() {
    return getLocalizedString();
  }

  /*
   * (non-Javadoc)
   *
   * @see com.unitesk.requality.core.checkers.IMark#getTarget()
   */
  @Override
  public Object getTarget() {
    return target;
  }

  @Override
  public String toString() {
    return target.toString() + ": " + description;
  }
  @Override
  public int compareTo(Object o) {
    if(o == null || !(o instanceof CheckerWarning))
      return -1;
    CheckerWarning cp = ((CheckerWarning)o);
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
    return StringsSource.WARNING;
  }
}
