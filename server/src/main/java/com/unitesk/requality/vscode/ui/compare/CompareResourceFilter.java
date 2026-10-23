package com.unitesk.requality.vscode.ui.compare;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@code org.eclipse.compare.internal.CompareResourceFilter}: the members that the "Filtered Members" of the Compare/Patch
 * preferences exclude from the compares: file name patterns ({@code *} and {@code ?} wild cards, e.g. {@code *.class}),
 * and folder names ending with {@code /} (e.g. {@code bin/}), comma separated.
 */
public class CompareResourceFilter {
  private Pattern[] fFileFilters;
  private String[] fFolderFilters;

  public CompareResourceFilter() {
    // no filters
  }

  /** Whether the member of the given name is filtered (true when it must not be compared). */
  public boolean filter(String name, boolean folder, boolean isArchive) {
    if (!folder && fFileFilters != null)
      for (Pattern p : fFileFilters)
        if (p.matcher(name).matches())
          return true;
    if (folder && fFolderFilters != null)
      for (String f : fFolderFilters)
        if (f.equals(name))
          return true;
    return false;
  }

  /** Sets the filters of the comma separated text of the preference; null or empty removes them all. */
  public void setFilters(String filterSequence) {
    if (filterSequence == null || filterSequence.isEmpty()) {
      fFileFilters = null;
      fFolderFilters = null;
      return;
    }
    List<Pattern> files = new ArrayList<>();
    List<String> folders = new ArrayList<>();
    for (String token : filterSequence.split(",")) {
      String filter = token.trim();
      if (filter.isEmpty())
        continue;
      if (filter.endsWith("/"))
        folders.add(filter.substring(0, filter.length() - 1).trim());
      else
        files.add(toPattern(filter));
    }
    fFileFilters = files.toArray(new Pattern[0]);
    fFolderFilters = folders.toArray(new String[0]);
  }

  /** The regular expression of a file name pattern with the {@code *} and {@code ?} wild cards. */
  private static Pattern toPattern(String wildcards) {
    StringBuilder regex = new StringBuilder();
    for (char c : wildcards.toCharArray()) {
      if (c == '*')
        regex.append(".*");
      else if (c == '?')
        regex.append('.');
      else
        regex.append(Pattern.quote(String.valueOf(c)));
    }
    return Pattern.compile(regex.toString(), Pattern.DOTALL);
  }
}
