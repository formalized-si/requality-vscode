package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckStateChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ComboViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ICheckStateListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ICheckStateProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ITableLabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.TabFolder;
import com.unitesk.requality.vscode.ui.swt.widgets.TabItem;
import com.unitesk.requality.vscode.ui.swt.widgets.TableColumn;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveRegistry.IPerspectiveDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IWizardCategory;
import com.unitesk.requality.vscode.ui.workbench.IWizardDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.Workbench;
import com.unitesk.requality.vscode.ui.workbench.internal.MainMenu;
import com.unitesk.requality.vscode.ui.workbench.internal.PerspectiveRegistry;
import com.unitesk.requality.vscode.ui.workbench.internal.PerspectiveCustomization;
import com.unitesk.requality.vscode.ui.workbench.internal.ViewRegistry;
import com.unitesk.requality.vscode.ui.workbench.views.IViewCategory;
import com.unitesk.requality.vscode.ui.workbench.views.IViewDescriptor;

/**
 * {@code org.eclipse.ui.internal.dialogs.cpd.CustomizePerspectiveDialog} (Window &gt; Perspective &gt; Customize
 * Perspective...): the customization of the current perspective, in four tabs: the toolbar items, the menu items, the
 * action sets and the shortcuts of the New, Open Perspective and Show View submenus. Apply and Close keeps the hidden
 * menu items and the shortcuts in the customization of the perspective (see {@link IWorkbenchPage#customizePerspective}).
 *
 * <p>VSCODE: the product has no main tool bar and no action sets, so those trees and lists are empty. The menu tree is
 * the one of {@code main-menu.json}; the "Filter by action set" check box shows the (empty) action set list beside the
 * tree. Reset Perspective, not this dialog, restores the defaults of the perspective.
 */
public class CustomizePerspectiveDialog extends Dialog {
  private static final int TAB_WIDTH_IN_DLUS = 490;
  private static final int TAB_HEIGHT_IN_DLUS = 230;
  private static final int[] SHORTCUT_COLUMN_WIDTHS = { 125, 300 };
  private static final String[] SHORTCUT_COLUMN_HEADERS = { "Shortcut", "Description" };
  /** {@code IIntroConstants.INTRO_VIEW_ID}: the Welcome view is not a shortcut. */
  private static final String INTRO_VIEW_ID = "org.eclipse.ui.internal.introview";

  /** A menu, submenu or item of the Menu Visibility tree; checked means visible. */
  private static final class MenuNode {
    final String id;
    final String label;
    final MenuNode parent;
    final List<MenuNode> children = new ArrayList<>();
    boolean checked;

    MenuNode(String id, String label, MenuNode parent) {
      this.id = id;
      this.label = label;
      this.parent = parent;
    }

    boolean anyChildChecked() {
      for (MenuNode c : children)
        if (c.checked)
          return true;
      return false;
    }
  }

  /** A category of the Shortcuts tab: the submenu itself, or a category of its items. */
  private static final class ShortcutCategory {
    final String label;
    final List<ShortcutCategory> children = new ArrayList<>();
    final List<ShortcutEntry> items = new ArrayList<>();

    ShortcutCategory(String label) {
      this.label = label;
    }
  }

  /** A shortcut of a submenu: a wizard, a perspective or a view. */
  private static final class ShortcutEntry {
    final String id;
    final String label;
    final String description;
    boolean checked;

    ShortcutEntry(String id, String label, String description, boolean checked) {
      this.id = id;
      this.label = label;
      this.description = description == null ? "" : description;
      this.checked = checked;
    }
  }

  private final IWorkbenchPage page;
  private final IPerspectiveDescriptor perspective;
  private final List<MenuNode> menuRoots = new ArrayList<>();
  private final List<MenuNode> menuNodes = new ArrayList<>();
  private final ShortcutCategory newWizards = new ShortcutCategory("New");
  private final ShortcutCategory openPerspectives = new ShortcutCategory("Open Perspective");
  private final ShortcutCategory showViews = new ShortcutCategory("Show View");
  private final List<ShortcutCategory> submenus = new ArrayList<>();
  private CheckboxTreeViewer menuViewer;
  private CheckboxTreeViewer categoriesViewer;
  private CheckboxTableViewer shortcutsViewer;
  private ComboViewer submenuViewer;

  public CustomizePerspectiveDialog(Shell parentShell, IWorkbenchPage page) {
    super(parentShell);
    this.page = page;
    this.perspective = page.getPerspective();
    initMenus();
    initShortcuts();
  }

  /** The menu tree, with the items hidden by the customization of the perspective unchecked. */
  private void initMenus() {
    PerspectiveCustomization.Customization c = PerspectiveCustomization.get(perspective.getId());
    List<String> hidden = c == null ? new ArrayList<>() : c.getMenuHidden();
    for (MainMenu.Item m : MainMenu.menus())
      menuRoots.add(menuNode(m, null, hidden));
  }

  private MenuNode menuNode(MainMenu.Item item, MenuNode parent, List<String> hidden) {
    MenuNode n = new MenuNode(item.getId(), item.getLabel(), parent);
    menuNodes.add(n);
    for (MainMenu.Item child : item.getItems())
      n.children.add(menuNode(child, n, hidden));
    n.checked = n.children.isEmpty() ? !hidden.contains(n.id) : n.anyChildChecked();
    return n;
  }

  /** The Shortcuts tab: New (the wizards), Open Perspective and Show View (the views in their categories). */
  private void initShortcuts() {
    List<String> wizards = List.of(page.getNewWizardShortcuts());
    List<String> perspectives = List.of(page.getPerspectiveShortcuts());
    List<String> views = List.of(page.getShowViewShortcuts());
    IWizardCategory root = Workbench.INSTANCE.getNewWizardRegistry().getRootCategory();
    for (IWizardCategory c : root.getCategories())
      newWizards.children.add(wizardCategory(c, wizards));
    for (IPerspectiveDescriptor d : PerspectiveRegistry.INSTANCE.getPerspectives())
      openPerspectives.items.add(new ShortcutEntry(d.getId(), d.getLabel(), d.getDescription(),
          perspectives.contains(d.getId())));
    for (IViewCategory c : ViewRegistry.INSTANCE.getCategories()) {
      ShortcutCategory category = new ShortcutCategory(c.getLabel());
      for (IViewDescriptor v : c.getViews())
        if (!INTRO_VIEW_ID.equals(v.getId()))
          category.items.add(new ShortcutEntry(v.getId(), v.getLabel(), v.getDescription(), views.contains(v.getId())));
      if (!category.items.isEmpty())
        showViews.children.add(category);
    }
    submenus.add(newWizards);
    submenus.add(openPerspectives);
    submenus.add(showViews);
  }

  private static ShortcutCategory wizardCategory(IWizardCategory c, List<String> active) {
    ShortcutCategory category = new ShortcutCategory(c.getLabel());
    for (IWizardCategory sub : c.getCategories())
      category.children.add(wizardCategory(sub, active));
    for (IWizardDescriptor w : c.getWizards())
      category.items.add(new ShortcutEntry(w.getId(), w.getLabel(), w.getDescription(), active.contains(w.getId())));
    return category;
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Customize Perspective - " + perspective.getLabel());
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.OK_ID, "&Apply and Close", true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite composite = (Composite) super.createDialogArea(parent);
    TabFolder tabFolder = new TabFolder(composite, SWT.NONE);
    GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
    gd.widthHint = convertHorizontalDLUsToPixels(TAB_WIDTH_IN_DLUS);
    gd.heightHint = convertVerticalDLUsToPixels(TAB_HEIGHT_IN_DLUS);
    tabFolder.setLayoutData(gd);

    TabItem tab = new TabItem(tabFolder, SWT.NONE);
    tab.setText("Toolbar Visibility");
    tab.setControl(createToolBarVisibilityPage(tabFolder));

    tab = new TabItem(tabFolder, SWT.NONE);
    tab.setText("Menu Visibility");
    tab.setControl(createMenuVisibilityPage(tabFolder));

    tab = new TabItem(tabFolder, SWT.NONE);
    tab.setText("Action Set Availability");
    tab.setControl(createActionSetAvailabilityPage(tabFolder));

    tab = new TabItem(tabFolder, SWT.NONE);
    tab.setText("Shortcuts");
    tab.setControl(createShortcutsPage(tabFolder));

    applyDialogFont(tabFolder);
    return composite;
  }

  /** Toolbar Visibility: the main tool bar of the product, which VS Code does not have, so the tree is empty. */
  private Composite createToolBarVisibilityPage(Composite parent) {
    Composite pageComposite = new Composite(parent, SWT.NONE);
    pageComposite.setLayout(new GridLayout());
    label(pageComposite, "Choose which toolbar items to display.");
    separator(pageComposite);
    Composite body = new Composite(pageComposite, SWT.NONE);
    body.setLayout(twoColumns());
    body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    Composite actionSets = actionSetGroup(body);
    Composite group = itemStructureGroup(body, "Toolbar Structure:");
    CheckboxTreeViewer viewer = checkTree(group);
    viewer.setInput(new ArrayList<MenuNode>());
    filterCheckBox(pageComposite, actionSets, body);
    return pageComposite;
  }

  /** Menu Visibility: the menus of the product with their items; checked items are visible. */
  private Composite createMenuVisibilityPage(Composite parent) {
    Composite pageComposite = new Composite(parent, SWT.NONE);
    pageComposite.setLayout(new GridLayout());
    label(pageComposite, "Choose which menu items to display.");
    separator(pageComposite);
    Composite body = new Composite(pageComposite, SWT.NONE);
    body.setLayout(twoColumns());
    body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    Composite actionSets = actionSetGroup(body);
    Composite group = itemStructureGroup(body, "Menu Structure:");
    menuViewer = checkTree(group);
    menuViewer.addCheckStateListener(new ICheckStateListener() {
      @Override
      public void checkStateChanged(CheckStateChangedEvent event) {
        menuChecked((MenuNode) event.getElement(), event.getChecked());
      }
    });
    menuViewer.setInput(menuRoots);
    syncMenu();
    filterCheckBox(pageComposite, actionSets, body);
    return pageComposite;
  }

  /** Action Set Availability: the product declares no action sets, so the list and the details are empty. */
  private Composite createActionSetAvailabilityPage(Composite parent) {
    Composite pageComposite = new Composite(parent, SWT.NONE);
    pageComposite.setLayout(new GridLayout());
    label(pageComposite, "Select the action sets that you want to see added to the current perspective ("
        + perspective.getLabel() + "). The details field identifies which menu items and/or toolbar items are added to "
        + "the perspective by the selected action set.");
    separator(pageComposite);
    Composite body = new Composite(pageComposite, SWT.NONE);
    body.setLayout(twoColumns());
    body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    Composite group = itemStructureGroup(body, "Available action sets:");
    CheckboxTableViewer actionSets = CheckboxTableViewer.newCheckList(group, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    actionSets.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    actionSets.setContentProvider(ArrayContentProvider.getInstance());
    actionSets.setLabelProvider(new LabelProvider());
    actionSets.setInput(new ArrayList<Object>());

    Composite details = new Composite(body, SWT.NONE);
    details.setLayout(new GridLayout());
    details.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    for (String text : List.of("Menubar details:", "Toolbar details:")) {
      Composite g = itemStructureGroup(details, text);
      TreeViewer viewer = new TreeViewer(g, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
      viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      viewer.setContentProvider(new MenuContentProvider());
      viewer.setLabelProvider(new MenuLabelProvider());
      viewer.setInput(new ArrayList<MenuNode>());
    }
    return pageComposite;
  }

  /** Shortcuts: the submenus, their categories and the shortcuts of the selected category, with check boxes. */
  private Composite createShortcutsPage(Composite parent) {
    Composite pageComposite = new Composite(parent, SWT.NONE);
    pageComposite.setLayout(new GridLayout());
    label(pageComposite, "Select the shortcuts that you want to see added as cascade items to the following submenus. The "
        + "selections made will only affect the current perspective (" + perspective.getLabel() + ").");
    separator(pageComposite);
    Composite body = new Composite(pageComposite, SWT.NONE);
    body.setLayout(twoColumns());
    body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    Composite left = new Composite(body, SWT.NONE);
    left.setLayout(new GridLayout());
    left.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    label(left, "&Submenus:");
    Combo combo = new Combo(left, SWT.READ_ONLY);
    combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    submenuViewer = new ComboViewer(combo);
    submenuViewer.setContentProvider(ArrayContentProvider.getInstance());
    submenuViewer.setLabelProvider(new LabelProvider() {
      @Override
      public String getText(Object element) {
        return ((ShortcutCategory) element).label;
      }
    });
    submenuViewer.setInput(submenus);

    label(left, "Shortcut &Categories:");
    categoriesViewer = checkTree(left);
    categoriesViewer.setContentProvider(new CategoryContentProvider());
    categoriesViewer.setLabelProvider(new CategoryLabelProvider());
    categoriesViewer.addCheckStateListener(new ICheckStateListener() {
      @Override
      public void checkStateChanged(CheckStateChangedEvent event) {
        categoryChecked((ShortcutCategory) event.getElement(), event.getChecked());
      }
    });
    categoriesViewer.addSelectionChangedListener(new ISelectionChangedListener() {
      @Override
      public void selectionChanged(SelectionChangedEvent event) {
        Object o = event.getStructuredSelection().getFirstElement();
        if (o instanceof ShortcutCategory)
          showItems((ShortcutCategory) o);
      }
    });

    Composite right = new Composite(body, SWT.NONE);
    right.setLayout(new GridLayout());
    right.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    label(right, "S&hortcuts:");
    shortcutsViewer = CheckboxTableViewer.newCheckList(right, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    Table table = shortcutsViewer.getTable();
    table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    table.setHeaderVisible(true);
    for (int i = 0; i < SHORTCUT_COLUMN_HEADERS.length; i++) {
      TableColumn column = new TableColumn(table, SWT.NONE, i);
      column.setResizable(true);
      column.setText(SHORTCUT_COLUMN_HEADERS[i]);
      column.setWidth(convertHorizontalDLUsToPixels(SHORTCUT_COLUMN_WIDTHS[i]));
    }
    shortcutsViewer.setContentProvider(ArrayContentProvider.getInstance());
    shortcutsViewer.setLabelProvider(new ShortcutLabelProvider());
    shortcutsViewer.setCheckStateProvider(new ICheckStateProvider() {
      @Override
      public boolean isChecked(Object element) {
        return ((ShortcutEntry) element).checked;
      }

      @Override
      public boolean isGrayed(Object element) {
        return false;
      }
    });
    shortcutsViewer.addCheckStateListener(new ICheckStateListener() {
      @Override
      public void checkStateChanged(CheckStateChangedEvent event) {
        ((ShortcutEntry) event.getElement()).checked = event.getChecked();
        syncCategories();
      }
    });

    submenuViewer.addSelectionChangedListener(new ISelectionChangedListener() {
      @Override
      public void selectionChanged(SelectionChangedEvent event) {
        Object o = event.getStructuredSelection().getFirstElement();
        if (o instanceof ShortcutCategory)
          showSubmenu((ShortcutCategory) o);
      }
    });
    submenuViewer.setSelection(new StructuredSelection(newWizards), true);
    showSubmenu(newWizards);
    return pageComposite;
  }

  /** Shows the categories of a submenu, and the shortcuts of its first category (as Eclipse selects it). */
  private void showSubmenu(ShortcutCategory submenu) {
    categoriesViewer.setInput(submenu);
    syncCategories();
    if (!submenu.children.isEmpty()) {
      categoriesViewer.setSelection(new StructuredSelection(submenu.children.get(0)), true);
      showItems(submenu.children.get(0));
    } else {
      showItems(submenu);
    }
  }

  private void showItems(ShortcutCategory category) {
    shortcutsViewer.setInput(new ArrayList<>(category.items));
    shortcutsViewer.refresh();
  }

  /** A check of a menu item: the item and its submenus follow it, and the submenus are checked when an item is. */
  private void menuChecked(MenuNode node, boolean checked) {
    setSubtree(node, checked);
    for (MenuNode p = node.parent; p != null; p = p.parent)
      p.checked = p.anyChildChecked();
    syncMenu();
  }

  private static void setSubtree(MenuNode node, boolean checked) {
    node.checked = checked;
    for (MenuNode c : node.children)
      setSubtree(c, checked);
  }

  private void syncMenu() {
    if (menuViewer == null)
      return;
    for (MenuNode n : menuNodes) {
      menuViewer.setChecked(n, n.checked);
      menuViewer.setGrayed(n, !n.children.isEmpty() && grayed(n));
    }
  }

  private static boolean grayed(MenuNode n) {
    boolean checked = false;
    boolean unchecked = false;
    for (MenuNode c : n.children) {
      if (c.checked)
        checked = true;
      else
        unchecked = true;
    }
    return checked && unchecked;
  }

  /** A check of a category: all the shortcuts of it and of its subcategories follow it. */
  private void categoryChecked(ShortcutCategory category, boolean checked) {
    setItems(category, checked);
    syncCategories();
    shortcutsViewer.refresh();
  }

  private static void setItems(ShortcutCategory category, boolean checked) {
    for (ShortcutEntry e : category.items)
      e.checked = checked;
    for (ShortcutCategory c : category.children)
      setItems(c, checked);
  }

  /** The categories of the selected submenu are checked when one of their shortcuts is (grayed when only some are). */
  private void syncCategories() {
    if (categoriesViewer == null)
      return;
    for (ShortcutCategory submenu : submenus)
      syncCategories(submenu.children);
  }

  private void syncCategories(List<ShortcutCategory> categories) {
    for (ShortcutCategory c : categories) {
      categoriesViewer.setChecked(c, categoryChecked(c));
      categoriesViewer.setGrayed(c, categoryGrayed(c));
      syncCategories(c.children);
    }
  }

  private static boolean categoryChecked(ShortcutCategory c) {
    for (ShortcutCategory s : c.children)
      if (categoryChecked(s))
        return true;
    for (ShortcutEntry e : c.items)
      if (e.checked)
        return true;
    return false;
  }

  private static boolean categoryGrayed(ShortcutCategory c) {
    boolean checked = false;
    boolean unchecked = false;
    for (ShortcutCategory s : c.children) {
      if (categoryGrayed(s))
        return true;
      if (categoryChecked(s))
        checked = true;
      else
        unchecked = true;
    }
    for (ShortcutEntry e : c.items) {
      if (e.checked)
        checked = true;
      else
        unchecked = true;
    }
    return checked && unchecked;
  }

  private static void collectChecked(ShortcutCategory c, List<String> ids) {
    for (ShortcutEntry e : c.items)
      if (e.checked)
        ids.add(e.id);
    for (ShortcutCategory s : c.children)
      collectChecked(s, ids);
  }

  private static void collectMenuHidden(List<MenuNode> nodes, List<String> ids) {
    for (MenuNode n : nodes) {
      if (!n.checked)
        ids.add(n.id);
      collectMenuHidden(n.children, ids);
    }
  }

  @Override
  protected void okPressed() {
    List<String> wizards = new ArrayList<>();
    collectChecked(newWizards, wizards);
    List<String> perspectives = new ArrayList<>();
    collectChecked(openPerspectives, perspectives);
    List<String> views = new ArrayList<>();
    collectChecked(showViews, views);
    List<String> hidden = new ArrayList<>();
    collectMenuHidden(menuRoots, hidden);
    page.customizePerspective(wizards, perspectives, views, hidden);
    super.okPressed();
  }

  /** The Filter by action set check box: shows the action set list beside the tree. */
  private void filterCheckBox(Composite pageComposite, Composite actionSets, Composite body) {
    Button filter = new Button(pageComposite, SWT.CHECK);
    filter.setText("&Filter by action set");
    filter.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        boolean show = filter.getSelection();
        ((GridData) actionSets.getLayoutData()).exclude = !show;
        actionSets.setVisible(show);
        body.layout();
      }
    });
    actionSets.setVisible(false);
    ((GridData) actionSets.getLayoutData()).exclude = true;
  }

  /** The action set list of the Menu and Toolbar tabs (empty: the product declares no action sets). */
  private Composite actionSetGroup(Composite body) {
    Composite group = itemStructureGroup(body, "&Action Sets:");
    group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    CheckboxTableViewer list = CheckboxTableViewer.newCheckList(group, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    list.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    list.setContentProvider(ArrayContentProvider.getInstance());
    list.setLabelProvider(new LabelProvider());
    list.setInput(new ArrayList<Object>());
    return group;
  }

  private static GridLayout twoColumns() {
    GridLayout layout = new GridLayout(2, true);
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    return layout;
  }

  private static Composite itemStructureGroup(Composite parent, String text) {
    Composite group = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    group.setLayout(layout);
    group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    label(group, text);
    return group;
  }

  private static CheckboxTreeViewer checkTree(Composite parent) {
    CheckboxTreeViewer viewer = new CheckboxTreeViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    viewer.setUseHashlookup(true);
    viewer.setAutoExpandLevel(TreeViewer.ALL_LEVELS);
    viewer.setContentProvider(new MenuContentProvider());
    viewer.setLabelProvider(new MenuLabelProvider());
    return viewer;
  }

  private static Label label(Composite parent, String text) {
    Label label = new Label(parent, SWT.WRAP);
    label.setText(text);
    label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    return label;
  }

  private static void separator(Composite parent) {
    Label sep = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
    sep.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
  }

  /** The content of the menu trees: the menus of the product, their submenus and items. */
  private static final class MenuContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
      return inputElement instanceof List<?> l ? l.toArray() : new Object[0];
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      return parentElement instanceof MenuNode n ? n.children.toArray() : new Object[0];
    }

    @Override
    public Object getParent(Object element) {
      return element instanceof MenuNode n ? n.parent : null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return element instanceof MenuNode n && !n.children.isEmpty();
    }
  }

  private static final class MenuLabelProvider extends LabelProvider {
    @Override
    public String getText(Object element) {
      return element instanceof MenuNode n ? n.label : String.valueOf(element);
    }
  }

  /** The categories of a submenu: its subcategories, and the subcategories of those. */
  private static final class CategoryContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
      return inputElement instanceof ShortcutCategory c ? c.children.toArray() : new Object[0];
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      return parentElement instanceof ShortcutCategory c ? c.children.toArray() : new Object[0];
    }

    @Override
    public Object getParent(Object element) {
      return null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return element instanceof ShortcutCategory c && !c.children.isEmpty();
    }
  }

  private static final class CategoryLabelProvider extends LabelProvider {
    @Override
    public String getText(Object element) {
      return element instanceof ShortcutCategory c ? c.label : String.valueOf(element);
    }
  }

  /** The columns of the shortcuts: the label, and the description. */
  private static final class ShortcutLabelProvider extends LabelProvider implements ITableLabelProvider {
    @Override
    public Image getColumnImage(Object element, int columnIndex) {
      return null;
    }

    @Override
    public String getColumnText(Object element, int columnIndex) {
      ShortcutEntry e = (ShortcutEntry) element;
      return columnIndex == 0 ? e.label : e.description;
    }

    @Override
    public String getText(Object element) {
      return ((ShortcutEntry) element).label;
    }
  }
}
