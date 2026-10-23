package com.unitesk.requality.core.transaction;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import org.apache.poi.util.IOUtils;

import com.unitesk.requality.core.storage.IStorageDeltaListener;
import com.unitesk.requality.core.storage.ITreeStorage;

public class ChangeResource extends TreeOperation {
  private UUID uuid;
  private String name;
  private String oldContentFile;
  private String newContentFile;
  private Object data;

  public ChangeResource(
      boolean derived, UUID uuid, String name, String oldContentFile, String newContentFile, Object data, byte flag) {
    super(derived, flag);
    this.uuid = uuid;
    this.name = name;
    this.oldContentFile = oldContentFile;
    this.newContentFile = newContentFile;
    this.data = data;
  }

  @Override
  public void apply(ITreeStorage storage, IStorageDeltaListener delta) {
    if (newContentFile != null) { // new content exists
      try {
        try(InputStream in = new FileInputStream(new File(newContentFile))){
          storage.setResourceContents(uuid, name, in, data, getFlag());
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      storage.deleteResource(uuid, name, getFlag());
    }
  }

  @Override
  public void rollback(ITreeStorage storage,IStorageDeltaListener delta) {
    if (oldContentFile == null) { // resource was created
      storage.deleteResource(uuid, name, getFlag());
    } else {
      try {
        try(InputStream in = new FileInputStream(new File(oldContentFile))){
          storage.setResourceContents(uuid, name, in, data, getFlag());
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void dispose() {
    // if(oldContentFile!=null) {
    // new File(oldContentFile).delete();
    // }
    // if(newContentFile!=null) {
    // new File(newContentFile).delete();
    // }
  }

  public static void copy(InputStream in, OutputStream out) throws IOException {
//    byte[] buffer = new byte[512];
    IOUtils.copy(in, out);
//    while (true) {
//      try{
//        int count = in.read(buffer);
//        if (count == -1) break;
//        out.write(buffer, 0, count);
//      }catch(IOException ie){
//        break;
//      }
//    }
//    out.close();
//    in.close();
  }

  @Override
  public Object getData() {
    return data;
  }

  @Override
  protected String opToString() {
    return "Change resource " + name + " for " + uuid;
  }

  public UUID getNodeUUID() {
    return uuid;
  }

  public String getResourceName() {
    return name;
  }

}
