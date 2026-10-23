package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.IFilter}. */
@FunctionalInterface
public interface IFilter {
  boolean select(Object toTest);
}
