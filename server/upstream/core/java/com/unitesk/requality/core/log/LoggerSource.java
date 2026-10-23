package com.unitesk.requality.core.log;

public class LoggerSource {
  private static RLogger rl = new RLogger();
  public RLogger getLogger(){
    return rl;
  }
}
