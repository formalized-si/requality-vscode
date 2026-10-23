package com.unitesk.requality.vscode.ui.team.ui.synchronize;

import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/**
 * {@code org.eclipse.team.ui.synchronize.ISynchronizeParticipant}: a synchronization shown in the Synchronize view, one page
 * of the view. A participant is pinned (kept when the un-pinned synchronizations are removed) or not.
 * VSCODE: the page of a participant is created by the participant itself ({@link #createPage}), as the page book of the
 * view has no page configuration of the platform.
 */
public interface ISynchronizeParticipant {
  /** {@code ISynchronizeParticipant.P_PINNED}: the property of the pinned state. */
  String P_PINNED = "org.eclipse.team.ui.synchronize.pinned";

  /** The name of the synchronization, shown in the title of the view. */
  String getName();

  /** The description of the synchronization, the content description of the view. */
  String getDescription();

  /** Whether the participant is pinned ({@code ISynchronizeParticipant.isPinned}). */
  boolean isPinned();

  /** Pins or un-pins the participant; the property {@link #P_PINNED} changes. */
  void setPinned(boolean pinned);

  /**
   * Whether the participant has unsaved changes ({@code ISynchronizeParticipant}: a dirty saveable of the participant). Removing
   * a dirty participant asks to save its changes. No participant is dirty by default.
   */
  default boolean isDirty() {
    return false;
  }

  /** Saves the unsaved changes of the participant ({@code ISynchronizeParticipant}: saves its dirty saveables); none by default. */
  default void save() {
  }

  /** Creates the page of the participant in the parent (the page book of the Synchronize view). */
  Control createPage(Composite parent);

  /** Releases the resources of the participant (its page is disposed by the view). */
  void dispose();

  /** Adds a listener of the property changes (the pinned state). */
  void addPropertyChangeListener(IPropertyChangeListener listener);

  /** Removes a listener of the property changes. */
  void removePropertyChangeListener(IPropertyChangeListener listener);
}
