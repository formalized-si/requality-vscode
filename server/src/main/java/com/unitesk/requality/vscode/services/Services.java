package com.unitesk.requality.vscode.services;

import java.util.Arrays;
import java.util.List;

import com.unitesk.requality.vscode.RequalityServer;

/**
 * All feature services. Each service registers its RPC methods (and core hooks) in {@link Service#register}.
 * To add a feature, create a {@link Service} implementation and append it here.
 */
public final class Services {
  private Services() {
  }

  public interface Service {
    void register(RequalityServer server);
  }

  public static List<Service> all() {
    return Arrays.asList(
        new WorkspaceService(),
        new ExplorerService(),
        new NodesService(),
        new UiService(),
        new SelectionService(),
        new PropertiesService(),
        new CommandService(),
        new MenuService(),
        new WizardService(),
        new PreferencesService(),
        new ProblemMarkers(),
        new ViewsService(),
        new CompareService());
  }

  public static void registerAll(RequalityServer server) {
    for (Service s : all())
      s.register(server);
  }
}
