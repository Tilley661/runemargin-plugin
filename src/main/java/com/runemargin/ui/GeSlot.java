package com.runemargin.ui;

/**
 * An immutable snapshot of one Grand Exchange slot, handed from the plugin (client thread)
 * to the {@link SlotsPanel} (EDT). Only the fields the panel renders are carried, so it can
 * be built off a {@code GrandExchangeOffer} without exposing RuneLite API types to the UI.
 */
public final class GeSlot
{
	/** Where the offer is in its lifecycle, distilled to what the panel colours by. */
	public enum Status
	{
		ACTIVE,
		FILLED,
		CANCELLED
	}

	public final int slot;
	public final int itemId;
	public final String name;
	public final boolean buy;
	public final int sold;
	public final int total;
	public final int price;
	public final Status status;

	public GeSlot(int slot, int itemId, String name, boolean buy, int sold, int total, int price, Status status)
	{
		this.slot = slot;
		this.itemId = itemId;
		this.name = name;
		this.buy = buy;
		this.sold = sold;
		this.total = total;
		this.price = price;
		this.status = status;
	}

	/** How far the offer has filled, 0..1. */
	public double fraction()
	{
		return total > 0 ? Math.min(1.0, (double) sold / total) : 0;
	}

	public boolean complete()
	{
		return total > 0 && sold >= total;
	}
}
