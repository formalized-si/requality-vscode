package com.unitesk.requality.core.exception;

import java.util.UUID;

public class NodeNotFoundException extends RuntimeException {
	protected String uid;
	public NodeNotFoundException(UUID uid, String proj){
		super("Node "+uid+" was not found in "+proj+"!");
		this.uid = uid.toString();
	}

	public NodeNotFoundException(String uid, String proj){
		super("Node "+uid+" was not found in "+proj+"!");
		this.uid = uid;
	}
	protected NodeNotFoundException(String message) {
        super(message);
	}
}
