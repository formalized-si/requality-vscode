package com.unitesk.requality.vscode.ui.navigator;

/** {@code org.eclipse.ui.navigator.INavigatorActivationService}: the activation of the content extensions of a viewer. */
public interface INavigatorActivationService {
  boolean isNavigatorExtensionActive(String aNavigatorExtensionId);

  /** Activates the extensions; with {@code toDeactivateAllOthers} the other extensions are deactivated. */
  void activateExtensions(String[] extensionIds, boolean toDeactivateAllOthers);

  void deactivateExtensions(String[] extensionIds, boolean toEnableAllOthers);

  void persistExtensionActivations();

  void addExtensionActivationListener(IExtensionActivationListener aListener);

  void removeExtensionActivationListener(IExtensionActivationListener aListener);
}
