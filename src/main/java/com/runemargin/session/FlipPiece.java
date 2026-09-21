package com.runemargin.session;

/**
 * One component consumed by a set flip: the piece's name, the quantity used and its total
 * cost. Lets the History show what a sold set was actually assembled from.
 */
public class FlipPiece
{
	public String name;
	public long quantity;
	public long cost;

	public FlipPiece()
	{
	}

	public FlipPiece(String name, long quantity, long cost)
	{
		this.name = name;
		this.quantity = quantity;
		this.cost = cost;
	}
}
