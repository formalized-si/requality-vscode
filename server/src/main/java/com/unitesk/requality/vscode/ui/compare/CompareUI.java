package com.unitesk.requality.vscode.ui.compare;

import java.io.IOException;
import java.io.InputStream;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.compare.contentmergeviewer.TextMergeViewer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.DiffNode;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IStructureCreator;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.StructureDiffViewer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.team.LocalResourceTypedElement;
import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;

/**
 * {@code org.eclipse.compare.CompareUI}: opens compare editors and dialogs on compare editor inputs, and finds the
 * structure and content viewers of the inputs in the registry ({@code org.eclipse.compare.contentMergeViewers},
 * {@code contentViewers}, {@code structureMergeViewers}) with the text and binary viewers as fallbacks.
 */
public final class CompareUI {
  public static final String PLUGIN_ID = "org.eclipse.compare";
  /** {@code org.eclipse.compare.CompareUI.COMPARE_VIEWER_TITLE}: widget data key of the viewer title. */
  public static final String COMPARE_VIEWER_TITLE = "org.eclipse.compare.CompareUI.CompareViewerTitle";

  private static final String TEXT_TYPE = ITypedElement.TEXT_TYPE;
  private static final String BINARY_TYPE = "binary";

  private CompareUI() {
  }

  /** {@code CompareUI.getImage(String)}: the image of a type of element (a folder or a file). */
  // VSCODE: file type images come from the VS Code file icon theme; the compare viewers use the workbench images.
  public static Image getImage(String type) {
    ISharedImages si = PlatformUI.getWorkbench().getSharedImages();
    return si.getImage(ITypedElement.FOLDER_TYPE.equals(type) ? ISharedImages.IMG_OBJ_FOLDER : ISharedImages.IMG_OBJ_FILE);
  }

  /** {@code CompareUI.getImage(IAdaptable)}: the workbench image of a resource. */
  public static Image getImage(Object adaptable) {
    return adaptable == null ? null : new WorkbenchLabelProvider().getImage(adaptable);
  }

  /** The extension of a file name (empty when there is none). */
  public static String extension(String name) {
    return name != null && name.lastIndexOf('.') >= 0 ? name.substring(name.lastIndexOf('.') + 1) : "";
  }

  /** The {@code <viewer>} of org.eclipse.compare.contentMergeViewers whose extensions include {@code ext}, or null. */
  public static IConfigurationElement viewerFor(String ext) {
    return viewer("org.eclipse.compare.contentMergeViewers", ext);
  }

  private static IConfigurationElement viewer(String point, String ext) {
    if (ext == null)
      return null;
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor(point)) {
      if (!"viewer".equals(e.getName()) || e.getAttribute("extensions") == null)
        continue;
      for (String x : e.getAttribute("extensions").split(","))
        if (x.trim().equalsIgnoreCase(ext))
          return e;
    }
    return null;
  }

  /** {@code CompareUI.openCompareEditor}: runs the input and opens the compare editor on its result. */
  public static void openCompareEditor(CompareEditorInput input) {
    CompareEditor.open(input);
  }

  public static void openCompareEditor(CompareEditorInput input, boolean activate) {
    CompareEditor.open(input);
  }

  public static void openCompareEditorOnPage(CompareEditorInput input, Object page) {
    CompareEditor.open(input);
  }

  /** {@code CompareUI.reuseCompareEditor}. VSCODE: a new compare editor is opened (VS Code reuses an equal diff editor). */
  public static void reuseCompareEditor(CompareEditorInput input, Object editor) {
    CompareEditor.open(input);
  }

  /** {@code CompareUI.openCompareDialog}: runs the input and shows its result in a modal compare dialog. */
  public static void openCompareDialog(CompareEditorInput input) {
    if (CompareEditor.compareResultOK(input, null))
      new CompareDialog(null, input).open();
  }

  /** The structure viewer registered for the type of the input, or null (none are contributed). */
  public static Viewer findStructureViewer(Viewer oldViewer, ICompareInput input, Composite parent,
      CompareConfiguration configuration) {
    String type = commonType(input);
    IConfigurationElement vd = type == null ? null : viewer("org.eclipse.compare.structureMergeViewers", type);
    if (vd != null)
      return create(vd, oldViewer, parent, configuration);
    // no viewer is registered for the type: the structure of the type (an archive) is compared by the generic viewer
    IStructureCreator creator = structureCreator(input);
    if (creator == null)
      return null;
    StructureDiffViewer sdv = new StructureDiffViewer(parent, configuration);
    sdv.setStructureCreator(creator);
    return sdv;
  }

  /** Whether the structure of the input is shown in a structure compare (its type has a structure creator). */
  public static boolean hasStructureCreator(Object input) {
    return input instanceof ICompareInput && structureCreatorElement((ICompareInput) input) != null;
  }

  /** The structure creator registered for the type of the input ({@code org.eclipse.compare.structureCreators}), or null. */
  // VSCODE: the structure creators are found by the type of the input (file extension), not by its content type.
  static IStructureCreator structureCreator(ICompareInput input) {
    IConfigurationElement sc = structureCreatorElement(input);
    if (sc == null)
      return null;
    try {
      return (IStructureCreator) sc.createExecutableExtension("class");
    } catch (CoreException | ClassCastException e) {
      RLogger.logError(e);
      return null;
    }
  }

  private static IConfigurationElement structureCreatorElement(ICompareInput input) {
    String type = commonType(input);
    if (type == null)
      return null;
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.compare.structureCreators")) {
      if (!"structureCreator".equals(e.getName()) || e.getAttribute("extensions") == null)
        continue;
      for (String x : e.getAttribute("extensions").split(","))
        if (x.trim().equalsIgnoreCase(type))
          return e;
    }
    return null;
  }

  /** The content merge viewer of the input: the one registered for its type, else the text or binary compare viewer. */
  public static Viewer findContentViewer(Viewer oldViewer, ICompareInput input, Composite parent,
      CompareConfiguration configuration) {
    if (input == null)
      return null;
    String type = commonType(input);
    if (ITypedElement.FOLDER_TYPE.equals(type))
      return null;
    if (type != null) {
      IConfigurationElement vd = viewerFor(type);
      if (vd != null)
        return create(vd, oldViewer, parent, configuration);
    }
    String leftType = guessType(input.getLeft());
    String rightType = guessType(input.getRight());
    if (leftType == null && rightType == null)
      return null;
    if (rightType != null && !TEXT_TYPE.equals(rightType) || leftType != null && !TEXT_TYPE.equals(leftType))
      return oldViewer instanceof BinaryCompareViewer ? oldViewer : new BinaryCompareViewer(parent, configuration);
    return oldViewer instanceof TextMergeViewer && oldViewer.getClass() == TextMergeViewer.class ? oldViewer
        : new TextMergeViewer(parent, configuration);
  }

  /** The content viewer of a single element: the one registered for its type, else a text viewer. */
  public static Viewer findContentViewer(Viewer oldViewer, Object input, Composite parent,
      CompareConfiguration configuration) {
    if (input instanceof ICompareInput)
      return findContentViewer(oldViewer, (ICompareInput) input, parent, configuration);
    if (!(input instanceof ITypedElement))
      return null;
    ITypedElement e = (ITypedElement) input;
    if (ITypedElement.FOLDER_TYPE.equals(e.getType()))
      return null;
    IConfigurationElement vd = viewer("org.eclipse.compare.contentViewers", e.getType());
    if (vd != null)
      return create(vd, oldViewer, parent, configuration);
    if (TEXT_TYPE.equals(guessType(e)))
      return oldViewer instanceof SimpleTextViewer ? oldViewer : new SimpleTextViewer(parent);
    return null;
  }

  /** A new viewer of a viewer creator. */
  private static Viewer create(IConfigurationElement vd, Viewer oldViewer, Composite parent,
      CompareConfiguration configuration) {
    try {
      IViewerCreator creator = (IViewerCreator) vd.createExecutableExtension("class");
      return creator.createViewer(parent, configuration);
    } catch (CoreException | ClassCastException e) {
      RLogger.logError(e);
      return null;
    }
  }

  /** The common type of the sides of an input (null when they differ). */
  static String commonType(ICompareInput input) {
    String type = null;
    int n = 0;
    String[] types = types(input);
    boolean homogenous = types.length > 0;
    for (String t : types)
      if (!t.equals(types[0]))
        homogenous = false;
    if (homogenous)
      return types[0];
    for (String t : types) {
      if (!ITypedElement.UNKNOWN_TYPE.equals(t)) {
        n++;
        if (type == null)
          type = t;
      }
    }
    return n > 1 ? null : type;
  }

  private static String[] types(ICompareInput input) {
    java.util.List<String> r = new java.util.ArrayList<>();
    for (ITypedElement e : new ITypedElement[] { input.getAncestor(), input.getLeft(), input.getRight() })
      if (e != null && e.getType() != null)
        r.add(e.getType().toLowerCase());
    return r.toArray(new String[0]);
  }

  /** "txt" when the first bytes of the contents have no zero byte, "binary" otherwise, null without contents. */
  static String guessType(ITypedElement input) {
    if (!(input instanceof IStreamContentAccessor))
      return null;
    try (InputStream is = ((IStreamContentAccessor) input).getContents()) {
      if (is == null)
        return null;
      int max = 1024;
      for (int c, count = 0; (c = is.read()) != -1 && count < max; count++)
        if (c == 0)
          return BINARY_TYPE;
      return TEXT_TYPE;
    } catch (CoreException | IOException e) {
      return null;
    }
  }

  /**
   * Opens two elements in a compare editor ({@code compare/open} of the extension: Requality node files): the editor of
   * their content merge viewer, or the text compare. Returns false when nothing can show them.
   */
  public static boolean openCompareEditor(String title, ITypedElement left, ITypedElement right, String leftLabel,
      String rightLabel) {
    return openCompareEditor(title, left, right, leftLabel, rightLabel, null, null);
  }

  /**
   * As {@link #openCompareEditor(String, ITypedElement, ITypedElement, String, String)}, and the editor is linked with the
   * element of a view (see {@link CompareLinks}): selecting the element shows it, and its activation selects the element.
   */
  public static boolean openCompareEditor(String title, ITypedElement left, ITypedElement right, String leftLabel,
      String rightLabel, Object linkElement, java.util.function.Consumer<Object> linkSelect) {
    CompareConfiguration config = new CompareConfiguration();
    config.setLeftLabel(leftLabel);
    config.setRightLabel(rightLabel);
    config.setLeftEditable(left instanceof LocalResourceTypedElement);
    config.setRightEditable(right instanceof LocalResourceTypedElement);
    CompareEditorInput input = new CompareEditorInput(config) {
      @Override
      protected Object prepareInput(IProgressMonitor monitor) {
        return new DiffNode(null, Differencer.CHANGE, null, left, right);
      }
    };
    input.setTitle(title == null ? "Compare" : title);
    CompareLinks.expect(input, linkElement, linkSelect);
    CompareEditor.open(input);
    return true;
  }
}
