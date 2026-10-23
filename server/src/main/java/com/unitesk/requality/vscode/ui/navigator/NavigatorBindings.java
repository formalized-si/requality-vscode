package com.unitesk.requality.vscode.ui.navigator;

import java.util.List;
import java.util.regex.Pattern;

/** The {@code viewerContentBinding} patterns of a viewer (shared by the action, filter and content services). */
public final class NavigatorBindings {
  private NavigatorBindings() {
  }

  public static List<Pattern> contentPatterns(String viewerId) {
    return NavigatorActionService.patterns(viewerId, "viewerContentBinding", "contentExtension");
  }

  public static boolean matches(List<Pattern> patterns, String id) {
    return NavigatorActionService.matches(patterns, id);
  }
}
