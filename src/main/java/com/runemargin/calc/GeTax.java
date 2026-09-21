package com.runemargin.calc;

/**
 * Grand Exchange tax — a faithful port of the website's single source of truth
 * ({@code frontend/src/lib/getax.ts}). Never hard-code the rate elsewhere; call
 * {@link #geTax(double)}.
 *
 * <p>Tax is 2% of the sale price, floored to whole gp, with items under 50 gp
 * exempt and a 5,000,000 gp per-item cap.
 */
public final class GeTax
{
	public static final double RATE = 0.02;
	public static final int EXEMPT_BELOW = 50;
	public static final int CAP = 5_000_000;

	private GeTax()
	{
	}

	/**
	 * Tax withheld from the seller when an item sells at {@code price}.
	 *
	 * @return 0 for a null-equivalent (non-finite) or sub-50 gp price, otherwise
	 *         {@code min(floor(price * 0.02), 5_000_000)}.
	 */
	public static long geTax(double price)
	{
		if (!Double.isFinite(price) || price < EXEMPT_BELOW)
		{
			return 0;
		}
		return Math.min((long) Math.floor(price * RATE), CAP);
	}
}
