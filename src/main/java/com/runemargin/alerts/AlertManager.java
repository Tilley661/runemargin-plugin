package com.runemargin.alerts;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.runemargin.RuneMarginConfig;
import com.runemargin.api.RuneMarginApiClient;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;

/**
 * Client-side price alerts: the player watches an item for a target after-tax margin,
 * and {@link #check()} (called on a schedule) fetches each watched item and fires a
 * RuneLite notification when the target is reached. Alerts are one-shot — a watch is
 * removed once it fires. Watches persist per RuneScape name via {@link ConfigManager}.
 */
@Slf4j
@Singleton
public class AlertManager
{
	private static final String KEY_PREFIX = "watches_";
	private static final Type WATCH_LIST = new TypeToken<List<Watch>>()
	{
	}.getType();

	private final Notifier notifier;
	private final RuneMarginApiClient api;
	private final ConfigManager configManager;
	private final Gson gson;

	@Nullable
	private String profile;
	private Map<Integer, Watch> watches = new ConcurrentHashMap<>();

	@Inject
	public AlertManager(Notifier notifier, RuneMarginApiClient api, ConfigManager configManager, Gson gson)
	{
		this.notifier = notifier;
		this.api = api;
		this.configManager = configManager;
		this.gson = gson;
	}

	/** Bind to a player and load their persisted watches. No-op if unchanged. */
	public synchronized void setProfile(@Nullable String rsn)
	{
		if (rsn == null || rsn.equals(profile))
		{
			return;
		}
		profile = rsn;
		watches = load(rsn);
	}

	public void addWatch(int itemId, String name, int targetMargin)
	{
		watches.put(itemId, new Watch(itemId, name, targetMargin));
		persist();
	}

	public void removeWatch(int itemId)
	{
		watches.remove(itemId);
		persist();
	}

	public boolean isWatched(int itemId)
	{
		return watches.containsKey(itemId);
	}

	public Collection<Watch> watches()
	{
		return new ArrayList<>(watches.values());
	}

	/** Fetch each watched item; fire (and clear) any whose margin has reached target. */
	public void check()
	{
		if (watches.isEmpty())
		{
			return;
		}
		for (Watch watch : new ArrayList<>(watches.values()))
		{
			api.item(watch.itemId,
				row ->
				{
					if (row != null && row.margin != null && row.margin >= watch.targetMargin)
					{
						notifier.notify("Rune Margin: " + watch.name + " margin hit "
							+ String.format("%,d", row.margin) + " (target " + String.format("%,d", watch.targetMargin) + ")");
						watches.remove(watch.itemId);
						persist();
					}
				},
				err -> { });
		}
	}

	private void persist()
	{
		if (profile == null)
		{
			return;
		}
		try
		{
			configManager.setConfiguration(RuneMarginConfig.GROUP, KEY_PREFIX + profile,
				gson.toJson(new ArrayList<>(watches.values())));
		}
		catch (RuntimeException e)
		{
			log.debug("Failed to persist watches", e);
		}
	}

	private Map<Integer, Watch> load(String rsn)
	{
		Map<Integer, Watch> loaded = new ConcurrentHashMap<>();
		try
		{
			String json = configManager.getConfiguration(RuneMarginConfig.GROUP, KEY_PREFIX + rsn);
			if (json != null && !json.isEmpty())
			{
				List<Watch> list = gson.fromJson(json, WATCH_LIST);
				if (list != null)
				{
					for (Watch watch : list)
					{
						if (watch != null)
						{
							loaded.put(watch.itemId, watch);
						}
					}
				}
			}
		}
		catch (RuntimeException e)
		{
			log.debug("Failed to load watches for {}", rsn, e);
		}
		return loaded;
	}
}
