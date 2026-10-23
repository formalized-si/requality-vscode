package com.unitesk.requality.core.service;

public interface IPortStatusProvider {
  public int  getPort();
  public String getPortUserInfo();
  public PortStatus getPortStatus();
}
