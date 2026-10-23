package com.unitesk.requality.core.storage;

//private Set<String> errorState = new HashSet<>();
  public enum ProjectLoadingState{NOT_LOADING, INITIAL_LOADING, START_TRACKING, VERSION_UPDATE, LOADED;

  public boolean isLoadable() {
    return this == ProjectLoadingState.NOT_LOADING || this == VERSION_UPDATE;
  }
}