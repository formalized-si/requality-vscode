package com.unitesk.requality.vscode.ui.jface.action;

/** {@code org.eclipse.jface.action.GroupMarker}: an invisible named group of a contribution manager. */
public class GroupMarker extends ContributionItem {
  public GroupMarker(String groupName) {
    super(groupName);
  }

  @Override
  public boolean isGroupMarker() {
    return true;
  }

  /** Group markers are always invisible (JFace). */
  @Override
  public boolean isVisible() {
    return false;
  }
}
