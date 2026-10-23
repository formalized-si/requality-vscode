package com.unitesk.requality.core.attribute;

public class ReadOnlyAttributeException extends RuntimeException {

  /**
   * 
   */
  private static final long serialVersionUID = 8296774140256111635L;
  public ReadOnlyAttributeException(String key){
    super("Attribute "+key+" is read only and cannot be changed");
  }
}
