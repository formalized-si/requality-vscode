package com.unitesk.requality.core.service;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class PortStateRegister {
  private  List<IPortStatusProvider> providers = new LinkedList<>();
  private static PortStateRegister one = new PortStateRegister();
  public static PortStateRegister getOne() {
    return one;
  }
  public PortStatus getStatus(int port) {
    for(IPortStatusProvider p: providers)
      if(p.getPort() == port)
        return p.getPortStatus();
    return null;
  }
  public void registerProvider(IPortStatusProvider prov) {
    providers.add(prov);
  }
  public List<IPortStatusProvider> getStatusProviders() {
    return Collections.unmodifiableList(providers);
  }
}
