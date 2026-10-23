package com.unitesk.requality.core.operation;

public interface IProgressMonitor {
  public void worked(int val);

  public void doneAll();

  public int getRest();
  
  public int getMax();
  
  public void beginTask(String string, int taskMax);
  
  public void doneTask();

  public boolean isCanceled();
  
  public void cancel();

  public String getTask();

  public boolean isDBLocal();
}
