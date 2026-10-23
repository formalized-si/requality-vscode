package com.unitesk.requality.core.attribute.valueprovider;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.unitesk.jsonclone.JSONException;
import com.unitesk.jsonclone.JSONObject;
import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.descriptors.CoverageStorageSourceDescriptor;
import com.unitesk.requality.core.model.TreeNode;

public class CoverageSourcesList implements IValueProvider {
	private static String ID = "coverageSourcesList";
	@Override
	public Set<String> getDependencies(AttributeStorage as) {
		return null;
	}

	@Override
	public String getType() {
		return ID;
	}

	@Override
	public Map<String, Object> getRawStores() {
		return null;
	}

	@Override
	public void attach(TreeNode node) {
	}

	@Override
	public void detach(TreeNode node) {
	}

	@Override
	public void saveJSON(JSONObject vpo) throws JSONException {
	}

	@Override
	public AttributeType getAttributeType() {
		return AttributeType.LIST;
	}

	@Override
	public TypedValue getTypedValue(String key, AttributeStorage as) {
		List<Object> list = new LinkedList<>();
		for(Entry<String, CoverageStorageSourceDescriptor> en: API.getCoverageSources().entrySet()) {
			list.add(en.getValue().getId());
		}
		return new TypedValue(getAttributeType(), new ListValue(AttributeType.STRING, list));
	}

	@Override
	public TypedValue getRawValue() {
		return null;
	}

}
