package com.runemargin.api;

import com.google.gson.annotations.SerializedName;

/**
 * The precomputed Rune Margin verdict for an item — mirrors {@code ItemAnalysis}
 * (and its nested flip/alch branches) in the website's {@code api.ts}.
 */
public class ItemAnalysis
{
	/** "flip" | "alch" | "none". */
	public String verdict;
	/** "high" | "medium" | "low". */
	public String confidence;
	/** Human-readable summary, e.g. "Flip for ~1.2M gp (3.2% ROI)". */
	public String headline;
	@SerializedName("expected_profit")
	public Long expectedProfit;

	public Flip flip;
	public Alch alch;

	public static class Flip
	{
		public boolean profitable;
		@SerializedName("avg_high")
		public Integer avgHigh;
		@SerializedName("avg_low")
		public Integer avgLow;
		public Integer tax;
		public Integer margin;
		@SerializedName("roi_pct")
		public Double roiPct;
		@SerializedName("vol_4h")
		public Integer vol4h;
		public Integer availability;
		@SerializedName("expected_profit")
		public Long expectedProfit;
	}

	public static class Alch
	{
		public boolean profitable;
		@SerializedName("buy_price")
		public Integer buyPrice;
		@SerializedName("high_alch")
		public Integer highAlch;
		@SerializedName("nature_price")
		public Integer naturePrice;
		@SerializedName("per_item_profit")
		public Integer perItemProfit;
		public Integer volume;
		@SerializedName("expected_profit")
		public Long expectedProfit;
	}
}
