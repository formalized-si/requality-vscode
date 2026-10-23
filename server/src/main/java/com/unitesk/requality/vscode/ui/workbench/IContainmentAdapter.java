package com.unitesk.requality.vscode.ui.workbench;

/**
 * {@code org.eclipse.ui.IContainmentAdapter}: tests the containment of an element in a model-independent way; the workbench
 * uses it for the working set filters.
 */
public interface IContainmentAdapter {
  /** Checks whether the given element corresponds to the containment context. */
  int CHECK_CONTEXT = 1;

  /** Checks whether the given element corresponds to a direct child of the containment context. */
  int CHECK_IF_CHILD = 2;

  /** Checks whether the given element corresponds to an ancestor of the containment context. */
  int CHECK_IF_ANCESTOR = 4;

  /** Checks whether the given element corresponds to a descendant of the containment context. */
  int CHECK_IF_DESCENDANT = 8;

  /** Whether the element is contained in the containment context (or is the context itself) for the flags. */
  boolean contains(Object containmentContext, Object element, int flags);
}
