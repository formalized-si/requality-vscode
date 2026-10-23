package com.unitesk.requality.vscode.ui.jface.action;

/** {@code org.eclipse.jface.action.ContributionItem}. */
public abstract class ContributionItem implements IContributionItem {
  private String id;
  private boolean visible = true;

  protected ContributionItem() {
    this(null);
  }

  protected ContributionItem(String id) {
    this.id = id;
  }

  @Override
  public String getId() {
    return id;
  }

  public void setId(String itemId) {
    id = itemId;
  }

  @Override
  public boolean isVisible() {
    return visible;
  }

  /** {@code org.eclipse.jface.action.ContributionItem#isEnabled}: items are enabled unless a subclass says otherwise. */
  public boolean isEnabled() {
    return true;
  }

  public void setVisible(boolean b) {
    visible = b;
  }
}
