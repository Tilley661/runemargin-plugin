package com.runemargin;

/**
 * How the Flips / Search lists are ordered in the panel. The Rune Margin API
 * returns best-flips ranked by volume-aware {@code realistic profit}; this lets
 * the user re-sort the fetched rows client-side by whichever metric they care
 * about. Every mode sorts highest-first, with missing values last.
 */
public enum SortMode
{
	REALISTIC_PROFIT("Realistic profit"),
	RM_SCORE("RM score"),
	MARGIN("Margin (after tax)"),
	ROI("ROI %"),
	POTENTIAL_PROFIT("Potential profit"),
	VOLUME_24H("24h volume");

	private final String label;

	SortMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
