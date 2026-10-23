package com.unitesk.requality.core.api;

import java.util.SortedMap;
import com.unitesk.requality.core.operation.IDeleteHandler;

public interface IAPIDeleteHandlers {
  SortedMap<String, IDeleteHandler> getDeleteHandlers();
}
