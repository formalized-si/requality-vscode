package com.unitesk.requality.vscode.ui.workbench.progress;

import com.unitesk.requality.vscode.ui.runtime.QualifiedName;

/** {@code org.eclipse.ui.progress.IProgressConstants}: the job properties the progress UI reads. */
public interface IProgressConstants {
  /** {@code IProgressConstants.PROPERTY_PREFIX}. */
  String PROPERTY_PREFIX = "org.eclipse.ui.workbench.progress";
  /** Keeps the job in the Progress view after it finished (also kept when it finished with an error). */
  QualifiedName KEEP_PROPERTY = new QualifiedName(PROPERTY_PREFIX, "keep");
  /**
   * Keeps the job in the Progress view after it finished; when it starts or finishes, the other kept jobs that belong
   * to it ({@code job.belongsTo(this)}) are removed from the view.
   */
  QualifiedName KEEPONE_PROPERTY = new QualifiedName(PROPERTY_PREFIX, "keepone");
  /** The image of the job in the Progress view: an {@code ImageDescriptor} or a {@code java.net.URL}. */
  QualifiedName ICON_PROPERTY = new QualifiedName(PROPERTY_PREFIX, "icon");
  /** Whether the job is shown in its progress dialog (set while the dialog is open, false after Run in Background). */
  QualifiedName PROPERTY_IN_DIALOG = new QualifiedName(PROPERTY_PREFIX, "inDialog");
}
