package com.unitesk.requality.vscode.ui.team.ui.history;

import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;

/**
 * {@code org.eclipse.team.ui.history.IHistoryPage}: a page that appears in the team history view (the pages of the
 * History view and of the history compare dialogs).
 */
public interface IHistoryPage {
  /** Property name constant for the name of the page. */
  String P_NAME = "org.eclipse.team.ui.name";
  /** Property name constant for the description of the page. */
  String P_DESCRIPTION = "org.eclipse.team.ui.description";

  /** Fetches and populates the page for the object; true if the page can display its history. */
  boolean setInput(Object object);

  /** The object whose history is displayed, or null when no input has been set. */
  Object getInput();

  /** Whether this page can show the history of the object. */
  boolean isValidInput(Object object);

  /** Requests a refresh of the information presented by the page. */
  void refresh();

  /** The name of the object whose history the page shows. */
  String getName();

  /** A one line description of the object whose history is displayed, or null. */
  String getDescription();

  /** Sets the site of the page. */
  void setSite(IHistoryPageSite site);

  /** The site of the page. */
  IHistoryPageSite getHistoryPageSite();

  /** Disposes of the widgets of the page. */
  void dispose();

  /** The history view that contains this page, or null. */
  IHistoryView getHistoryView();

  void addPropertyChangeListener(IPropertyChangeListener listener);

  void removePropertyChangeListener(IPropertyChangeListener listener);
}
