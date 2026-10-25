package com.unitesk.requality.core.operation;

public class NullProgressMonitor implements IProgressMonitor {

  public static final IProgressMonitor ONE = new NullProgressMonitor();

  @Override
  public void worked(int val) {
    // TODO Auto-generated method stub

  }

  @Override
  public void doneAll() {
    // TODO Auto-generated method stub

  }


  @Override
  public boolean isCanceled() {
    // TODO Auto-generated method stub
    return false;
  }


  @Override
  public int getRest() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int getMax() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public void beginTask(String string, int taskMax) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void doneTask() {
    // TODO Auto-generated method stub
  }

  @Override
  public void cancel() {
	// TODO Auto-generated method stub
  }

  @Override
  public String getTask() {
	// TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean isDBLocal() {
	// TODO Auto-generated method stub
    return false;
  }

}
