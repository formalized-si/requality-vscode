package com.unitesk.requality.vscode.ui.team.synchronize;

import com.unitesk.requality.vscode.services.MenuService;
import com.unitesk.requality.vscode.ui.compare.CompareLinks;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.FillLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.team.preferences.IPreferenceIds;
import com.unitesk.requality.vscode.ui.team.preferences.TeamUIPlugin;
import com.unitesk.requality.vscode.ui.team.synchronize.actions.PasteAction;
import com.unitesk.requality.vscode.ui.team.synchronize.actions.PinParticipantAction;
import com.unitesk.requality.vscode.ui.team.synchronize.actions.RemoveSynchronizeParticipantAction;
import com.unitesk.requality.vscode.ui.team.synchronize.actions.SynchronizePageDropDownAction;
import com.unitesk.requality.vscode.ui.team.synchronize.actions.ToggleLinkingAction;
import com.unitesk.requality.vscode.ui.team.ui.synchronize.ISynchronizeParticipant;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.ViewPart;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionFactory;

/**
 * {@code org.eclipse.team.internal.ui.synchronize.SynchronizeView} (a PageBookView): the Synchronize view (Window &gt; Show
 * View &gt; Other &gt; Team). Its pages are the synchronize participants: the view shows the current participant, or the default
 * page (empty) without one. The title is the name of the participant, the content description its description.
 * The toolbar has the page drop-down (the participants and Synchronize...) and Pin; the view menu has Pin, Link with Editor,
 * Remove Current Synchronization and Remove Un-Pinned Synchronizations; Paste (the global paste) pastes a patch from the
 * clipboard. With no participant, the actions of the participants are disabled and the paste is always enabled.
 * VSCODE: the view is registered as a host view of the Requality panel; the default page is a composite with the margins of
 * the MessagePage of Eclipse. The Link with Editor state is kept in the Team UI preferences; with it on, the compare editors
 * opened for the elements of the participants are linked with the elements (see {@link CompareLinks}). The active VS Code
 * editor is not followed.
 */
public class SynchronizeView extends ViewPart {
  /** The id of the view (plugin.xml {@code org.eclipse.team.sync.views.SynchronizeView}). */
  public static final String ID = "org.eclipse.team.sync.views.SynchronizeView";
  /** {@code SynchronizeView.MAX_NAME_LENGTH}: the longest name of a synchronization in the title. */
  public static final int MAX_NAME_LENGTH = 100;
  /** The menu of the page drop-down, listed by the server ({@code menu/items}). */
  public static final String SYNCHRONIZATIONS_MENU = ID + ".synchronizations";

  private Composite pageBook;
  private Control currentPage;
  private ISynchronizeParticipant participant;
  private String defaultTitle;
  private SynchronizePageDropDownAction pageDropDown;
  private PinParticipantAction pinAction;
  private ToggleLinkingAction toggleLinkingAction;
  private RemoveSynchronizeParticipantAction removeCurrentAction;
  private RemoveSynchronizeParticipantAction removeAllAction;
  private PasteAction pastePatchAction;
  private final SynchronizeManager.Listener managerListener = this::participantsChanged;

  @Override
  public void createPartControl(Composite parent) {
    defaultTitle = getTitle();
    pageBook = new Composite(parent, SWT.NONE);
    pageBook.setLayout(new FillLayout());
    createActions();
    configureToolBar(getViewSite().getActionBars());
    SynchronizeManager.getInstance().addListener(managerListener);
    CompareLinks.setLinkingEnabled(this::isLinkingEnabled);
    ISynchronizeParticipant[] all = SynchronizeManager.getInstance().getSynchronizeParticipants();
    display(all.length == 0 ? null : all[all.length - 1]);
  }

  /**
   * Shows the participant in the page book: its page replaces the current one ({@code SynchronizeView.display}). A null
   * participant shows the default page.
   */
  public void display(ISynchronizeParticipant p) {
    if (pageBook == null || pageBook.isDisposed())
      return;
    if (currentPage != null && !currentPage.isDisposed())
      currentPage.dispose();
    currentPage = null;
    participant = p;
    if (p != null)
      currentPage = p.createPage(pageBook);
    else
      currentPage = createDefaultPage();
    pageBook.layout(true);
    updateTitle();
    updateActionEnablements();
  }

  /** The default page: an empty page with the margins of the message page of Eclipse. */
  private Control createDefaultPage() {
    Composite defaultPage = new Composite(pageBook, SWT.NONE);
    FillLayout layout = new FillLayout();
    layout.marginHeight = 5;
    layout.marginWidth = 5;
    defaultPage.setLayout(layout);
    return defaultPage;
  }

  /** The participants changed: the view shows another participant when its current one is removed. */
  private void participantsChanged() {
    if (pageBook == null || pageBook.isDisposed())
      return;
    ISynchronizeParticipant[] all = SynchronizeManager.getInstance().getSynchronizeParticipants();
    boolean current = false;
    for (ISynchronizeParticipant p : all)
      current |= p == participant;
    if (participant == null || !current) {
      display(all.length == 0 ? null : all[all.length - 1]);
    } else {
      updateActionEnablements();
    }
  }

  /**
   * Updates the title of the view from the current participant: the name of the participant in the title, its description
   * as the content description; without one, the title and the description of the view are the default ones.
   */
  protected void updateTitle() {
    if (participant == null) {
      if (defaultTitle != null)
        setPartName(defaultTitle);
      setContentDescription("");
      setTitleToolTip("");
      return;
    }
    String name = participant.getName();
    if (name != null && name.length() > MAX_NAME_LENGTH)
      name = name.substring(0, MAX_NAME_LENGTH) + "...";
    setPartName("Synchronize (" + name + ")");
    setContentDescription(participant.getDescription());
    setTitleToolTip(participant.getDescription());
  }

  /** Creates the actions of the view ({@code SynchronizeView.createActions}). */
  protected void createActions() {
    pageDropDown = new SynchronizePageDropDownAction(this);
    pinAction = new PinParticipantAction(this);
    toggleLinkingAction = new ToggleLinkingAction(this);
    removeCurrentAction = new RemoveSynchronizeParticipantAction(this, false);
    removeAllAction = new RemoveSynchronizeParticipantAction(this, true);
    pastePatchAction = new PasteAction(this);
    updateActionEnablements();
  }

  /**
   * The enablement of the actions: the participant actions need a current participant (the Remove Un-Pinned ones need a
   * participant to remove), the paste is always enabled.
   */
  public void updateActionEnablements() {
    boolean hasParticipant = getParticipant() != null;
    boolean hasAny = SynchronizeManager.getInstance().getSynchronizeParticipants().length > 0;
    pinAction.setParticipant(participant);
    toggleLinkingAction.setEnabled(hasParticipant);
    removeAllAction.setEnabled(hasAny);
    removeCurrentAction.setEnabled(hasParticipant);
    if (pageDropDown != null)
      pageDropDown.update();
    pastePatchAction.setEnabled(true);
    // VSCODE: the toolbar and view menu of the view are the VS Code view title items, which read the state of the
    // actions again when told (command/changed)
    if (com.unitesk.requality.vscode.ui.commands.CommandSupport.get() != null)
      com.unitesk.requality.vscode.ui.commands.CommandSupport.get().refreshElements(ID);
  }

  /** The current participant of the view, or null when the view shows the default page. */
  public ISynchronizeParticipant getParticipant() {
    return participant;
  }

  /** Adds the actions to the toolbar, the view menu and the global handlers ({@code SynchronizeView.configureToolBar}). */
  protected void configureToolBar(IActionBars bars) {
    ToolBarManager mgr = bars.getToolBarManager();
    mgr.add(pageDropDown);
    mgr.add(pinAction);
    IMenuManager menu = bars.getMenuManager();
    menu.add(pinAction);
    menu.add(toggleLinkingAction);
    menu.add(removeCurrentAction);
    menu.add(removeAllAction);
    // the view menu, listed by the server for the client (menu/items)
    MenuService.register(ID + ".viewMenu", menu::getItems);
    MenuService.register(SYNCHRONIZATIONS_MENU, pageDropDown::getMenuItems);
    bars.setGlobalActionHandler(ActionFactory.PASTE.getId(), pastePatchAction);
    bars.updateActionBars();
  }

  /** Whether the view is linked with the active editor ({@code SynchronizeView.isLinkingEnabled}). */
  public boolean isLinkingEnabled() {
    return TeamUIPlugin.getPreferenceStore().getBoolean(IPreferenceIds.SYNCVIEW_LINK_WITH_EDITOR);
  }

  /**
   * Sets the Link with Editor state, kept in the preferences ({@code SynchronizeView.setLinkingEnabled}). The compare
   * editors of the elements are linked with the view while it is on (see {@link CompareLinks}).
   */
  public void setLinkingEnabled(boolean enabled) {
    TeamUIPlugin.getPreferenceStore().setValue(IPreferenceIds.SYNCVIEW_LINK_WITH_EDITOR, enabled);
  }

  @Override
  public void setFocus() {
    if (pageBook != null && !pageBook.isDisposed())
      pageBook.setFocus();
  }

  @Override
  public void dispose() {
    SynchronizeManager.getInstance().removeListener(managerListener);
    CompareLinks.setLinkingEnabled(null);
    if (pastePatchAction != null)
      pastePatchAction.dispose();
    if (pageDropDown != null)
      pageDropDown.dispose();
    super.dispose();
  }
}
