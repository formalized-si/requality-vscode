package com.unitesk.requality.core.storage.cache;

import java.util.Random;

public class CacheTransactionDescription {
  private static Random rand = new Random();
  public String name;
  public int ac;
  public String error;
  public boolean sucessfull = true;
  public final long session;
  public CacheTransactionDescription(String name, int ac) {
    this.name = name;
    this.ac = ac;
    this.session = rand.nextLong();
  }
  public void error(String err) {
    this.error = err;
    sucessfull = false;
  }
}
