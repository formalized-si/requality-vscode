package com.unitesk.requality.eclipse.api;

import java.util.List;

import com.unitesk.requality.core.api.MassOppDescriptor;
import com.unitesk.requality.core.operation.mass.AbstractNodeOperation;
import com.unitesk.requality.eclipse.wizards.massops.AbstractExtraSettingsPage;

public abstract class MassOppDescriptorWithPages extends MassOppDescriptor {
  public MassOppDescriptorWithPages(String i, String n, AbstractNodeOperation op, boolean debug) {
    super(i, n, op, debug);
  }

  public abstract List<Class<? extends AbstractExtraSettingsPage>> getPages();
}
