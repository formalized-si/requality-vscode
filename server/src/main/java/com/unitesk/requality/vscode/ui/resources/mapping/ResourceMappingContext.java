package com.unitesk.requality.vscode.ui.resources.mapping;

/** {@code org.eclipse.core.resources.mapping.ResourceMappingContext}: the context of a resource mapping query. */
public interface ResourceMappingContext {
  /** The context for local (workspace) queries. */
  ResourceMappingContext LOCAL_CONTEXT = new ResourceMappingContext() {
    @Override
    public boolean isThreeWay() {
      return false;
    }
  };

  /** Whether the context compares the local state with a remote one. */
  boolean isThreeWay();
}
