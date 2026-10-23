package com.unitesk.requality.core.selection;


public class EventFSId extends FSId{
  private FSId base, event = null;
  public EventFSId(FSId baseFSId, FSId eventFSId) {
    super(baseFSId.getObject());
    this.base = baseFSId;
    if(eventFSId!=baseFSId)
      this.event = eventFSId;
  }
  public EventFSId(FSId eventFSId) {
    super(eventFSId.getObject());
    this.base = eventFSId;
  }
  public static FSId create(FSId fsid, FSId source) {
    return new EventFSId(fsid, source);
  }
  @Override
  public Long getId() {
    if(event!=null)
      return event.getId();
    if(base == null)
      return null;
    return base.getId();
  }
  @Override
  public EventSource getEventSource() {
    if(event!=null)
      return event.getEventSource();
    if(base == null)
      return null;
    return base.getEventSource();
  }
  @Override
  public boolean relatesToSource(EventSource editor) {
    if(base!=null && base.relatesToSource(editor))
      return true;
    if(event!=null)
      return event.relatesToSource(editor);
    return super.relatesToSource(editor);
  }
  @Override
  public boolean isSameAs(FSId viewId) {
    if(viewId == null)
      return base == null;
    if(base!=null) 
      if(base.isSameAs(viewId))
        return true;
    if(event!=null)
      return event.isSameAs(viewId);
    return false;
  }
  @Override
  public FSId getBaseId() {
    return base;
  }
  @Override
  public FSId getRootId() {
    return base.getRootId();
  }
}
