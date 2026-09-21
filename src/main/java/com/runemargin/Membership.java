package com.runemargin;

/**
 * Which items the Flips / Search lists show: members-only (P2P), free-to-play
 * (F2P), or both. Maps to the API's {@code members} filter. Defaults to P2P, where
 * the bulk of flipping happens.
 */
public enum Membership
{
	P2P("Members (P2P)", Boolean.TRUE),
	F2P("Free-to-play (F2P)", Boolean.FALSE),
	BOTH("Both", null);

	private final String label;
	private final Boolean members;

	Membership(String label, Boolean members)
	{
		this.label = label;
		this.members = members;
	}

	/** The {@code members} query value: TRUE, FALSE, or null for "no filter". */
	public Boolean members()
	{
		return members;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
