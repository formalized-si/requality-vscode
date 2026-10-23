package com.unitesk.requality.vscode.ui.ide.datatransfer;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.DataTransferMessages}: the texts of the smart import (the English
 * values of messages.properties). The messages with arguments are formatted by {@link #bind(String, Object...)}.
 */
public final class DataTransferMessages {
  public static final String DataTransfer_browse = "B&rowse..."; //$NON-NLS-1$
  public static final String DataTransfer_selectAll = "&Select All"; //$NON-NLS-1$
  public static final String DataTransfer_deselectAll = "&Deselect All"; //$NON-NLS-1$
  public static final String DataTransfer_refresh = "R&efresh"; //$NON-NLS-1$

  public static final String SmartImportWizardPage_selectFolderOrArchiveToImport = "Select the folder to find projects to import"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_browseForFolder = "Browse for Folder"; //$NON-NLS-1$
  public static final String SmartImportProposals_alreadyImportedAsProject_title = "Folder already imported as project"; //$NON-NLS-1$
  public static final String SmartImportProposals_anotherProjectWithSameNameExists_title = "Project with same name already imported"; //$NON-NLS-1$
  public static final String SmartImportProposals_anotherProjectWithSameNameExists_description = "Could not perform operation:\n\nA project with name ''{0}'' already exists in workspace, and the folder you''re trying to import contains a project description file (.project) using this same name.\n\nYou should consider renaming either the project in workspace, or editing the .project in selected folder."; //$NON-NLS-1$
  public static final String SmartImportWizardPage_importProjectsInFolderTitle = "Import Projects from File System or Archive"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_importProjectsInFolderDescription = "This wizard analyzes the content of your folder or archive file to find projects and import them in the IDE."; //$NON-NLS-1$
  public static final String SmartImportWizardPage_selectRootDirectory = "&Import source:"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_incorrectRootDirectory = "Invalid directory or archive file"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_browse = "Di&rectory..."; //$NON-NLS-1$
  public static final String SmartImportWizardPage_selectArchiveButton = "&Archive..."; //$NON-NLS-1$
  public static final String SmartImportWizardPage_selectArchiveTitle = "Select an archive file to import"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_workingSets = "Working Sets"; //$NON-NLS-1$
  public static final String SmartImportReport_importedProjects = "Imported Projects"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_detectNestedProjects = "Search for nested &projects"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_configureProjects = "Detect and &configure project natures"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_showAvailableDetectors = "Use <a>installed project configurators</a> to:"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_availableDetectors_title = "Installed project configurators"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_availableDetectors_description = "The following project configurators are installed and available to detect and configure projects:"; //$NON-NLS-1$
  public static final String SmartImportReport_importedProjectsWithCount = "Imported {0} projects"; //$NON-NLS-1$
  public static final String SmartImportReport_relativePath = "Relative Path"; //$NON-NLS-1$
  public static final String SmartImportReport_project = "Project"; //$NON-NLS-1$
  public static final String SmartImportReport_natures = "Imported as"; //$NON-NLS-1$
  public static final String SmartImportReport_importErrors = "Skipped {0} locations because of errors"; //$NON-NLS-1$
  public static final String SmartImportReport_error = "Error"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_scanProjectsFailed = "Unable to scan for projects"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_allSupportedArchives = "All supported archives"; //$NON-NLS-1$
  public static final String SmartImportJob_discardRootProject_title = "Discard root project"; //$NON-NLS-1$
  public static final String SmartImportJob_discardRootProject_description = "It seems like the root directory you specified doesn't contain much development data. Do you want to discard it?\nWhile keeping the root project may be helpful for navigation purpose (especially if you use nested view of projects), discarding it may improve the overall performances of your IDE."; //$NON-NLS-1$
  public static final String SmartImportProposals_selectionSummary = "{0} of {1} selected"; //$NON-NLS-1$
  public static final String SmartImportProposals_folder = "Folder"; //$NON-NLS-1$
  public static final String SmartImportProposals_importAs = "Import as"; //$NON-NLS-1$
  public static final String SmartImportProposals_hideExistingProjects = "&Hide already open projects"; //$NON-NLS-1$
  public static final String SmartImportProposals_inspecitionCanceled = "Inspeciton canceled"; //$NON-NLS-1$
  public static final String SmartImportProposals_errorWhileInspecting = "Error while inspecting: {0}"; //$NON-NLS-1$
  public static final String SmartImportJob_detectAndConfigureProjects = "Detecting and configuring nested projects"; //$NON-NLS-1$
  public static final String SmartImportJob_configuringSelectedDirectories = "Configuring selected directories"; //$NON-NLS-1$
  public static final String SmartImportJob_configuring = "Configuring project {0}"; //$NON-NLS-1$
  public static final String SmartImportJob_crawling = "Crawling {0}"; //$NON-NLS-1$
  public static final String SmartImportJob_continuingConfiguration = "Continue configuration of project {0}"; //$NON-NLS-1$
  public static final String SmartImportJob_inspecting = "Inspecting location {0}"; //$NON-NLS-1$
  public static final String SmartImportJob_importingProjectIntoWorkspace = "Import project at {0} into workspace"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_expandingArchive = "Expanding {0} into {1}"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_overwriteArchiveDirectory_title = "Overwrite directory content"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_overwriteArchiveDirectory_message = "This archive seems to be already expanded in directory {0}. Do you want to overwrite the content of this directory and re-expand archive there?"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_incompleteExpand_title = "Incomplete expansion"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_incompleteExpand_message = "Archive expansion didn''t complete successfully. It''s recommend that you clean directory {0} and try importing again."; //$NON-NLS-1$
  public static final String SmartImportWizardPage_selectAtLeastOneFolderToOpenAsProject = "Select at least one folder to import as project."; //$NON-NLS-1$
  public static final String SmartImportWizardPage_showOtherSpecializedImportWizard = "Show other specialized import wizards"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_closeProjectsAfterImport = "Cl&ose newly imported projects upon completion"; //$NON-NLS-1$
  public static final String SmartImportWizardPage_skipDotFolders = "S&kip folders starting with '.' (e.g., .git)"; //$NON-NLS-1$

  private DataTransferMessages() {
  }

  /** {@code NLS.bind}: the message with its {0}, {1}, ... arguments (the '' of the pattern is one quote). */
  public static String bind(String message, Object... args) {
    return java.text.MessageFormat.format(message, args);
  }
}
