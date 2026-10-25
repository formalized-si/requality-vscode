package com.unitesk.requality.core.checker;

import java.util.UUID;

import com.unitesk.requality.core.localization.Localization;
import com.unitesk.requality.core.localization.StringsSource;
import com.unitesk.requality.core.model.TreeNode;

/** Class represents problems over Tree Nodes */
public class StorageProblem implements INodeMark, ILocalizedStringSource {
  public static String ERROR_ID_UUID_PARENT_DUP="problem_uuid_duplication_parent";
  public static String ERROR_ID_UUID_FIRST_DUP="problem_uuid_duplication_error_first_node";
  public static String ERROR_ID_UUID_DUP="problem_uuid_duplication_error_node";
  public static final String MESSAGE_UUID_PARENT_DUP = "The child uuid '%s' is duplicated on paths '%s' and '%s' for parent '%s'  (Temporary uuid '%s' was given for current node)";
  public static final String MESSAGE_UUID_FIRST_DUP = "The uuid '%s' is the same with node '%s'";
  public static final String MESSAGE_UUID_DUP = "The uuid '%s' is the same with node '%s' (Temporary uuid '%s' was given for current node)";
  private UUID target;
  private String description;
  private String problemId;
  private String[] addData;

  public StorageProblem(UUID target, String problemId, String description,  String ... addData) {
    this.target = target;
    this.description = description;
    this.problemId = problemId;
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
    return problemId;
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
    if(o == null || !(o instanceof StorageProblem))
      return -1;
    StorageProblem cp = ((StorageProblem)o);
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
	return Localization.getLocalizedString(getLocalizationCategory(), problemId, description, addData);
  }

  @Override
  public StringsSource getLocalizationCategory() {
    return StringsSource.ERROR;
  }
}
