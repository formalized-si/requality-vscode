package com.unitesk.requality.tool;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UUIDDuplicationHelper {
  private static Map<UUID, UUID> nextUUID = new HashMap<>();
  public static UUID getIncremented(UUID uid) {
    UUID next = nextUUID.get(uid);
    if(next == null) {
      next = UUID.randomUUID();
      nextUUID.put(uid,next);
    }
    return next;
  }
}
