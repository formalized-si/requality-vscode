package com.unitesk.requality.vscode.ui.navigator;

/** {@code org.eclipse.ui.navigator.IExtensionActivationListener}: content extensions were (de)activated. */
public interface IExtensionActivationListener {
  void onExtensionActivation(String aViewerId, String[] theNavigatorExtensionIds, boolean isActive);
}
