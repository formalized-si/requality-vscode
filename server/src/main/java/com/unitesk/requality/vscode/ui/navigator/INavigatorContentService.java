package com.unitesk.requality.vscode.ui.navigator;

/** {@code org.eclipse.ui.navigator.INavigatorContentService}: the content service of a common viewer. */
public interface INavigatorContentService {
  String getViewerId();

  void dispose();

  /** The common filters of the viewer (null when the viewer has none). */
  default INavigatorFilterService getFilterService() {
    return null;
  }

  /** The content extensions bound to the viewer. */
  default INavigatorContentDescriptor[] getVisibleExtensions() {
    return new INavigatorContentDescriptor[0];
  }

  /** Whether the content extension is active. */
  default boolean isActive(String extensionId) {
    return true;
  }

  /** Activates exactly these content extensions (the others are deactivated) and refreshes the viewer. */
  default void activateExtensions(String[] extensionIds) {
  }

  /** The activation of the content extensions (null when not supported). */
  default INavigatorActivationService getActivationService() {
    return null;
  }

  /** The state model shared by the parts of a content extension. */
  default IExtensionStateModel findStateModel(String anExtensionId) {
    return null;
  }
}
