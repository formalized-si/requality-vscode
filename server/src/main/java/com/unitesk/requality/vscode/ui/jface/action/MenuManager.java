package com.unitesk.requality.vscode.ui.jface.action;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Menu;
import com.unitesk.requality.vscode.ui.swt.widgets.MenuItem;

/** {@code org.eclipse.jface.action.MenuManager}: builds the SWT menu each time it is shown. */
public class MenuManager extends ContributionItem implements IMenuManager {
  private final List<IContributionItem> items = new ArrayList<>();
  private final List<IMenuListener> listeners = new CopyOnWriteArrayList<>();
  private final String text;
  private boolean removeAllWhenShown;
  private Menu menu;

  public MenuManager() {
    this(null, null);
  }

  public MenuManager(String text) {
    this(text, null);
  }

  public MenuManager(String text, String id) {
    super(id);
    this.text = text;
  }

  public String getMenuText() {
    return text;
  }

  @Override
  public void add(IAction action) {
    items.add(new ActionContributionItem(action));
  }

  @Override
  public void add(IContributionItem item) {
    items.add(item);
  }

  @Override
  public IContributionItem find(String id) {
    for (IContributionItem i : items)
      if (id != null && id.equals(i.getId()))
        return i;
    return null;
  }

  /** {@code ContributionManager.indexOf(String)}: the index of the item with the id, or -1. */
  public int indexOf(String id) {
    for (int k = 0; k < items.size(); k++)
      if (id != null && id.equals(items.get(k).getId()))
        return k;
    return -1;
  }

  /** ContributionManager.addToGroup: IllegalArgumentException "Group not found" when there is no such marker. */
  private void addToGroup(String groupName, IContributionItem item, boolean append) {
    int k = 0;
    for (; k < items.size(); k++) {
      IContributionItem i = items.get(k);
      if (i.isGroupMarker() && groupName.equals(i.getId()))
        break;
    }
    if (k == items.size())
      throw new IllegalArgumentException("Group not found: " + groupName);
    k++;
    if (append)
      while (k < items.size() && !items.get(k).isGroupMarker())
        k++;
    items.add(k, item);
  }

  @Override
  public void appendToGroup(String groupName, IAction action) {
    addToGroup(groupName, new ActionContributionItem(action), true);
  }

  @Override
  public void appendToGroup(String groupName, IContributionItem item) {
    addToGroup(groupName, item, true);
  }

  @Override
  public void prependToGroup(String groupName, IAction action) {
    addToGroup(groupName, new ActionContributionItem(action), false);
  }

  @Override
  public void prependToGroup(String groupName, IContributionItem item) {
    addToGroup(groupName, item, false);
  }

  @Override
  public void insertAfter(String id, IAction action) {
    insertAfter(id, new ActionContributionItem(action));
  }

  /** {@code ContributionManager.insert(int, IContributionItem)}: the item at the index. */
  public void insert(int index, IContributionItem item) {
    items.add(Math.max(0, Math.min(index, items.size())), item);
  }

  @Override
  public void insertAfter(String id, IContributionItem item) {
    int k = indexOf(id);
    if (k < 0)
      throw new IllegalArgumentException("can't find ID" + id);
    items.add(k + 1, item);
  }

  @Override
  public void insertBefore(String id, IAction action) {
    insertBefore(id, new ActionContributionItem(action));
  }

  @Override
  public void insertBefore(String id, IContributionItem item) {
    int k = indexOf(id);
    if (k < 0)
      throw new IllegalArgumentException("can't find ID" + id);
    items.add(k, item);
  }

  @Override
  public void removeAll() {
    items.clear();
  }

  @Override
  public IContributionItem[] getItems() {
    return items.toArray(new IContributionItem[0]);
  }

  @Override
  public void addMenuListener(IMenuListener l) {
    listeners.add(l);
  }

  public void removeMenuListener(IMenuListener l) {
    listeners.remove(l);
  }

  @Override
  public void setRemoveAllWhenShown(boolean b) {
    removeAllWhenShown = b;
  }

  public Menu createContextMenu(Control parent) {
    menu = new Menu(parent);
    menu.addListener(SWT.Show, e -> rebuild());
    return menu;
  }

  public Menu getMenu() {
    return menu;
  }

  private void rebuild() {
    if (removeAllWhenShown)
      items.clear();
    for (IMenuListener l : listeners)
      l.menuAboutToShow(this);
    for (MenuItem mi : menu.getItems())
      mi.dispose();
    fillItems(menu);
  }

  /**
   * Fills a menu with the visible items as {@code MenuManager.update} does: a separator is shown only between two
   * other items (not at the start, not at the end, not after another separator).
   */
  private void fillItems(Menu m) {
    IContributionItem separator = null;
    boolean filled = false;
    for (IContributionItem item : items) {
      if (!item.isVisible())
        continue;
      if (item.isSeparator()) {
        separator = item;
        continue;
      }
      if (separator != null && filled)
        separator.fill(m, -1);
      separator = null;
      item.fill(m, -1);
      filled = true;
    }
  }

  @Override
  public void fill(Menu parent, int index) {
    MenuItem cascade = new MenuItem(parent, SWT.CASCADE, index);
    cascade.setText(text == null ? "" : text);
    Menu sub = new Menu(cascade);
    cascade.setMenu(sub);
    for (IMenuListener l : listeners)
      l.menuAboutToShow(this);
    fillItems(sub);
  }

  public void update(boolean force) {
  }

  /**
   * {@code MenuManager.isVisible}: a menu is visible when it has a visible item that is not a separator or a group
   * marker (or is filled when shown).
   */
  @Override
  public boolean isVisible() {
    if (!super.isVisible())
      return false;
    if (removeAllWhenShown || !listeners.isEmpty())
      return true;
    for (IContributionItem item : items)
      if (item.isVisible() && !item.isSeparator() && !item.isGroupMarker())
        return true;
    return false;
  }

  public void dispose() {
    if (menu != null && !menu.isDisposed())
      menu.dispose();
  }
}
