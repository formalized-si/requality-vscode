package com.unitesk.requality.eclipse.handlers.location;

import java.util.List;
import java.util.Map;
import org.eclipse.swt.widgets.Control;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;

public interface ILocationTab {
  Map<Location, List<Requirement>> getLocationsAndRequirements();

  void refreshLocations();

  Control getControl();
}
