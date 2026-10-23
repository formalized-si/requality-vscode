package com.unitesk.requality.core.propagator;

import java.util.Comparator;
import com.unitesk.requality.core.api.IAttributeChangePropagator;

public class AttributePropagatorComparator implements Comparator<IAttributeChangePropagator>{
  private AttributePropagatorComparator() {
  }
  @Override
  public int compare(IAttributeChangePropagator o1, IAttributeChangePropagator o2) {
    int c = Integer.compare(o1.getPriority(),o2.getPriority());
    if(c!=0)
      return c;
    return Integer.compare(o1.hashCode(), o2.hashCode());
  }
  private static AttributePropagatorComparator one = new AttributePropagatorComparator();
  public static AttributePropagatorComparator getOne(){
    return one;
  }
}
