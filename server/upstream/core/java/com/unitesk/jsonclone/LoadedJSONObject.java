package com.unitesk.jsonclone;

import java.io.Closeable;
import java.io.InputStream;

import com.unitesk.requality.core.log.RLogger;

public class LoadedJSONObject extends JSONObject implements Closeable {

	  /**
	   * Construct a JSONObject from a JSONTokener.
	   *
	   * @param x A JSONTokener object containing the source string.
	   * @throws JSONException If there is a syntax error in the source string or a duplicated key.
	   */
	  public LoadedJSONObject(JSONTokener x) throws JSONException {
	    super();
	    this.x = x;
	    char c;
	    String key;

	    if (x.nextClean() != '{') {
	      throw x.syntaxError("A JSONObject text must begin with '{'");
	    }
	    for (; ; ) {
	      c = x.nextClean();
	      switch (c) {
	        case 0:
	          throw x.syntaxError("A JSONObject text must end with '}'");
	        case '}':
	          return;
	        default:
	          x.back();
	          key = x.nextValue().toString();
	      }

	      /*
	       * The key is followed by ':'. We will also tolerate '=' or '=>'.
	       */

	      c = x.nextClean();
	      if (c == '=') {
	        if (x.next() != '>') {
	          x.back();
	        }
	      } else if (c != ':') {
	        throw x.syntaxError("Expected a ':' after a key");
	      }
	      try {
	        putOnce(key, x.nextValue());
	      }catch(JSONException exc) {
	        RLogger.logError(exc);
	        throw exc;
	      }

	      /*
	       * Pairs are separated by ','. We will also tolerate ';'.
	       */

	      switch (x.nextClean()) {
	        case ';':
	        case ',':
	          if (x.nextClean() == '}') {
	            return;
	          }
	          x.back();
	          break;
	        case '}':
	          return;
	        default:
	          throw x.syntaxError("Expected a ',' or '}'");
	      }
	    }
	  }
	  /**
	   * Construct a JSONObject from a source JSON text string. This is the most commonly used
	   * JSONObject constructor.
	   *
	   * @param source A string beginning with <code>{</code>&nbsp;<small>(left brace)</small> and
	   *     ending with <code>}</code>&nbsp;<small>(right brace)</small>.
	   * @exception JSONException If there is a syntax error in the source string or a duplicated key.
	   */
	  public LoadedJSONObject(String source) throws JSONException {
	    this(new JSONTokener(source));
	  }
	  public LoadedJSONObject(InputStream source) throws JSONException {
		this(new JSONTokener(source));
	  }
	@Override
	public void close() {
      if(x!=null)
        x.close();
	}

}
