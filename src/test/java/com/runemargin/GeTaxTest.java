package com.runemargin;

import com.runemargin.calc.GeTax;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GeTaxTest
{
	@Test
	public void exemptBelowFifty()
	{
		assertEquals(0, GeTax.geTax(0));
		assertEquals(0, GeTax.geTax(49));
	}

	@Test
	public void twoPercentFloored()
	{
		assertEquals(1, GeTax.geTax(50));    // floor(50 * 0.02) = 1
		assertEquals(2, GeTax.geTax(100));   // floor(100 * 0.02) = 2
		assertEquals(30, GeTax.geTax(1540)); // floor(1540 * 0.02) = 30
	}

	@Test
	public void cappedAtFiveMillion()
	{
		// 2% of 500m would be 10m, but the cap holds it at 5m.
		assertEquals(GeTax.CAP, GeTax.geTax(500_000_000));
	}

	@Test
	public void nonFiniteIsZero()
	{
		assertEquals(0, GeTax.geTax(Double.NaN));
		assertEquals(0, GeTax.geTax(Double.NEGATIVE_INFINITY));
	}
}
