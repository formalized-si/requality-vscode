package com.unitesk.requality.core.service;

public class ServiceStatus {
  public String port;
  public String status;
  public PortStatus portState;
  public ServiceStatus(String port, String status, PortStatus portState) {
    this.port = port;
    this.status = status;
    this.portState = portState;
  }
}
