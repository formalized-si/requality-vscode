package com.unitesk.requality.vscode.ui.navigator;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.navigator.wizards.CommonWizardDescriptor;
import com.unitesk.requality.vscode.ui.navigator.wizards.CommonWizardDescriptorManager;
import com.unitesk.requality.vscode.ui.navigator.wizards.WizardShortcutAction;
import com.unitesk.requality.vscode.ui.workbench.IWizardDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IWizardRegistry;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionContext;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionGroup;

/**
 * {@code org.eclipse.ui.navigator.WizardActionGroup}: populates context menus with shortcut actions for the wizards
 * declared by {@code commonWizard} elements of the navigator content extensions, for the selection of the viewer.
 * The wizards come from the new, import or export wizard registries.
 */
public final class WizardActionGroup extends ActionGroup {
  /** The type for commonWizard extensions with the value "new" for their type attribute. */
  public static final String TYPE_NEW = "new";
  /** The type for commonWizard extensions with the value "import" for their type attribute. */
  public static final String TYPE_IMPORT = "import";
  /** The type for commonWizard extensions with the value "export" for their type attribute. */
  public static final String TYPE_EXPORT = "export";

  private static final CommonWizardDescriptor[] NO_DESCRIPTORS = new CommonWizardDescriptor[0];
  private static final String[] NO_IDS = new String[0];

  private CommonWizardDescriptor[] descriptors;
  /** The actions by wizard id, kept so that the image caching of the contribution items works. */
  private Map<String, IAction> actions;
  private final IWorkbenchWindow window;
  private final IWizardRegistry wizardRegistry;
  private boolean disposed = false;
  private final String type;
  private final INavigatorContentService contentService;
  private final Predicate<IWizardDescriptor> descriptorFilter;
  private final boolean useSeparators;

  /**
   * @param aWindow the window used for the shell and the selection of the wizard shortcuts
   * @param aWizardRegistry the registry of the wizards
   * @param aType the type of the commonWizard elements: {@link #TYPE_NEW}, {@link #TYPE_IMPORT} or {@link #TYPE_EXPORT}
   * @param aContentService the content service of the viewer, which decides the visibility, or null
   * @param descriptorFilter the filter of the wizards, or null for all of them
   * @param useSeparators true to add a separator before each group of wizards
   */
  public WizardActionGroup(IWorkbenchWindow aWindow, IWizardRegistry aWizardRegistry, String aType,
      INavigatorContentService aContentService, Predicate<IWizardDescriptor> descriptorFilter, boolean useSeparators) {
    if (aWindow == null || aWizardRegistry == null || aType == null)
      throw new IllegalArgumentException();
    if (!(TYPE_NEW.equals(aType) || TYPE_IMPORT.equals(aType) || TYPE_EXPORT.equals(aType)))
      throw new IllegalArgumentException(aType);
    this.window = aWindow;
    this.wizardRegistry = aWizardRegistry;
    this.type = aType;
    this.contentService = aContentService;
    this.descriptorFilter = descriptorFilter;
    this.useSeparators = useSeparators;
  }

  /** The group for the wizards of the registry without a filter, with separators. */
  public WizardActionGroup(IWorkbenchWindow aWindow, IWizardRegistry aWizardRegistry, String aType,
      INavigatorContentService aContentService) {
    this(aWindow, aWizardRegistry, aType, aContentService, null, true);
  }

  public WizardActionGroup(IWorkbenchWindow aWindow, IWizardRegistry aWizardRegistry, String aType) {
    this(aWindow, aWizardRegistry, aType, null, null, true);
  }

  @Override
  public void setContext(ActionContext aContext) {
    if (disposed)
      throw new IllegalStateException("disposed");
    super.setContext(aContext);
    if (aContext != null) {
      ISelection selection = aContext.getSelection();
      Object element = null;
      if (selection instanceof IStructuredSelection)
        element = ((IStructuredSelection) selection).getFirstElement();
      if (element == null)
        element = Collections.EMPTY_LIST;
      setWizardActionDescriptors(CommonWizardDescriptorManager.getInstance()
          .getEnabledCommonWizardDescriptors(element, type, contentService));
    } else {
      setWizardActionDescriptors(NO_DESCRIPTORS);
    }
  }

  @Override
  public void fillContextMenu(IMenuManager menu) {
    if (disposed)
      throw new IllegalStateException("disposed");
    if (descriptors != null) {
      Map<String, SortedSet<IAction>> groups = findGroups();
      for (Map.Entry<String, SortedSet<IAction>> entry : groups.entrySet()) {
        if (useSeparators)
          menu.add(new Separator(entry.getKey()));
        for (Iterator<IAction> wizardItr = entry.getValue().iterator(); wizardItr.hasNext();)
          menu.add(wizardItr.next());
      }
    }
  }

  /** The actions of the enabled wizards by menu group id. */
  private synchronized Map<String, SortedSet<IAction>> findGroups() {
    Map<String, SortedSet<IAction>> groups = new TreeMap<>();
    for (CommonWizardDescriptor descriptor : descriptors) {
      String menuGroupId = descriptor.getMenuGroupId() != null ? descriptor.getMenuGroupId()
          : CommonWizardDescriptor.DEFAULT_MENU_GROUP_ID;
      SortedSet<IAction> sortedWizards = groups.get(menuGroupId);
      if (sortedWizards == null)
        groups.put(menuGroupId, sortedWizards = new TreeSet<>(ActionComparator.INSTANCE));
      IAction action = getAction(descriptor.getWizardId());
      if (action != null)
        sortedWizards.add(action);
    }
    return groups;
  }

  @Override
  public void dispose() {
    super.dispose();
    actions = null;
    descriptors = null;
    disposed = true;
  }

  /** The action for the given wizard id, or null if the wizard is not in the registry. */
  protected IAction getAction(String id) {
    if (id == null || id.isEmpty())
      return null;
    IAction action = getActions().get(id);
    if (action == null) {
      IWizardDescriptor descriptor = getDescriptor(id);
      if (descriptor != null) {
        action = new WizardShortcutAction(window, descriptor);
        getActions().put(id, action);
      }
    }
    return action;
  }

  protected IWizardDescriptor getDescriptor(String id) {
    IWizardDescriptor descriptor = wizardRegistry.findWizard(id);
    if (descriptor != null && descriptorFilter != null && !descriptorFilter.test(descriptor))
      return null;
    return descriptor;
  }

  protected Map<String, IAction> getActions() {
    if (actions == null)
      actions = new HashMap<>();
    return actions;
  }

  /** The ids of the wizards enabled for the current context. */
  public synchronized String[] getWizardActionIds() {
    if (descriptors != null && descriptors.length > 0) {
      String[] ids = new String[descriptors.length];
      for (int i = 0; i < descriptors.length; i++)
        ids[i] = descriptors[i].getWizardId();
      return ids;
    }
    return NO_IDS;
  }

  private synchronized void setWizardActionDescriptors(CommonWizardDescriptor[] theWizardDescriptors) {
    descriptors = theWizardDescriptors;
  }

  /** Orders the wizard actions by their text, ignoring case. */
  private static final class ActionComparator implements Comparator<IAction> {
    private static final ActionComparator INSTANCE = new ActionComparator();

    @Override
    public int compare(IAction arg0, IAction arg1) {
      return arg0.getText().compareToIgnoreCase(arg1.getText());
    }
  }
}
