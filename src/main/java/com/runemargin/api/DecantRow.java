package com.runemargin.api;

import com.google.gson.annotations.SerializedName;

/**
 * One potion-decanting play — buy the cheapest dose, decant up to (4), sell the (4).
 * Mirrors {@code DecantRow} in the website's {@code api.ts} / backend schema. Only the
 * fields the plugin surfaces are modelled; unknown JSON fields deserialize away.
 */
public class DecantRow
{
	@SerializedName("base_name")
	public String baseName;
	/** The 4-dose variant's item id (links to its page / overlay). */
	public int id4;
	public String icon;
	public boolean members;

	/** Cheapest dose to buy and decant up: 1, 2 or 3. */
	@SerializedName("best_source_dose")
	public int bestSourceDose;
	@SerializedName("source_id")
	public int sourceId;

	@SerializedName("cost_per4")
	public Integer costPer4;
	public Integer sell4;
	public Integer tax;
	@SerializedName("profit_per4")
	public Integer profitPer4;
	@SerializedName("roi_pct")
	public Double roiPct;

	@SerializedName("vol4_24h")
	public Integer vol424h;
	@SerializedName("source_vol_24h")
	public Integer sourceVol24h;

	@SerializedName("realistic_profit_total")
	public Long realisticProfitTotal;
	@SerializedName("achievable_4s_total")
	public Integer achievable4sTotal;
	@SerializedName("rm_score")
	public Integer rmScore;
}
