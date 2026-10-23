package com.unitesk.requality.core.fi;

import java.util.List;
import java.util.UUID;

public interface IInspectionResult {
  public UUID getUUId();
  public String getSUUId();
  public List<InspectionStep> getSteps();
}