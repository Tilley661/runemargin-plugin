package com.runemargin.api;

import com.google.gson.annotations.SerializedName;

/**
 * One item row from the Rune Margin API — mirrors the {@code ItemRow} interface in
 * the website's {@code frontend/src/lib/api.ts}. Boxed numbers so absent values
 * deserialize as {@code null} rather than 0. Note {@link #margin} is already
 * <em>after-tax</em>.
 */
public class ItemRow
{
	public int id;
	public String name;
	public boolean members;
	@SerializedName("ge_limit")
	public Integer geLimit;
	@SerializedName("high_alch")
	public Integer highAlch;
	public String icon;
	public String category;

	/** Instant buy price (what you pay). */
	public Integer high;
	/** Instant sell price (what you receive). */
	public Integer low;
	@SerializedName("high_time")
	public Integer highTime;
	@SerializedName("low_time")
	public Integer lowTime;

	/** After-tax margin (high - tax(high) - low). */
	public Integer margin;
	@SerializedName("roi_pct")
	public Double roiPct;
	@SerializedName("potential_profit")
	public Long potentialProfit;
	public Integer tax;
	@SerializedName("pre_tax_margin")
	public Integer preTaxMargin;

	public ItemAnalysis analysis;
	public ItemStats stats;
}
