package com.unitesk.requality.core.tree;

public enum TreeState {
  //NOT LOADED - new treedb to be loaded
  //LOADING - loading all main information and start initing cache
  //READY_NO_CACHE - Tree is loaded but not all caches are inited
  //READY - tree is loaded and cache is inited
  //MODIFYING
  NOT_LOADED, LOADING, LOADED_NO_CACHE, LOADED, READY, MODIFYING, DISPOSED, RESTART, HALTED, CANCELED;
  static TreeState[]ALL_STATES = TreeState.values();
  public static boolean readyState(TreeState ts) {//TODO rename
    return ts == TreeState.LOADED || ts == TreeState.READY;
  }

  public static boolean isDisposed(TreeState ts) {
	return ts == TreeState.DISPOSED || ts == TreeState.HALTED || ts == TreeState.CANCELED;
  }

  public static boolean isIncorrectProjectState(TreeState ts) {
	return ts != TreeState.RESTART &&ts != TreeState.READY && ts != TreeState.LOADING && ts != TreeState.DISPOSED && ts!= TreeState.CANCELED && ts != TreeState.NOT_LOADED && ts!= TreeState.HALTED;
  }

  public static boolean isProjectLoadable(TreeState ts) {
    return ts == TreeState.RESTART ||ts == TreeState.NOT_LOADED || ts == TreeState.DISPOSED || ts == TreeState.CANCELED || ts == TreeState.HALTED;
  }
}
