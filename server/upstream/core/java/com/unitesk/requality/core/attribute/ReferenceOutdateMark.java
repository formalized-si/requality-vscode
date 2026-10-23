package com.unitesk.requality.core.attribute;

import java.util.UUID;

import com.unitesk.requality.core.checker.ILocalizedStringSource;
import com.unitesk.requality.core.checker.ITreeCheckerProblem;
import com.unitesk.requality.core.checker.IWarningMark;
import com.unitesk.requality.core.localization.Localization;
import com.unitesk.requality.core.localization.StringsSource;

public class ReferenceOutdateMark implements ITreeCheckerProblem, ILocalizedStringSource, IWarningMark {
  private static String ID_OUTDATED_REF = "problem_ref_outdated";
  private static String OUTDATED_DEF_MESSAGE = "Reference '%s' from ‘%s’ reffers to outdated version (%s) of ‘%s’(%s)";
  public static final String WARNING_KEY = "_attributeWarningsList";
  public static final String TYPE = "requality.outdate.reference";
  String p1, p2;
  UUID node, targ;
  String key, oldVers, newVers, nodeS, targS;
  String[] addData = null;
  public ReferenceOutdateMark(String p1, UUID node, String p2, UUID targ, String nodeS, String targS, String key, String oldVers, String newVers){
    this.p1 = p1;
    this.p2 = p2;
    this.node = node;
    this.targ = targ;
    this.nodeS = nodeS;
    this.targS = targS;
    this.oldVers = oldVers;
    this.newVers = newVers;
    this.key = key;
  }
  
  @Override
  public String getDescription() {
    return getLocalizedString();
  }

  @Override
  public String toString(){
    return getLocalizedString();
  }

  public String getProjTo() {
    return p2;
  }
  public String getProjFrom() {
    return p1;
  }
  public UUID getNodeTo() {
    return targ;
  }
  public UUID getNodeFrom() {
    return node;
  }

  public String getNodeToString() {
    return targS;
  }
  public String getNodeFromString() {
    return nodeS;
  }

  public String getAKey() {
    return key;
  }
  public String getKey() {
    return key;
  }

  public String getOldVersion() {
    return oldVers;
  }
  public String getNewVersion() {
    return newVers;
  }

  public String getType() {
    return TYPE;
  }
  public int compareTo(Object arg0r) {
	if(arg0r == null)
		return 1;
	if(!(arg0r instanceof ReferenceOutdateMark))
		return arg0r.toString().compareTo(this.toString());
	ReferenceOutdateMark arg0 = (ReferenceOutdateMark) arg0r;
    if(nodeS.equals(arg0.nodeS))
    {
      if(key.equals(arg0.key))
        return targS.compareTo(arg0.targS);
      else
        return key.compareTo(arg0.key);
    }
    return nodeS.compareTo(arg0.nodeS);
  }

  public boolean isCP() {
    return !p1.equals(p2);
  }

  @Override
  public Object[] getTargets() {
    return new Object[]{node, targ};
  }

  @Override
  public String getCheckerMessageId() {
    return ID_OUTDATED_REF;
  }
  @Override
  public String[] getAdditionalData() {
    if(addData == null)
      addData = new String[] {key, nodeS, oldVers, targS, newVers};
    return addData;
  }

  @Override
  public String getLocalizedString() {
	return Localization.getLocalizedString(getLocalizationCategory(), 
			ID_OUTDATED_REF, OUTDATED_DEF_MESSAGE, getAdditionalData());
  }

  @Override
  public StringsSource getLocalizationCategory() {
    return StringsSource.ERROR;
  }
}
