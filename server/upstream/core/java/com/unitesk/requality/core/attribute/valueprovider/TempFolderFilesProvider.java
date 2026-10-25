package com.unitesk.requality.core.attribute.valueprovider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class TempFolderFilesProvider implements IRelatedFilesProvider {
  public static final TempFolderFilesProvider EMPTY = new TempFolderFilesProvider(null, true);
  File dir;
  boolean dirInited = false;
  private TempFolderFilesProvider() {
    this(null, false);
  }
  public static TempFolderFilesProvider getNewNotInited(AttributeType at) {
    if(at == AttributeType.HTML || at == AttributeType.HTML)//TODO detect lt by LIST
      return getOne(null, false);
    return EMPTY;
  }
  public static TempFolderFilesProvider getOne(AttributeType at, File f, boolean dirInited) {
    if(f == null && dirInited)
      return EMPTY;
    return getOne(f, dirInited);
  }
  
  //TODO most calls could be rewrited by resourced providers
  public static TempFolderFilesProvider getOne(File f, boolean dirInited) {
    if(f == null && dirInited)
      return EMPTY;
    return new TempFolderFilesProvider(f, dirInited);
  }
  private TempFolderFilesProvider(File dir, boolean dirInited) {
    this.dir = dir;
    if(dir!=null && !dir.exists())
      this.dir = null;
    this.dirInited = dirInited;
  }
  @Override
  public Set<String> getRelatedNames() {
    Set<String> ret = Collections.emptySet();
    if(dir == null)
      return ret;
    File[]files = dir.listFiles();
    if(files == null)
      return ret;
    ret = new HashSet<>();
    for(File chi:files)
      recAddFiles("", chi, ret);
    return ret;
  }
  private static void recAddFiles(String path, File dir, Set<String> ret) {
    if(path.startsWith("/"))
      path = path.substring(1);
    if(dir.isFile())
      ret.add((path.isEmpty()?"":(path+"/"))+dir.getName());
    if(dir.isDirectory())
      for(File chi:dir.listFiles())
        recAddFiles(path+"/"+dir.getName(), chi, ret);
  }
  @Override
  public InputStream getFileContent(String name) {
    if(dir == null)
      return null;
    java.nio.file.Path resource = dir.toPath().resolve(name);//TODO encoding problems?
    if(Files.exists(resource)) {
      try {
        return Files.newInputStream(resource);
      } catch (IOException e){// TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return null;

  }
  public File getDir() {
    if(dir==null && !dirInited){
      dirInited = true;
      dir = getNewDirFolder();
    }
    return dir;
  }
  
  public void addFile(File f) throws IOException {
    getDir();
    if(dir == null)
      throw new RuntimeException ("Attempt to add file for inited null directory");
    File localF = new File(dir.getAbsolutePath()+File.separator+f.getName());
    localF.createNewFile();
    RequalityCLI.copyfile(f.toURI(), localF.toURI());
  }
  @Override
  public Path addFile(String string, InputStream in, File srcFile){
    getDir();
    if(dir == null)
      throw new RuntimeException ("Attempt to add file for inited null directory");
    File f = dir.toPath().resolve(string).toFile();//TODO check encoding problems?
    if(srcFile!=null && f.getAbsolutePath().equals(srcFile.getAbsolutePath()))//nothing to do here
      return null;
    f.getParentFile().mkdirs();
    try {
      Files.copy(in,f.toPath(),StandardCopyOption.REPLACE_EXISTING);
      f.deleteOnExit();//mark temp file to be removed on application stop
    } catch (IOException e){// TODO Auto-generated catch block
      e.printStackTrace();
    }
    return f.toPath();
  }
  public static File getNewDirFolder() {
    File dir = CoreUtils.getTempDir().resolve("htmlvp"+UUID.randomUUID().toString()).toFile();
    if(!dir.exists())
      dir.mkdirs();
    dir.deleteOnExit();
    return dir;
  }
}
