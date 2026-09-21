package com.runemargin.ui;

/** Small number-formatting helpers shared across the UI. */
final class Format
{
	private Format()
	{
	}

	/** "—" for null, otherwise a compact gp value (e.g. 12,345 / 1.2M / 3.4B). */
	static String gp(Number value)
	{
		if (value == null)
		{
			return "—";
		}
		long v = value.longValue();
		long abs = Math.abs(v);
		if (abs >= 1_000_000_000L)
		{
			return trim(v / 1_000_000_000.0) + "B";
		}
		if (abs >= 1_000_000L)
		{
			return trim(v / 1_000_000.0) + "M";
		}
		if (abs >= 100_000L)
		{
			return (v / 1000) + "K";
		}
		return String.format("%,d", v);
	}

	/** "—" for null, otherwise the exact value with thousands separators. */
	static String exact(Number value)
	{
		return value == null ? "—" : String.format("%,d", value.longValue());
	}

	/** "—" for null, otherwise a percentage to one decimal place. */
	static String pct(Double value)
	{
		return value == null ? "—" : String.format("%.1f%%", value);
	}

	/** A compact "time since" for an epoch-millis instant (e.g. "just now", "5m", "2h"). */
	static String timeAgo(long epochMillis)
	{
		long secs = Math.max(0, (System.currentTimeMillis() - epochMillis) / 1000);
		if (secs < 60)
		{
			return "just now";
		}
		long mins = secs / 60;
		if (mins < 60)
		{
			return mins + "m ago";
		}
		long hours = mins / 60;
		if (hours < 24)
		{
			return hours + "h ago";
		}
		return (hours / 24) + "d ago";
	}

	private static String trim(double d)
	{
		// One decimal, but drop a trailing ".0".
		String s = String.format("%.1f", d);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}
}
