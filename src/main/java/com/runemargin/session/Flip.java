package com.runemargin.session;

import java.util.List;

/**
 * One realized flip: a sell that was matched against earlier buys this session. All gp
 * figures are whole coins; {@link #profit} is after GE tax.
 *
 * <p>{@link #basis} records how the cost was resolved — a plain {@code "direct"} flip, a
 * {@code "set"} costed from its component pieces, or a {@code "decant"} costed from pooled
 * potion doses. For a set, {@link #pieces} carries the pieces that were consumed so the
 * History can show exactly what went into it.
 */
public class Flip
{
	public int itemId;
	public String itemName;
	/** Quantity matched buy→sell for this realization. */
	public long quantity;
	/** Total cost of the matched buy lots. */
	public long cost;
	/** Sale proceeds for the matched quantity, after GE tax. */
	public long netProceeds;
	/** {@code netProceeds - cost}. */
	public long profit;
	/** When the sell completed (epoch millis). */
	public long timeMillis;
	/** How the cost basis was resolved: {@code "direct"}, {@code "set"} or {@code "decant"} (null on legacy data). */
	public String basis;
	/** For a set flip, the component pieces consumed (their names, quantities and cost); null otherwise. */
	public List<FlipPiece> pieces;

	public Flip()
	{
	}

	public Flip(int itemId, String itemName, long quantity, long cost, long netProceeds, long timeMillis,
		String basis, List<FlipPiece> pieces)
	{
		this.itemId = itemId;
		this.itemName = itemName;
		this.quantity = quantity;
		this.cost = cost;
		this.netProceeds = netProceeds;
		this.profit = netProceeds - cost;
		this.timeMillis = timeMillis;
		this.basis = basis;
		this.pieces = pieces;
	}
}
