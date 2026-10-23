package com.unitesk.requality.vscode.ui.compare.patch;

import java.text.MessageFormat;

/**
 * {@code org.eclipse.compare.internal.patch.PatchMessages}: the English texts of the Apply Patch wizard (the
 * messages.properties of the compare bundle, as literals). {@link #bind} is {@code NLS.bind}: the MessageFormat of the
 * pattern with the arguments.
 */
public final class PatchMessages {
  public static final String PatcherCompareEditorInput_LocalCopy = "Local Copy";
  public static final String PatcherCompareEditorInput_0 = "Discard Changes?";
  public static final String PatcherCompareEditorInput_AfterPatch = "After Patch";
  public static final String PatchWizard_title = "Apply Patch";
  public static final String PatchWizard_0 = "Patch has rejects";
  public static final String PatchWizard_1 = "The patch you are applying has segments that did not match. Are you sure you want to apply it?";
  public static final String PatchWizard_unexpectedException_message = "Unexpected exception while applying the patch. See log for a detailed error description.";
  public static final String PatcherCompareEditorInput_PatchContents = "Patch Contents";
  public static final String PatcherCompareEditorInput_NotIncluded = "(Not included)";
  public static final String InputPatchPage_title = "Patch Input Specification";
  public static final String InputPatchPage_NoURL = "Please enter an URL";
  public static final String InputPatchPage_message = "Select the patch location.";
  public static final String InputPatchPage_Clipboard = "Clipboard";
  public static final String InputPatchPage_SelectInput = "Apply the patch to the &selected file, folder or project:";
  public static final String InputPatchPage_MalformedURL = "Malformed URL";
  public static final String InputPatchPage_PatchErrorDialog_title = "Patch Error";
  public static final String InputPatchPage_FileButton_text = "Fil&e";
  public static final String InputPatchPage_URLButton_text = "&URL";
  public static final String InputPatchPage_ChooseFileButton_text = "&Browse...";
  public static final String InputPatchPage_UseClipboardButton_text = "&Clipboard";
  public static final String InputPatchPage_UseWorkspaceButton_text = "&Workspace";
  public static final String InputPatchPage_WorkspaceSelectPatch_text = "&Select the location of the patch:";
  public static final String InputPatchPage_NothingSelected_message = "Select a file or folder to be patched";
  public static final String InputPatchPage_ClipboardIsEmpty_message = "Clipboard is empty";
  public static final String InputPatchPage_NoTextInClipboard_message = "Clipboard does not contain text";
  public static final String InputPatchPage_CouldNotReadClipboard_message = "Cannot retrieve clipboard contents";
  public static final String InputPatchPage_CannotLocatePatch_message = "Cannot locate patch file:";
  public static final String InputPatchPage_NoFileName_message = "No file name";
  public static final String InputPatchPage_FileSelectedNotPatch_message = "Selected resource is not a valid patch";
  public static final String InputPatchPage_SelectPatchFileDialog_title = "Select Patch File";
  public static final String InputPatchPage_PatchFileNotFound_message = "Patch file not found.";
  public static final String InputPatchPage_ParseError_message = "Error while parsing patch";
  public static final String InputPatchPage_Clipboard_title = "Clipboard";
  public static final String InputPatchPage_PatchFile_title = "Patch file";
  public static final String InputPatchPage_URL_title = "URL";
  public static final String InputPatchPage_WorkspacePatch_title = "Workspace file";
  public static final String InputPatchPage_NoDiffsFound_format = "{0} does not contain valid patch.";
  public static final String InputPatchPage_SingleFileError_format = "{0} contains multiple patches. You cannot apply them to a single file.";
  public static final String InputPatchPage_URLConnecting = "Opening connection to the URL";
  public static final String InputPatchPage_URLFetchingContent = "Fetching content from the URL";
  public static final String PatchTargetPage_title = "Target Resource";
  public static final String PatchTargetPage_0 = "Apply the patch to the &workspace root";
  public static final String PatchTargetPage_message = "Select the target workspace resource for the patch.";
  public static final String PatchCompareEditorInput_0 = "(file does not exist)";
  public static final String PreviewPatchPage_title = "Review Patch";
  public static final String PreviewPatchPage2_0 = "&Exclude";
  public static final String PreviewPatchPage2_1 = "&Include";
  public static final String PreviewPatchPage2_2 = "Performing this operation will require that your manual changes be discarded.";
  public static final String PreviewPatchPage2_3 = "Reversing the patch will require that your manual changes be discarded.";
  public static final String PreviewPatchPage2_4 = "Performing this operation will require that your manual changes be discarded.";
  public static final String PreviewPatchPage2_5 = "Changing the fuzz factor will require that your manual changes be discarded.";
  public static final String PreviewPatchPage2_6 = "Changing the fuzz factor will require that your manual changes be discarded.";
  public static final String PreviewPatchPage2_7 = "&Show Excluded";
  public static final String PreviewPatchPage2_8 = "Review the patch with respect to the local file system and manually merge any unmatched portions.";
  public static final String PreviewPatchPage2_9 = "Double-click on file or patch segment entries to view their content:";
  public static final String PreviewPatchPage_Target = "(target: {0})";
  public static final String PreviewPatchPage_PatchOptions_title = "Patch options";
  public static final String PreviewPatchPage_IgnoreSegments_text = "&Ignore leading path name segments:";
  public static final String PreviewPatchPage_ReversePatch_text = "&Reverse patch";
  public static final String PreviewPatchPage_FuzzFactor_text = "Fu&zz factor:";
  public static final String PreviewPatchPage2_RetargetAction = "&Move";
  public static final String PreviewPatchPage2_RetargetTooltip = "Move the selected patch element to another resource";
  public static final String PreviewPatchPage2_OrphanedHunk = "Unmatched Patch Segment";
  public static final String PreviewPatchPage2_MatchedHunk = "Matched Hunk";
  public static final String PreviewPatchPage2_IgnoreWSAction = "Ignore whitespace";
  public static final String PreviewPatchPage_FuzzFactor_tooltip = "Allow this number of context lines to be ignored";
  public static final String PreviewPatchPage2_IgnoreWSTooltip = "Ignore whitespace";
  public static final String PreviewPatchPage2_IgnoreWhitespace = "Ignore whitespace";
  public static final String PreviewPatchPage2_PatchedLocalFile = "Patched Local File";
  public static final String PreviewPatchPage2_CalculateReverse = "Calculating reverse";
  public static final String PreviewPatchPage_RetargetPatch = "Retarget Patch";
  public static final String PreviewPatchPage_SelectProject = "Select the new target project for the portion of the patch targeted to project ''{0}'':";
  public static final String PreviewPatchPage_GuessFuzz_text = "&Guess";
  public static final String PreviewPatchPage_FuzzUsed = "(fuzz factor used: {0})";
  public static final String PreviewPatchPage_AllContextIgnored = "(fuzz factor used: {0}, all context lines ignored)";
  public static final String PreviewPatchPage2_ShowMatched = "Show &matched hunks";
  public static final String PreviewPatchPage2_AddedRemovedLines = "Patch contains {0} added and {1} removed lines.";
  public static final String PreviewPatchLabelDecorator_ProjectDoesNotExist = "(Project does not exist in workspace)";
  public static final String Diff_2Args = "{0} {1}";
  public static final String HunkMergePage_Merged = "(merged)";
  public static final String HunkMergePage_GenerateRejectFile = "G&enerate a .rej file for unmerged hunks";
  public static final String RetargetPatchElementDialog_0 = "Select the new target file for the portion of the patch targeted to file ''{0}''";
  public static final String RetargetPatchElementDialog_1 = "Select the new target file for this portion of the patch targeted to file ''{0}''";

  private PatchMessages() {
  }

  /** {@code NLS.bind}: the pattern with its {0}, {1}... arguments (MessageFormat quoting). */
  static String bind(String pattern, Object... args) {
    return MessageFormat.format(pattern, args);
  }
}
