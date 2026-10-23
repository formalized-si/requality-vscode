package com.unitesk.requality.eclipse.handlers.create;

import org.eclipse.core.commands.AbstractHandler;

public abstract class AHotKeyCommand extends AbstractHandler {
  static Runnable runnable;
  static Runnable runnable2;//TODO mb more proper way
  public void setActionsOnCompleted(Runnable r) {
    runnable = r;
  }
  public void setActionsBeforeRun(Runnable r) {
    runnable2 = r;
  }
  public void runActionsOnCompleted() {
    if(runnable!=null)
      runnable.run();
  }
  public void runActionsBeforeRun() {
    if(runnable2!=null)
    	runnable2.run();
  }
}