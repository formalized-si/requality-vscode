package com.unitesk.requality.core.storage.formatversion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.storage.NodeMapper;

public class ResHelper {
  public static UUID getFileUUID(File file) {
    InputStream content = null;
    try {
      content = readFile(file);
      if (content == null) return null;
      return NodeMapper.getUUId(content);
    } catch (JSONException e) {
      e.printStackTrace();
      return null;
    }
    finally{
      if(content!=null)
        try {
          content.close();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
    }
  }
  private static String getExtension(Path f){
    String fn = f.getFileName().toString();
    if(fn!=null){
      int dot = fn.lastIndexOf(".");
      if(dot>-1)
        return fn.substring(dot+1);
    }
    return null;
  }
  private static String getExtension(File f){
    String fn = f.getName();
    if(fn!=null){
      int dot = fn.lastIndexOf(".");
      if(dot>-1)
        return fn.substring(dot+1);
    }
    return null;
  }
  public static InputStream readFile(Path file) {
    InputStream is = null;
    try {
      boolean exists = Files.exists(file);
      if(exists){
        if(!Files.isReadable(file)){
          RLogger.logError("File cannot be read! "+file.toAbsolutePath());
          while(!Files.isReadable(file))
            try {file.wait(300);} catch (InterruptedException e){// TODO Auto-generated catch block
            e.printStackTrace();}
        }
      }
      else
        RLogger.logError("File is not exist! "+file.toAbsolutePath());
      String fileExt = getExtension(file);
      if (fileExt == null || !fileExt.equals("json") || !exists) {
        return null;
      }
      is = Files.newInputStream(file);
      return is;
    } catch ( IOException e) {
      e.printStackTrace();
    }
    finally{
    }
    throw new RuntimeException("File cannot be loaded! "+file.toAbsolutePath()+" ("+(Files.isReadable(file)?"can read":"cannot read")+")");
  }
  public static InputStream readFile(File file) {
    FileInputStream is = null;
    try {
      if(file.exists()){
        if(!file.canRead()){
          RLogger.logError("File cannot be read! "+file.getAbsolutePath());
          while(!file.canRead())
            try {file.wait(300);} catch (InterruptedException e){// TODO Auto-generated catch block
            e.printStackTrace();}
        }
      }
      else
        RLogger.logError("File is not exist! "+file.getAbsolutePath());
      String fileExt = getExtension(file);
      if (fileExt == null || !fileExt.equals("json") || !file.exists()) {
        return null;
      }
      is = new FileInputStream(file);
      return is;
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    finally{
    }
    throw new RuntimeException("File cannot be loaded! "+file.getAbsolutePath()+" ("+(file.canRead()?"can read":"cannot read")+")");
  }
}
