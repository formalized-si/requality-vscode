package com.unitesk.requality.core.api;

import java.util.Collection;
@ApiExtension
public interface IAPISource extends ILightAPISource{
  public Collection<MassOppDescriptor> loadMassOpsDescriptors();

  public void init();
  public void destroy();
}
