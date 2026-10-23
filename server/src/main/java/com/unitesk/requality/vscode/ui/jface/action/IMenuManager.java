package com.unitesk.requality.vscode.ui.jface.action;

/** {@code org.eclipse.jface.action.IMenuManager}. */
public interface IMenuManager extends IContributionItem {
  void add(IAction action);

  void add(IContributionItem item);

  void removeAll();

  void addMenuListener(IMenuListener listener);

  void setRemoveAllWhenShown(boolean removeAll);

  IContributionItem[] getItems();

  /** The item with the id, or null. */
  IContributionItem find(String id);

  /** Adds the action at the end of the group (before the next group marker); the group must exist. */
  void appendToGroup(String groupName, IAction action);

  void appendToGroup(String groupName, IContributionItem item);

  /** Adds the item right after the group marker; the group must exist. */
  void prependToGroup(String groupName, IAction action);

  void prependToGroup(String groupName, IContributionItem item);

  void insertAfter(String id, IAction action);

  void insertAfter(String id, IContributionItem item);

  void insertBefore(String id, IAction action);

  void insertBefore(String id, IContributionItem item);
}
