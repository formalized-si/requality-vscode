package com.unitesk.requality.vscode.ui.team.preferences;

import java.text.Collator;
import java.util.Collection;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.viewers.CellEditor;
import com.unitesk.requality.vscode.ui.jface.viewers.ComboBoxCellEditor;
import com.unitesk.requality.vscode.ui.jface.viewers.ICellModifier;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ITableLabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProviderListener;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.TableColumn;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.team.core.Team;

/**
 * {@code org.eclipse.team.internal.ui.preferences.FileTypeTable}: the table of the name and extension patterns of the File
 * Content page. The Name/Extension column shows the pattern ({@code *.ext} or a file name), the Content column its content
 * (Binary or ASCII Text), which is changed in place with a combo box. The extensions are listed before the names, and a
 * filter text shows the patterns that contain its text.
 * VSCODE: the Remember decision column of the save option is not shown (the File Content page does not show it in Eclipse).
 */
public class FileTypeTable implements ICellModifier, IStructuredContentProvider, ITableLabelProvider {
  /** {@code FileTypeTable.COMBO_INDEX_BINARY}: the index of Binary in the content combo box. */
  private static final int COMBO_INDEX_BINARY = 0;
  /** {@code FileTypeTable.COMBO_INDEX_TEXT}: the index of ASCII Text in the content combo box. */
  private static final int COMBO_INDEX_TEXT = 1;
  /** {@code FileTypeTable.MODES_TEXT}: the labels of the content modes. */
  private static final String[] MODES_TEXT = { "Binary", "ASCII Text" };
  private static final int COLUMN_NAME = 0;
  private static final int COLUMN_MODE = 1;
  private static final String ITEM = "item";
  private static final String PROPERTY_MODE = "mode";

  /** {@code FileTypeTable.Comparator}: the extensions before the names, each sorted by its name. */
  private static final class FileTypeComparator extends ViewerComparator {
    private static int getCategory(Object element) {
      if (element instanceof Extension)
        return 0;
      if (element instanceof Name)
        return 1;
      return 2;
    }

    @Override
    public int compare(Viewer viewer, Object e1, Object e2) {
      int compare = getCategory(e1) - getCategory(e2);
      if (compare != 0)
        return compare;
      return Collator.getInstance().compare(((Item) e1).name, ((Item) e2).name);
    }
  }

  /** {@code FileTypeTable.Item}: a name or extension pattern, with its content mode. */
  public abstract static class Item {
    public final String name;
    public final boolean contributed;
    /** The content mode: {@link Team#TEXT} or {@link Team#BINARY}. */
    public int mode;

    public Item(String name, boolean contributed) {
      this.name = name;
      this.contributed = contributed;
      this.mode = Team.BINARY;
    }
  }

  /** {@code FileTypeTable.Extension}: a file extension pattern (*.ext). */
  public static class Extension extends Item {
    public Extension(String name, boolean contributed) {
      super(name, contributed);
    }
  }

  /** {@code FileTypeTable.Name}: a file name pattern. */
  public static class Name extends Item {
    public Name(String name, boolean contributed) {
      super(name, contributed);
    }
  }

  private final TableViewer tableViewer;
  private final List<Item> items;

  /** Creates the table with the filter text and the items as its input. */
  public FileTypeTable(Composite composite, List<Item> items) {
    this.items = items;
    Composite tableComposite = new Composite(composite, SWT.NONE);
    tableComposite.setLayout(new GridLayout(1, false));
    tableComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    // the filter text
    Text filterText = new Text(tableComposite, SWT.SEARCH | SWT.ICON_CANCEL);
    filterText.setMessage("type filter text");
    filterText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    // the table
    Table table = new Table(tableComposite, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION);
    GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
    gd.heightHint = 300;
    table.setLayoutData(gd);
    table.setLinesVisible(true);
    table.setHeaderVisible(true);
    TableColumn fileColumn = new TableColumn(table, SWT.NONE, COLUMN_NAME);
    fileColumn.setWidth(220);
    fileColumn.setText("Name/Extension");
    TableColumn modeColumn = new TableColumn(table, SWT.NONE, COLUMN_MODE);
    modeColumn.setWidth(120);
    modeColumn.setText("Content");

    tableViewer = new TableViewer(table);
    tableViewer.setContentProvider(this);
    tableViewer.setLabelProvider(this);
    tableViewer.setComparator(new FileTypeComparator());
    // the content is edited in place (Eclipse: a combo box cell editor in the Content column)
    ComboBoxCellEditor modeEditor = new ComboBoxCellEditor(table, MODES_TEXT, SWT.READ_ONLY);
    tableViewer.setCellEditors(new CellEditor[] { null, modeEditor });
    tableViewer.setColumnProperties(new String[] { ITEM, PROPERTY_MODE });
    tableViewer.setCellModifier(this);
    tableViewer.setInput(items);

    tableViewer.addFilter(new ViewerFilter() {
      @Override
      public boolean select(Viewer viewer, Object parentElement, Object element) {
        String inputText = filterText.getText().toLowerCase().trim();
        if (inputText.isEmpty())
          return true;
        Item text = (Item) element;
        return text.name != null && text.name.toLowerCase().contains(inputText);
      }
    });
    filterText.addModifyListener(e -> tableViewer.refresh());
  }

  @Override
  public Object getValue(Object element, String property) {
    Item item = (Item) element;
    if (PROPERTY_MODE.equals(property)) {
      if (item.mode == Team.BINARY)
        return Integer.valueOf(COMBO_INDEX_BINARY);
      if (item.mode == Team.TEXT)
        return Integer.valueOf(COMBO_INDEX_TEXT);
    }
    return null;
  }

  @Override
  public boolean canModify(Object element, String property) {
    return PROPERTY_MODE.equals(property);
  }

  @Override
  public void modify(Object element, String property, Object value) {
    Item item = (Item) element;
    int comboIndex = ((Integer) value).intValue();
    if (PROPERTY_MODE.equals(property)) {
      if (comboIndex == COMBO_INDEX_BINARY)
        item.mode = Team.BINARY;
      if (comboIndex == COMBO_INDEX_TEXT)
        item.mode = Team.TEXT;
    }
    tableViewer.refresh(item);
  }

  @Override
  public Image getColumnImage(Object element, int columnIndex) {
    return null;
  }

  @Override
  public String getColumnText(Object element, int columnIndex) {
    Item item = (Item) element;
    if (columnIndex == COLUMN_NAME)
      return (item instanceof Extension ? "*." : "") + item.name;
    if (columnIndex == COLUMN_MODE) {
      if (item.mode == Team.BINARY)
        return MODES_TEXT[COMBO_INDEX_BINARY];
      if (item.mode == Team.TEXT)
        return MODES_TEXT[COMBO_INDEX_TEXT];
    }
    return null;
  }

  @Override
  public void addListener(ILabelProviderListener listener) {
  }

  @Override
  public void dispose() {
  }

  @Override
  public boolean isLabelProperty(Object element, String property) {
    return false;
  }

  @Override
  public void removeListener(ILabelProviderListener listener) {
  }

  @Override
  public Object[] getElements(Object inputElement) {
    return ((Collection<?>) inputElement).toArray();
  }

  @Override
  public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
  }

  /** The selected items of the table. */
  public IStructuredSelection getSelection() {
    return tableViewer.getStructuredSelection();
  }

  /** The viewer of the table. */
  public TableViewer getViewer() {
    return tableViewer;
  }
}
