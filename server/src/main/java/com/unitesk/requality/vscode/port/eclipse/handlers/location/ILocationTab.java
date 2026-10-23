package com.unitesk.requality.vscode.port.eclipse.handlers.location;

import java.util.List;
import java.util.Map;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;

public interface ILocationTab {
  Map<Location, List<Requirement>> getLocationsAndRequirements();

  void refreshLocations();

  Control getControl();
}
