package com.runemargin.api;

import com.google.gson.annotations.SerializedName;

/**
 * The caller's plan + per-flip profit cap, from {@code GET /auth/tier}. Resolvable
 * with a personal access token, so the plugin can show subscription status and nudge
 * an upgrade. Anonymous callers report the {@code anon} tier.
 */
public class TierStatus
{
	public String tier;
	@SerializedName("effective_tier")
	public String effectiveTier;
	/** Per-flip profit cap; {@code null} means unlimited (Pro / Supporter). */
	public Integer cap;
	public boolean authenticated;
}
