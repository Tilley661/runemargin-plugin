package com.runemargin.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Combine/break arbitrage for one GE item set — buy the pieces, pack, sell the set
 * (combine), or the reverse (break). Mirrors {@code SetRow} in the website's
 * {@code api.ts} / backend schema.
 */
public class SetRow
{
	@SerializedName("set_id")
	public int setId;
	@SerializedName("set_name")
	public String setName;
	public String icon;
	public boolean members;
	@SerializedName("piece_count")
	public int pieceCount;

	@SerializedName("set_buy")
	public Integer setBuy;
	@SerializedName("set_sell")
	public Integer setSell;
	public List<SetComponentRow> components;

	// Combine: buy pieces -> pack -> sell set (GE tax once, on the set).
	@SerializedName("combine_profit")
	public Integer combineProfit;
	@SerializedName("combine_roi")
	public Double combineRoi;

	// Break: buy set -> unpack -> sell pieces (GE tax on each piece).
	@SerializedName("break_profit")
	public Integer breakProfit;
	@SerializedName("break_roi")
	public Double breakRoi;

	// Headline: better direction + volume-aware realistic total profit.
	@SerializedName("best_direction")
	public String bestDirection; // "combine" | "break"
	@SerializedName("best_profit")
	public Integer bestProfit;
	public Integer achievable;
	@SerializedName("realistic_profit")
	public Long realisticProfit;
	@SerializedName("rm_score")
	public Integer rmScore;
}
