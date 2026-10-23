package com.unitesk.requality.core.exception;

import java.util.UUID;

public class NullParentException extends NodeNotFoundException {
	public NullParentException(UUID uid, String proj){
		super("Parent of "+uid+" is null in "+proj+"!");
		this.uid = uid.toString();
	}

	public NullParentException(String uid, String proj){
		super("Parent of "+uid+" is null in "+proj+"!");
		this.uid = uid;
	}
}
