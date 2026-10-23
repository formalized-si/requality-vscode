package com.unitesk.requality.core.service;

public enum PortInitStrategy {
  FIXED, DYNAMIC_ON_OCCUPIED;
  public static PortInitStrategy strategy = PortInitStrategy.FIXED;
}
