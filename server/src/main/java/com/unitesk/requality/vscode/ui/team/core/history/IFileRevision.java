package com.unitesk.requality.vscode.ui.team.core.history;

import java.net.URI;

import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/** {@code org.eclipse.team.core.history.IFileRevision}: an individual revision of a file. */
public interface IFileRevision {
  /** The storage of this revision; an {@code IFile} is the current state of the workspace file. */
  IStorage getStorage(IProgressMonitor monitor) throws CoreException;

  /** The name of the file of this revision. */
  String getName();

  /** The URI of the file of this revision, or null. */
  URI getURI();

  /** The time stamp of this revision in milliseconds, or -1 when unknown. */
  long getTimestamp();

  /** Whether the file of this revision exists. */
  boolean exists();

  /** The unique identifier of this revision, or null (the time stamp then tells the revisions apart). */
  String getContentIdentifier();

  /** The author of this revision, or null. */
  String getAuthor();

  /** The comment of this revision, or null. */
  String getComment();

  /** The branches of this revision (empty when none). */
  ITag[] getBranches();

  /** The tags of this revision (empty when none). */
  ITag[] getTags();

  /** Whether a supported property of this revision is missing. */
  boolean isPropertyMissing();

  /** A revision with all supported properties present, or null. */
  IFileRevision withAllProperties(IProgressMonitor monitor) throws CoreException;
}
