package com.unitesk.requality.eclipse.wizards.massops;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jface.wizard.WizardPage;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.api.MassOppDescriptor;
import com.unitesk.requality.core.attribute.AttributeFilter;
import com.unitesk.requality.core.attribute.GetterAttributeNames;
import com.unitesk.requality.core.attribute.IAttributeFilter;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.eclipse.api.MassOppDescriptorWithPages;
import com.unitesk.requality.eclipse.wizards.TreeNodeIDMode;
import com.unitesk.requality.eclipse.wizards.TreeNodeSelectionPage;

public class MassOppsWizard extends Wizard {

  public static final String FILE_NAME = "dialogSettings.xml";

  private SelectOppsPage selectOP = null;
  private AbstractExtraSettingsPage[] extraSettings = null;
  private MassOppDescriptor[] objs = null;
  private WizardPage treeNodePage;
  private DialogSettings dialogSettings;
  public static IAttributeFilter filter = AttributeFilter.getOne();
  private TreeNode superRoot;

  public MassOppsWizard(TreeNode root, MassOppDescriptor... objs) {
    setHelpAvailable(false);
    setWindowTitle("Mass ops wizard");
    this.superRoot = root;
    this.objs = objs;
    setHelpAvailable(false);
    extraSettings = null;
    treeNodePage =
        new TreeNodeSelectionPage(
            new StructuredSelection(
                Arrays.asList(
                    SelectionManager.getAllNodes(SelectionManager.getOne().getSelection(EventSource.EXPLORER)))), TreeNodeIDMode.QualifiedId);
    WorkspaceJob j = new WorkspaceJob("Load mass ops and attributes for mass ops"){

        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) {
          getMassOps();
          getASettings();
          return Status.OK_STATUS;
        }
        
      };
      j.schedule();
      //((TreeNodeSelectionPage)treeNodePage).setSelection(new String[]{root.getTreeDB().getFullName(superRoot)});
  }

  protected MassOppDescriptor[] getMassOps() {
    synchronized (this) {
      return API.getMassOppDescriptors(); 
    }
  }

  @Override
  public void addPages() {
    //((TreeNodeSelectionPage)treeNodePage).setSelection(new String[]{superRoot.getTreeDB().getFullName(superRoot)});
    addPage(treeNodePage);
  }

  String lastop = null;

  @Override
  public IWizardPage getNextPage(IWizardPage page) {
    if (TreeNodeSelectionPage.class.isInstance(page)) {
      if (selectOP != null) return selectOP;
      selectOP = new SelectOppsPage(objs);
      return addNextPage(selectOP);
    }
    IWizardPage ret = null;
    if (SelectOppsPage.class.isInstance(page)) {
      DialogSettings dialogSettings = (DialogSettings) this.getDialogSettings();
      String operationName = dialogSettings.get(SelectOppsPage.ID);
      IDialogSettingsParser parser = new DialogSettingsParser(dialogSettings);
      TreeNode root = parser.getRoot(superRoot);
      if ((lastop == null || !lastop.equals(operationName))) {

        if (extraSettings != null && lastop != null && !lastop.equals(operationName)) {
          for (int i = 0; i < extraSettings.length; i++) extraSettings[i].dispose();
        }
        lastop = operationName;
        extraSettings = extractPages(dialogSettings, operationName);
        if (extraSettings == null) RLogger.logDebug("null extra pages for massop "+operationName);
      }
    }
    if (extraSettings != null && extraSettings.length > 0) {
      int j = -1;
      for (int i = 0; i < extraSettings.length; i++)
        if (extraSettings[i].equals(page)) {
          j = i;
          break;
        }
      if (j < (extraSettings.length - 1)) ret = extraSettings[j + 1];
      else if (j < extraSettings.length) ret = super.getNextPage(extraSettings[j]);
      else ret = super.getNextPage(page);
    }
    if (ret == null) ret = super.getNextPage(page);
    if (ret != null) ret.setWizard(this);
    if (ret instanceof AbstractExtraSettingsPage)
      ((AbstractExtraSettingsPage) ret).reloadSettingsFromDS();
    return ret;
  }

  private AbstractExtraSettingsPage[] extractPages(DialogSettings ds, String operationName) {
    MassOppDescriptor mo = getDescriptor(operationName);
    if (mo instanceof MassOppDescriptorWithPages) {
      MassOppDescriptorWithPages mod = (MassOppDescriptorWithPages) mo;
      try {
        List<Class<? extends AbstractExtraSettingsPage>> l = mod.getPages();
        AbstractExtraSettingsPage[] pages = new AbstractExtraSettingsPage[l.size()];
        int i = 0;
        for (Class<? extends AbstractExtraSettingsPage> cl : l)
          pages[i++] =
              cl.getConstructor(DialogSettings.class, Set.class, TreeDB.class)
                  .newInstance(
                      ds,
                      getASettings(),
                      superRoot.getTreeDB());
        return pages;
      } catch (NoSuchMethodException e) {
        e.printStackTrace();
      } catch (SecurityException e) {
        e.printStackTrace();
      } catch (InstantiationException e) {
        e.printStackTrace();
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      } catch (IllegalArgumentException e) {
        e.printStackTrace();
      } catch (InvocationTargetException e) {
        e.printStackTrace();
      }
    }
    return null;
  }
  Set<String> st = null;
  private Object getASettings() {
    synchronized (this) {
      if(st==null)
        st = GetterAttributeNames.getFromRoot(superRoot, filter);
      return st;
    }
  }

  private MassOppDescriptor getDescriptor(String operationName) {
    for (MassOppDescriptor mod : getMassOps())
      if (mod.getId().equals(operationName)) return mod;
    return null;
  }

  @Override
  public boolean performFinish() {
    dialogSettings = (DialogSettings) getDialogSettings();
    DialogSettingsParser parser = new DialogSettingsParser(dialogSettings);
    final TreeNode root = parser.getRoot(superRoot);
    Map<String, String> sett = parser.parse();
    if (extraSettings != null)
      for (AbstractExtraSettingsPage aesp : extraSettings)
        sett.putAll(parser.parse(aesp.getDialogNames()));
    MassOppDescriptor op = getMassOperationDescriptor();
    op.getOp().applySettings(sett);
    WorkspaceJob wj =
        new WorkspaceJob("Mass op") {
          @Override
          public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
    	    if(root == null)
              return Status.CANCEL_STATUS;
            op.getOp().execute(root);
            return Status.OK_STATUS;
          }
        };
    wj.schedule();
    return true;
  }

  private MassOppDescriptor getMassOperationDescriptor() {
    String oppId = dialogSettings.get(SelectOppsPage.ID);
    for (final MassOppDescriptor op : objs)
      if (op.getId().equals(oppId)) {
        return op;
      }
    return null;
  }

  @Override
  public boolean canFinish() {
    if (treeNodePage == null || !treeNodePage.isPageComplete()) return false;
    if (selectOP == null || !selectOP.isPageComplete()) return false;
    dialogSettings = (DialogSettings) getDialogSettings();
    if (extraSettings != null)
      for (AbstractExtraSettingsPage aesp : extraSettings) if (!aesp.isPageComplete()) return false;
    return true;
  }

  private IWizardPage addNextPage(IWizardPage page) {
    if (page == null) return null;
    addPage(page);
    return page;
  }

  public void updateCanFinish() {
    ((WizardDialog)getContainer()).updateButtons();
  }
}
