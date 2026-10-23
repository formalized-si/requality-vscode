package com.unitesk.requality.core.attribute.valueprovider;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.storage.OFlag;

public class ResFilesProvider implements IRelatedFilesProvider {
  private AttributeStorage as;
  private String key;
  public ResFilesProvider(AttributeStorage as, String key) {
    this.as = as;
    this.key = key;
  }

  @Override
  public Set<String> getRelatedNames() {
    Set<String> lr = as.getNode().listResources();
    Set<String> ret = new HashSet<>();
    for(String s : lr)
      if(s.startsWith(key+"/"))
        ret.add(s.substring(key.length()+1));
    return ret;
  }

  @Override
  public InputStream getFileContent(String name) {
    return as.getNode().getResourceContent(key+"/"+name);
  }

  @Override 
  public Path addFile(String string, InputStream in, File srcFile){// TODO Auto-generated method stub
    as.getNode().setResourceContent(string, in, OFlag.NONE);
    File f = as.getNode().getResource(string);
    if(f!=null)
      return f.toPath();
    return null;
  }
}
