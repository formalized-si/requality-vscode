package com.unitesk.requality.core.selection;

//full selection source id;
public class FSId {
	public static FSId UNKNOWN = new FSId(EventSource.SYSTEM, null);
	private static long MAX_ID = 0;
	private EventSource sourceClass;
	private Long id = -1L;
	private Object obj;
	protected FSId(Object obj) {
	  this.obj = obj;
	}
	public FSId(EventSource es, Object obj)
	{
	  this(obj);
      this.sourceClass = es;
	  id = MAX_ID++;
	}
	public FSId(EventSource es, Object obj, Long l) {
      this(obj);
	  this.sourceClass = es;
	  this.id = l;
	}
	public Object getObject() {
	  return obj;
	}
	public Long getId() {
	  return id;
	}
	public EventSource getEventSource() { 
	  return sourceClass;
	}
	public boolean isSameAs(FSId viewId) {
	  if(viewId == null)
	    return false;
	  return viewId.sourceClass == sourceClass && id == viewId.id;
	}
    public FSId getBaseId() {
      return this;
    }
    public FSId getRootId() {
      return this;
    }
	public boolean relatesToSource(EventSource editor) {
      return getEventSource() == editor;
	}
	public String getFullIdString() {
      StringBuilder sb = new StringBuilder();
      sb.append(sourceClass);
      sb.append("_");
      sb.append(id);
      return sb.toString();
	}
}
