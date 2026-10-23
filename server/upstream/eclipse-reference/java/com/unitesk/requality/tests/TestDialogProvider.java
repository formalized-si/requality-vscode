package com.unitesk.requality.tests;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.dialogs.ListSelectionDialog;
import org.eclipse.ui.dialogs.SaveAsDialog;
import org.eclipse.ui.wizards.IWizardDescriptor;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.descriptors.TestGeneratorDescriptor;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.wizards.ITestGenWizard;

public class TestDialogProvider {
  public static final String CREATE_DIALOG = Consts.TEST_DIALOG_PROVIDER_CREATE_NAME;
  private Shell shell;
  public static TestDialogProvider getOne(){
    return getTestDialogProvider();
  }
  public TestDialogProvider(Shell s) {
    shell = s;
    WorkspaceJob j = new WorkspaceJob("Load descriptions for test gens"){

        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) {
          synchronized (TestDialogProvider.this) {
            Activator.getDefault().getTestGenerators(); 
          }
          return Status.OK_STATUS;
        }
      
    };
    j.schedule();
  }
  
  private static TestDialogProvider testDialog;

  private static TestDialogProvider getTestDialogProvider() {
    if (testDialog == null)
      testDialog =
          new TestDialogProvider(
              PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
    return testDialog;
  }

  public boolean fireTestCreation(TreeDB db, String qid, Shell shell2) {
    TreeNode target = null;
    try {
      target = db.getNodeExc(qid, Path.QID | Path.UV);
    } catch (Exception e) {
      return false;
    }
    SortedSet<TestGeneratorDescriptor> set, fset;
    synchronized (TestDialogProvider.this) {
      set = Activator.getDefault().getTestGenerators(); 
    }
    
    List<String> filters = db.getTestGeneratorsFilters();
    fset = new TreeSet<>();
    if (filters != null) {
      for (TestGeneratorDescriptor tgd : set)
        if (filters.contains(tgd.getId()) && tgd.getGenerator().isEnabled(target)) fset.add(tgd);
    } else
      for (TestGeneratorDescriptor tgd : set)
        if (tgd.getGenerator().isEnabled(target)) fset.add(tgd);

    TestGeneratorDescriptor choosed;

    if (fset.size() == 0) {
      MessageDialog.openInformation(
          shell2,
          "Test generators not available",
          "There are no available test generators. Please install plugins with test generators");
      return false;
    } else {
      choosed = fset.first();
      if (fset.size() > 1) {
        choosed = fireSelectionFromList(fset, target.getUserVisibleName());
      }
    }
    if (choosed == null) return false;
    ITestGenerator gen = choosed.getGenerator();
    if (gen == null) return false;
    TreeNode ftarget = target;
    if(target == null)
      return false;
    WorkspaceJob j = new WorkspaceJob("Create test for "+target.getUserVisibleName())
    {

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        gen.generateTest(ftarget, getTestDialogProvider());
        return Status.OK_STATUS;
      }
  
    };
    j.schedule();
    return true;//TODO test
  }

  public static TestGeneratorDescriptor fireSelectionFromList(Set<TestGeneratorDescriptor> gens, String node) {
    ElementListSelectionDialog dialog =
        new ElementListSelectionDialog(
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), new LabelProvider());

    dialog.setTitle("Test Generator Selection (on "+node+")");
    dialog.setMessage("Select a String (* = any string, ? = any char):");

    dialog.setElements(gens.toArray());

    int res = dialog.open();
    if (res == ListSelectionDialog.OK) return (TestGeneratorDescriptor) dialog.getResult()[0];
    return null;
  }

  public OperationResult openCreateFileInEclipseProjectDialog(String extenstion, String title) {
    SaveAsDialog sad = new SaveAsDialog(shell);
    sad.setTitle(title);
    int res = sad.open();

    return new OperationResult(
        sad.getResult().toFile().getAbsolutePath() + "." + extenstion, res == SaveAsDialog.OK);
  }

  public OperationResult openCreateFileDialog(String extenstion, String title) {
    FileDialog dialog = new FileDialog(shell, SWT.SAVE);
    dialog.setText(title);
    String[] filterNames = new String[] {extenstion + " files", "All Files (*)"};
    String[] filterExtensions = new String[] {"*." + extenstion, "*"};

    dialog.setFilterNames(filterNames);
    dialog.setFilterExtensions(filterExtensions);

    String res = dialog.open();

    return new OperationResult(res == null ? "" : res, res != null);
  }

  public OperationResult openWizard(String wizardid, TreeNode target) {
    return openWizard(null, wizardid, target);
  }

  public OperationResult openWizard(Pair<IWizard, WizardDialog> wd, String wizardid, TreeNode target) {
    if (wd == null) wd = getWizard(wizardid, target);
    if (wd == null) return new OperationResult("Wizard not activated", false);
    AtomicBoolean ab = new AtomicBoolean(false);
    String message = null;
    try {
      Pair<IWizard, WizardDialog> fwd = wd;
      EclipseAPI.getDisplay().syncExec(new Runnable(){
      
        @Override
        public void run() {
          if(fwd.getSecond()==null)
            return;
          ab.set(fwd.getSecond().open() == WizardDialog.OK);
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
    }
    //TODO check
    if (wd != null) {
      String id = ((ITestGenWizard) wd.getFirst()).getTargetQID();
      if (target.getTreeDB().getFullName(id) != null) message = target.getTreeDB().getFullName(id);
    }
    return new OperationResult(message, ab.get());
  }

  public Pair<IWizard ,WizardDialog> getWizard(String wizardId, TreeNode targetNode) {
      // First see if this is a "new wizard".
    IWizardDescriptor descriptor =
        PlatformUI.getWorkbench().getNewWizardRegistry().findWizard(wizardId);
    // If not check if it is an "import wizard".
    if (descriptor == null) {
      descriptor = PlatformUI.getWorkbench().getImportWizardRegistry().findWizard(wizardId);
    }
    // Or maybe an export wizard
    if (descriptor == null) {
      descriptor = PlatformUI.getWorkbench().getExportWizardRegistry().findWizard(wizardId);
    }
    try {
      // 	Then if we have a wizard, open it.
      if (descriptor != null) {
        wizard = descriptor.createWizard();
        if (wizard instanceof ITestGenWizard) {
          WizardDialog wd = new WizardDialog(shell, wizard);
          wd.setTitle(wizard.getWindowTitle());
          ((ITestGenWizard) wizard).setTreeDB(targetNode.getTreeDB());
          ((ITestGenWizard) wizard)
              .setTargetQID(targetNode.getTreeDB().getFullName(targetNode), false);
          return new Pair<>(wizard, wd);
        }
      }
    } catch (CoreException e) {
      e.toString();
    }
    return null;
  }

  private IWizard wizard;

  public IWizard getLastWizard() {
    return wizard;
  }
}
