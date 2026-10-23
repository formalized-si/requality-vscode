package com.unitesk.requality.document;

import java.util.Comparator;

import com.unitesk.requality.nodetype.Location;

public class LocFragment {
  public static final Comparator<LocFragment> ORDER_COMPARATOR = new Comparator<LocFragment>() {
    @Override
    public int compare(LocFragment o1, LocFragment o2) {
      if(o1 == null)
        return o2 == null ? 0 : -1;
      if(o2 == null)
        return 1;
      return Integer.compare(o1.fragment[0],o2.fragment[0]);
    }
  };
  public int[]fragment;
  public Location loc;
  public LocFragment(int[] loc, Location locn) {
    fragment = loc;
    this.loc = locn;
  }
}
