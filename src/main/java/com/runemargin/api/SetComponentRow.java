package com.runemargin.api;

import com.google.gson.annotations.SerializedName;

/** One component piece of a GE item set. Mirrors {@code SetComponentRow}. */
public class SetComponentRow
{
	@SerializedName("item_id")
	public int itemId;
	public String name;
	public String icon;
	public Integer buy;
	public Integer sell;
	@SerializedName("vol_24h")
	public Integer vol24h;
	@SerializedName("ge_limit")
	public Integer geLimit;
}
