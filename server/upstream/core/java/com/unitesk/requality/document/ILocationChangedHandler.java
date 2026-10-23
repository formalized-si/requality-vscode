package com.unitesk.requality.document;

import com.unitesk.requality.nodetype.Location;

public interface ILocationChangedHandler {

  void onLocationChanged(long time, Location loc, boolean upreq, boolean source);

}
