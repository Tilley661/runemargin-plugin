package com.runemargin.api;

import com.runemargin.RuneMarginConfig;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A small TTL cache of {@link ItemRow} market data keyed by item id, backing the live
 * GE offer overlay. {@link #get(int)} is safe to call every frame from the client
 * thread: it never blocks — it returns the last cached value (or {@code null}) and
 * only schedules an async refresh when the entry is missing/stale and not already
 * in flight. Callbacks run off the game thread on OkHttp's dispatcher.
 */
@Singleton
public class LivePriceCache
{
	private final RuneMarginApiClient api;
	private final RuneMarginConfig config;

	private final ConcurrentHashMap<Integer, Entry> cache = new ConcurrentHashMap<>();
	private final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();

	@Inject
	public LivePriceCache(RuneMarginApiClient api, RuneMarginConfig config)
	{
		this.api = api;
		this.config = config;
	}

	/** Latest cached market data for {@code itemId}, refreshing in the background if stale. */
	public ItemRow get(int itemId)
	{
		Entry entry = cache.get(itemId);
		long now = System.currentTimeMillis();
		if (entry == null || now - entry.fetchedAt > ttlMillis())
		{
			refresh(itemId);
		}
		return entry == null ? null : entry.row;
	}

	private void refresh(int itemId)
	{
		if (!inFlight.add(itemId))
		{
			return;
		}
		api.item(itemId,
			row ->
			{
				if (row != null)
				{
					cache.put(itemId, new Entry(row, System.currentTimeMillis()));
				}
				inFlight.remove(itemId);
			},
			err -> inFlight.remove(itemId));
	}

	private long ttlMillis()
	{
		return Math.max(15, config.refreshSeconds()) * 1000L;
	}

	private static final class Entry
	{
		final ItemRow row;
		final long fetchedAt;

		Entry(ItemRow row, long fetchedAt)
		{
			this.row = row;
			this.fetchedAt = fetchedAt;
		}
	}
}
