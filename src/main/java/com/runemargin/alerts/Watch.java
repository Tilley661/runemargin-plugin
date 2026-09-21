package com.runemargin.alerts;

/** A one-shot margin alert: notify once when {@code itemId}'s after-tax margin ≥ target. */
public class Watch
{
	public int itemId;
	public String name;
	public int targetMargin;

	public Watch()
	{
	}

	public Watch(int itemId, String name, int targetMargin)
	{
		this.itemId = itemId;
		this.name = name;
		this.targetMargin = targetMargin;
	}
}
