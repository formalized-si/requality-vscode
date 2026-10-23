package com.unitesk.requality.vscode.rpc;

/** A JSON-RPC error that is reported to the peer with its code and message. */
public class RpcException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public static final int INVALID_PARAMS = -32602;
  public static final int INTERNAL_ERROR = -32603;
  /** The request refers to a node, project or editor that no longer exists. */
  public static final int NOT_FOUND = -32001;
  /** The target project is not loaded yet or is being modified. */
  public static final int NOT_READY = -32002;
  /** The operation was refused by Requality (validation, read-only, policy). */
  public static final int REFUSED = -32003;
  /** The user cancelled the operation. */
  public static final int CANCELLED = -32800;

  private final int code;
  private final transient Object data;

  public RpcException(int code, String message) {
    this(code, message, null);
  }

  public RpcException(int code, String message, Object data) {
    super(message);
    this.code = code;
    this.data = data;
  }

  public int getCode() {
    return code;
  }

  public Object getData() {
    return data;
  }

  public static RpcException invalidParams(String message) {
    return new RpcException(INVALID_PARAMS, message);
  }

  public static RpcException notFound(String message) {
    return new RpcException(NOT_FOUND, message);
  }

  public static RpcException refused(String message) {
    return new RpcException(REFUSED, message);
  }
}
