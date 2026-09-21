package com.runemargin.session;

import java.util.List;
import java.util.Set;

/**
 * An immutable summary of the current flipping session, handed to the UI. Built by
 * {@link SessionTracker#snapshot()} so the panel never touches live tracker state.
 */
public final class SessionSnapshot
{
	public final long realizedProfit;
	public final int flipCount;
	public final long taxPaid;
	public final long netProceeds;
	public final long costBasis;
	public final Long bestProfit;
	public final Long worstProfit;
	public final long sessionStartMillis;
	/** Cost still tied up in unsold buy lots. */
	public final long openInvested;
	/** Most-recent realized flips first (capped). */
	public final List<Flip> recentFlips;
	/** Most-recent raw buy/sell fills first (capped) — the browsable trade history. */
	public final List<Trade> recentTrades;
	/** Previously-finished sessions, newest first — archived on "New session". */
	public final List<SessionSummary> pastSessions;
	/** Item ids that still have an open cost basis (unsold buy lots / pooled potion doses). */
	public final Set<Integer> heldItemIds;

	SessionSnapshot(long realizedProfit, int flipCount, long taxPaid, long netProceeds, long costBasis,
		Long bestProfit, Long worstProfit, long sessionStartMillis, long openInvested, List<Flip> recentFlips,
		List<Trade> recentTrades, List<SessionSummary> pastSessions, Set<Integer> heldItemIds)
	{
		this.realizedProfit = realizedProfit;
		this.flipCount = flipCount;
		this.taxPaid = taxPaid;
		this.netProceeds = netProceeds;
		this.costBasis = costBasis;
		this.bestProfit = bestProfit;
		this.worstProfit = worstProfit;
		this.sessionStartMillis = sessionStartMillis;
		this.openInvested = openInvested;
		this.recentFlips = recentFlips;
		this.recentTrades = recentTrades;
		this.pastSessions = pastSessions;
		this.heldItemIds = heldItemIds;
	}

	/** Realized ROI as a percentage of matched cost, or null if nothing sold yet. */
	public Double roiPct()
	{
		if (costBasis <= 0)
		{
			return null;
		}
		return realizedProfit * 100.0 / costBasis;
	}
}
