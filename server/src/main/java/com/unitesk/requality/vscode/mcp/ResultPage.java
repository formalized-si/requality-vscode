package com.unitesk.requality.vscode.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** A bounded page with totals and a continuation offset. Callers add items in a stable order. */
final class ResultPage {
  final JsonArray items = new JsonArray();
  int total;
  private final int offset;
  private final int limit;

  ResultPage(int offset, int limit) {
    this.offset = offset;
    this.limit = limit;
  }

  void add(JsonElement item) {
    if (total++ >= offset && items.size() < limit)
      items.add(item);
  }

  JsonObject json() {
    JsonObject result = new JsonObject();
    result.addProperty("total", total);
    result.add("items", items);
    if ((long) offset + items.size() < total)
      result.addProperty("next_offset", offset + items.size());
    return result;
  }
}
