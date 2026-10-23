package com.unitesk.requality.core.model;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

public class SerialUUIDGenerator implements IUUIDGenerator {
  static long id = 0;

  @Override
  public synchronized UUID nextUUId() {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(bos);
    try {
      dos.writeLong(0L);
      dos.writeLong(id);
      dos.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
    UUID result = UUID.nameUUIDFromBytes(bos.toByteArray());
    id++;
    return result;
  }

  public static long getLastId() {
    return id - 1;
  }

  public void reset() {
    id = 0;
  }
}
