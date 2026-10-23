package com.unitesk.requality.core.attribute;

public class DataType {
	public DataType(String id, String desc, String types, DataTypeAttribute[]attrs) {
	  this.id = id;
	  this.description = desc;
	  this.typeFallbacks = types;
	  this.attributes = attrs;
	}
	public String id;
	public String description;
	public String typeFallbacks;//in format of DATATYPE, TYPE2 and so on
	public DataTypeAttribute[] attributes;
}
