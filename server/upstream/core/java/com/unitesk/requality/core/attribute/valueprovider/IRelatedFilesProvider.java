package com.unitesk.requality.core.attribute.valueprovider;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;

public interface IRelatedFilesProvider {
  public Set<String> getRelatedNames();
  public InputStream getFileContent(String name);
  public Path addFile(String string, InputStream in, File srcFile);

}
