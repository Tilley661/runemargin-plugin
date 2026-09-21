package com.runemargin.session;

/**
 * A finished flipping session, archived when the player starts a new one. Just the
 * headline figures needed to review past performance — the individual flips/trades of
 * a closed session are not retained. All gp figures are whole coins; {@link #profit}
 * is realized, after-tax.
 */
public class SessionSummary
{
	public long startMillis;
	public long endMillis;
	public long profit;
	public int flipCount;
	/** Matched cost basis, for the ROI figure. */
	public long costBasis;
	public long taxPaid;

	public SessionSummary()
	{
	}

	public SessionSummary(long startMillis, long endMillis, long profit, int flipCount, long costBasis, long taxPaid)
	{
		this.startMillis = startMillis;
		this.endMillis = endMillis;
		this.profit = profit;
		this.flipCount = flipCount;
		this.costBasis = costBasis;
		this.taxPaid = taxPaid;
	}

	/** Realized ROI as a percentage of matched cost, or null if nothing was sold. */
	public Double roiPct()
	{
		return costBasis <= 0 ? null : profit * 100.0 / costBasis;
	}
}
