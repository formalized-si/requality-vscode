package com.unitesk.requality.vscode.ui.ide.markers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.unitesk.requality.vscode.ui.resources.IMarker;

/**
 * The marker fields of the markers views ({@code org.eclipse.ui.ide.markerSupport} markerField elements of
 * {@code org.eclipse.ui.ide}): their ids, the column headers of {@code plugin.properties} (Field.*) and the default
 * column widths of their {@code getDefaultColumnWidth}, in pixels.
 */
public final class MarkerFields {
  /** {@code org.eclipse.ui.ide.completionField}. */
  public static final String COMPLETED = "completed";
  /** {@code org.eclipse.ui.ide.priorityField}. */
  public static final String PRIORITY = "priority";
  /** {@code org.eclipse.ui.ide.descriptionField}. */
  public static final String DESCRIPTION = "description";
  /** {@code org.eclipse.ui.ide.resourceField}. */
  public static final String RESOURCE = "resource";
  /** {@code org.eclipse.ui.ide.pathField}. */
  public static final String PATH = "path";
  /** {@code org.eclipse.ui.ide.locationField}. */
  public static final String LOCATION = "location";
  /** {@code org.eclipse.ui.ide.markerType}. */
  public static final String TYPE = "type";
  /** {@code org.eclipse.ui.ide.creationTimeField}. */
  public static final String CREATION_TIME = "creationTime";
  /** {@code org.eclipse.ui.ide.idField}. */
  public static final String ID = "id";

  /** The fields of the Bookmarks view ({@code org.eclipse.ui.ide.bookmarksGenerator}), in the order of the generator. */
  static final String[] BOOKMARK_FIELDS = { DESCRIPTION, RESOURCE, PATH, LOCATION, ID, CREATION_TIME, TYPE };
  /** The fields of the Tasks view ({@code org.eclipse.ui.ide.tasksGenerator}), in the order of the generator. */
  static final String[] TASK_FIELDS = { COMPLETED, PRIORITY, DESCRIPTION, RESOURCE, PATH, LOCATION, CREATION_TIME,
      TYPE };
  /** The fields of the Problems view ({@code org.eclipse.ui.ide.problemsGenerator}), in the order of the generator. */
  static final String[] PROBLEM_FIELDS = { DESCRIPTION, RESOURCE, PATH, LOCATION, ID, CREATION_TIME, TYPE };

  private MarkerFields() {
  }

  /** The fields of the generator of the marker type, in the order of the generator. */
  public static List<String> generatorFields(String markerType) {
    if (IMarker.PROBLEM.equals(markerType))
      return new ArrayList<>(Arrays.asList(PROBLEM_FIELDS));
    return new ArrayList<>(Arrays.asList(IMarker.TASK.equals(markerType) ? TASK_FIELDS : BOOKMARK_FIELDS));
  }

  /** The column header of a field ({@code Field.*} of plugin.properties). */
  public static String header(String field) {
    switch (field) {
      case COMPLETED:
        return "Completed";
      case PRIORITY:
        return "Priority";
      case DESCRIPTION:
        return "Description";
      case RESOURCE:
        return "Resource";
      case PATH:
        return "Path";
      case LOCATION:
        return "Location";
      case CREATION_TIME:
        return "Creation Time";
      case ID:
        return "ID";
      default:
        return "Type";
    }
  }

  /** The default width of a column, in pixels ({@code getDefaultColumnWidth} of the fields, 7 pixels a character). */
  public static int width(String field) {
    switch (field) {
      case COMPLETED:
        return 80;
      case PRIORITY:
        return 80;
      case DESCRIPTION:
        return 350;
      case PATH:
        return 140;
      case LOCATION:
        return 105;
      case CREATION_TIME:
        return 160;
      case ID:
        return 60;
      default:
        return 140;
    }
  }

  /** Whether the field is shown by default in the view of the marker type ({@code visible} of the generators). */
  public static boolean visibleByDefault(String markerType, String field) {
    if (ID.equals(field) || CREATION_TIME.equals(field))
      return false;
    return !(TYPE.equals(field) && !IMarker.TASK.equals(markerType) && !IMarker.PROBLEM.equals(markerType));
  }
}
