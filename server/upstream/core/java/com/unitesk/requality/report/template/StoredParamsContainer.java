package com.unitesk.requality.report.template;

public class StoredParamsContainer implements IParamsProvider{
  public enum Condition{OR, AND, WITHOUT}
  public StoredParamsContainer(Condition c) {
    this.cond = c;
  }
  public Condition cond = Condition.WITHOUT;
  public IParamsProvider[] stored;//StoredParamsContainer or StoredParamDefinition
  public void addParamDefinition(StoredParamDefinition spd) {
	  IParamsProvider[]st = null;
    int i = 0;
    if(stored != null) {
      st = new IParamsProvider[stored.length+1];
      for(IParamsProvider o:stored)
        st[i++] = o;
      stored = st;
    }else
      stored = new IParamsProvider[1];
    stored[i++] = spd;
  }
  public void addParamContainer(StoredParamsContainer spd) {
    IParamsProvider[]st = null;
    int i = 0;
    if(stored != null) {
      st = new IParamsProvider[stored.length+1];
      for(IParamsProvider o:stored)
        st[i++] = o;
      stored = st;
    }else
    	stored = new IParamsProvider[1];
    stored[i++] = spd;
  }
  @Override
  public boolean isContainer() {
    return true;
  }
  @Override
  public IParamsProvider[] getProviders() {
    return stored;
  }
}
