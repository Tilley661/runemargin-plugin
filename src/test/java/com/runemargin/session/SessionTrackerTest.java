package com.runemargin.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/**
 * Proves the session P&L core: FIFO buy→sell matching and after-tax realized profit.
 * Uses the no-arg tracker (no persistence / no name resolution).
 */
public class SessionTrackerTest
{
	private static final int ITEM = 4151; // arbitrary

	@Test
	public void singleFlipBooksAfterTaxProfit()
	{
		SessionTracker t = new SessionTracker();
		t.recordBuy(ITEM, 1, 1000);
		t.recordSell(ITEM, 1, 1100); // tax = floor(1100 * 0.02) = 22

		SessionSnapshot s = t.snapshot();
		assertEquals(78, s.realizedProfit);   // 1100 - 22 - 1000
		assertEquals(22, s.taxPaid);
		assertEquals(1000, s.costBasis);
		assertEquals(1078, s.netProceeds);
		assertEquals(1, s.flipCount);
		assertEquals(0, s.openInvested);
	}

	@Test
	public void fifoAcrossLotsWithPartialSell()
	{
		SessionTracker t = new SessionTracker();
		t.recordBuy(ITEM, 5, 500);   // 100 ea
		t.recordBuy(ITEM, 5, 600);   // 120 ea
		t.recordSell(ITEM, 8, 1200); // 150 ea; tax = floor(150 * 0.02) = 3 -> 24 total

		SessionSnapshot s = t.snapshot();
		// matched cost = 500 (first lot) + 360 (3 of second lot) = 860
		// net = 1200 - 24 = 1176 -> profit 316
		assertEquals(316, s.realizedProfit);
		assertEquals(24, s.taxPaid);
		assertEquals(860, s.costBasis);
		assertEquals(1, s.flipCount);
		assertEquals(240, s.openInvested); // 2 units of the 120-ea lot remain
	}

	@Test
	public void sellWithoutMatchingBuyIsIgnoredForProfit()
	{
		SessionTracker t = new SessionTracker();
		t.recordSell(ITEM, 10, 5000);

		SessionSnapshot s = t.snapshot();
		assertEquals(0, s.realizedProfit);
		assertEquals(0, s.flipCount);
		assertEquals(0, s.costBasis);
		assertNull(s.roiPct());
	}

	@Test
	public void lowValueSalesAreTaxExempt()
	{
		SessionTracker t = new SessionTracker();
		t.recordBuy(ITEM, 1, 40);
		t.recordSell(ITEM, 1, 45); // under 50 gp -> no tax

		SessionSnapshot s = t.snapshot();
		assertEquals(0, s.taxPaid);
		assertEquals(5, s.realizedProfit);
	}

	@Test
	public void recordsRawBuysAndSellsAsTradeHistory()
	{
		SessionTracker t = new SessionTracker();
		t.recordBuy(ITEM, 10, 1000);   // 100 ea
		t.recordSell(ITEM, 10, 1500);  // 150 ea (pre-tax listing price)

		SessionSnapshot s = t.snapshot();
		assertEquals(2, s.recentTrades.size());
		// Newest-first: the sell leads.
		Trade sell = s.recentTrades.get(0);
		assertEquals(Trade.Side.SELL, sell.side);
		assertEquals(150, sell.unitPrice);   // gross / qty, not net of tax
		assertEquals(1500, sell.total);
		assertEquals(10, sell.quantity);

		Trade buy = s.recentTrades.get(1);
		assertEquals(Trade.Side.BUY, buy.side);
		assertEquals(100, buy.unitPrice);
		assertEquals(ITEM, buy.itemId);
	}

	@Test
	public void tradeHistoryCapturesUnmatchedSells()
	{
		SessionTracker t = new SessionTracker();
		t.recordSell(ITEM, 5, 2500); // no matching buy: no profit, but still a real fill

		SessionSnapshot s = t.snapshot();
		assertEquals(0, s.flipCount);
		assertEquals(1, s.recentTrades.size());
		assertEquals(500, s.recentTrades.get(0).unitPrice);
	}

	@Test
	public void resetArchivesTheFinishedSession()
	{
		SessionTracker t = new SessionTracker();
		t.recordBuy(ITEM, 1, 1000);
		t.recordSell(ITEM, 1, 1100); // profit 78 after tax
		t.reset();

		SessionSnapshot s = t.snapshot();
		// The new session is empty...
		assertEquals(0, s.realizedProfit);
		assertEquals(0, s.flipCount);
		// ...but the finished one is retained for review.
		assertEquals(1, s.pastSessions.size());
		assertEquals(78, s.pastSessions.get(0).profit);
		assertEquals(1, s.pastSessions.get(0).flipCount);
	}

	@Test
	public void costBasisSurvivesResetSoAnEarlierBuyStillMatches()
	{
		SessionTracker t = new SessionTracker();
		t.recordBuy(ITEM, 10, 1000); // 100 ea, held into the next session
		t.reset();                   // start a fresh session; holdings carry over

		// The buy lot survives, so selling now books real profit rather than being
		// written off as basis-less bank stock.
		t.recordSell(ITEM, 10, 1500); // 150 ea; tax = floor(150 * 0.02) * 10 = 30

		SessionSnapshot s = t.snapshot();
		assertEquals(1, s.flipCount);
		assertEquals(1000, s.costBasis);
		assertEquals(470, s.realizedProfit); // 1500 - 30 - 1000
		assertEquals(0, s.openInvested);
	}

	@Test
	public void partialCarriedLotLeavesRemainderBasisLess()
	{
		SessionTracker t = new SessionTracker();
		t.recordBuy(ITEM, 4, 400); // 100 ea
		t.reset();
		t.recordSell(ITEM, 10, 2000); // sell 10, only 4 have a tracked cost basis

		SessionSnapshot s = t.snapshot();
		// Only the 4 matched units are booked; 200 ea sale, tax floor(200*0.02)=4 ea.
		// matched net = 800 - 16 = 784 -> profit 384 over 400 cost.
		assertEquals(1, s.flipCount);
		assertEquals(400, s.costBasis);
		assertEquals(384, s.realizedProfit);
		assertEquals(0, s.openInvested);
	}

	@Test
	public void resetDoesNotArchiveAnEmptySession()
	{
		SessionTracker t = new SessionTracker();
		t.reset();

		assertEquals(0, t.snapshot().pastSessions.size());
	}

	@Test
	public void tracksBestAndWorstAndResets()
	{
		SessionTracker t = new SessionTracker();
		t.recordBuy(ITEM, 1, 1000);
		t.recordSell(ITEM, 1, 2000); // big win
		t.recordBuy(ITEM, 1, 1000);
		t.recordSell(ITEM, 1, 900);  // loss

		SessionSnapshot s = t.snapshot();
		assertEquals(2, s.flipCount);
		assertEquals(Long.valueOf(960), s.bestProfit);    // 2000 - 40 tax - 1000
		assertEquals(Long.valueOf(-118), s.worstProfit);  // 900 - 18 tax - 1000
	}

	// --- Set combine: buy the pieces, sell the assembled set --------------------------

	private static final int SET = 900;
	private static final int HAT = 10;
	private static final int TOP = 11;
	private static final int BOTTOM = 12;

	@Test
	public void buyingPiecesThenSellingTheSetBooksTheFlip()
	{
		SessionTracker t = new SessionTracker();
		t.setSetRecipes(Collections.singletonMap(SET, Arrays.asList(HAT, TOP, BOTTOM)));
		t.recordBuy(HAT, 1, 100);
		t.recordBuy(TOP, 1, 150);
		t.recordBuy(BOTTOM, 1, 200);      // 450 to assemble
		t.recordSell(SET, 1, 1000);       // tax = floor(1000 * 0.02) = 20

		SessionSnapshot s = t.snapshot();
		assertEquals(1, s.flipCount);
		assertEquals(450, s.costBasis);
		assertEquals(20, s.taxPaid);
		assertEquals(530, s.realizedProfit); // 1000 - 20 - 450
		assertEquals(0, s.openInvested);

		// The flip is tagged as a set and carries the pieces it was assembled "from pieces".
		Flip flip = s.recentFlips.get(0);
		assertEquals("set", flip.basis);
		assertEquals(3, flip.pieces.size());
		long piecesCost = flip.pieces.stream().mapToLong(p -> p.cost).sum();
		assertEquals(450, piecesCost);
	}

	@Test
	public void sellingMoreSetsThanPiecesBoughtOnlyBooksTheCoveredOnes()
	{
		SessionTracker t = new SessionTracker();
		t.setSetRecipes(Collections.singletonMap(SET, Arrays.asList(HAT, TOP, BOTTOM)));
		t.recordBuy(HAT, 1, 100);
		t.recordBuy(TOP, 1, 150);
		t.recordBuy(BOTTOM, 1, 200);      // only enough for one set
		t.recordSell(SET, 2, 2000);       // sell two: the second came from the bank

		SessionSnapshot s = t.snapshot();
		assertEquals(1, s.flipCount);
		assertEquals(450, s.costBasis);
		// one set matched: gross 1000, tax floor(1000*0.02)=20, net 980, profit 530
		assertEquals(530, s.realizedProfit);
		assertEquals(0, s.openInvested);
	}

	// --- Decant: buy low doses, decant, sell (4)s -------------------------------------

	private static final int DOSE3 = 30;
	private static final int DOSE4 = 40;

	private static SessionTracker prayerTracker()
	{
		SessionTracker t = new SessionTracker();
		t.putItemName(DOSE3, "Prayer potion(3)");
		t.putItemName(DOSE4, "Prayer potion(4)");
		return t;
	}

	@Test
	public void buyingDosesThenSellingFoursBooksTheDecantFlip()
	{
		SessionTracker t = prayerTracker();
		t.recordBuy(DOSE3, 4, 1200);      // 12 doses for 1200
		t.recordSell(DOSE4, 3, 1350);     // 12 doses out; tax = floor(450*0.02)*3 = 27

		SessionSnapshot s = t.snapshot();
		assertEquals(1, s.flipCount);
		assertEquals(1200, s.costBasis);
		assertEquals(27, s.taxPaid);
		assertEquals(123, s.realizedProfit); // 1350 - 27 - 1200
		assertEquals(0, s.openInvested);

		// The decant flip is tagged so the History can read "from doses".
		assertEquals("decant", s.recentFlips.get(0).basis);
	}

	@Test
	public void sellingMoreFoursThanDosesBoughtOnlyBooksTheCoveredOnes()
	{
		SessionTracker t = prayerTracker();
		t.recordBuy(DOSE3, 4, 1200);      // 12 doses = enough for three (4)s
		t.recordSell(DOSE4, 4, 1800);     // sell four: the fourth is basis-less bank stock

		SessionSnapshot s = t.snapshot();
		assertEquals(1, s.flipCount);
		assertEquals(1200, s.costBasis);
		// three covered: gross 1350, tax floor(450*0.02)*3=27, net 1323, profit 123
		assertEquals(123, s.realizedProfit);
		assertEquals(0, s.openInvested);
	}
}
