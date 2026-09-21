package com.runemargin.session;

/**
 * One completed GE fill — a raw buy or sell the player transacted this session,
 * kept as a browsable trade history so they can see what they last paid / sold at
 * and decide what price to chase. Prices are the GE listing price (pre-tax): a
 * sell's {@link #unitPrice} is what the offer was set to, not the post-tax take.
 */
public class Trade
{
	public enum Side
	{
		BUY,
		SELL
	}

	public int itemId;
	public String itemName;
	public Side side;
	/** Units filled in this fill. */
	public long quantity;
	/** Per-unit GE price (pre-tax). */
	public long unitPrice;
	/** Total gp for the fill: {@code unitPrice * quantity} (pre-tax). */
	public long total;
	/** When the fill completed (epoch millis). */
	public long timeMillis;

	public Trade()
	{
	}

	public Trade(int itemId, String itemName, Side side, long quantity, long total, long timeMillis)
	{
		this.itemId = itemId;
		this.itemName = itemName;
		this.side = side;
		this.quantity = quantity;
		this.total = total;
		this.unitPrice = quantity > 0 ? total / quantity : total;
		this.timeMillis = timeMillis;
	}
}
