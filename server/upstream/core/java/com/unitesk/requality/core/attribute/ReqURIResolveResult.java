package com.unitesk.requality.core.attribute;

public class ReqURIResolveResult {
  public static final ReqURIResolveResult NF = new ReqURIResolveResult(null, null, true);
  public String value, link;
  public boolean not_found;
  public ReqURIResolveResult(String l, String v, boolean nf){
    value = v;
    link = l;
    not_found = nf;
  }
}
