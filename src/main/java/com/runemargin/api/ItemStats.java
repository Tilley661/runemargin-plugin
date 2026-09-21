package com.runemargin.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Precomputed screener stats for an item — mirrors {@code ItemStats} in the
 * website's {@code api.ts}. Only the fields the plugin currently surfaces are
 * modelled explicitly; the rest are here for parity / future use.
 */
public class ItemStats
{
	@SerializedName("pre_tax_margin")
	public Integer preTaxMargin;
	@SerializedName("vol_24h")
	public Integer vol24h;
	public Long liquidity;
	@SerializedName("change_15m")
	public Double change15m;
	@SerializedName("change_1h")
	public Double change1h;
	@SerializedName("change_6h")
	public Double change6h;
	@SerializedName("change_24h")
	public Double change24h;
	@SerializedName("change_7d")
	public Double change7d;
	@SerializedName("change_30d")
	public Double change30d;
	public Double volatility;
	@SerializedName("spread_stability")
	public Double spreadStability;
	@SerializedName("flip_score")
	public Integer flipScore;
	@SerializedName("score_reasons")
	public List<String> scoreReasons;
	/** After-tax margin x min(limit, 24h vol) — the volume-aware profit estimate. */
	@SerializedName("realistic_profit")
	public Long realisticProfit;
	@SerializedName("dump_score")
	public Integer dumpScore;
	@SerializedName("dump_drop_pct")
	public Double dumpDropPct;
}
