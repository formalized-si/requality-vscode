package com.unitesk.requality.core.attribute.valueprovider;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import com.dslplatform.json.JsonReader;
import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.attribute.AttributeType;

public interface IVPCreator {
  public IValueProvider create(JSONObject object, IRelatedFilesProvider resFolder) throws JSONException;

  public IValueProvider create(UUID uid,
      JsonReader set, AttributeType atype, AttributeType etype, String enumKey, IRelatedFilesProvider resFolder, Map<String, Object> addParams) throws IOException;

}
