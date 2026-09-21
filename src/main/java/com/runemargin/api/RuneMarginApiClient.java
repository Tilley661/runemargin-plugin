package com.runemargin.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.runemargin.RuneMarginConfig;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Thin async client over the Rune Margin public JSON API — the Java counterpart
 * of the website's {@code frontend/src/lib/api.ts}. Uses the RuneLite-provided
 * {@link OkHttpClient} and {@link Gson}. All calls are enqueued, so they run off
 * the game/EDT thread; callbacks fire on OkHttp's dispatcher thread and callers
 * must marshal any Swing work back onto the EDT themselves.
 */
@Slf4j
@Singleton
public class RuneMarginApiClient
{
	private static final Type ITEM_ROW_LIST = new TypeToken<List<ItemRow>>()
	{
	}.getType();
	private static final Type DECANT_ROW_LIST = new TypeToken<List<DecantRow>>()
	{
	}.getType();
	private static final Type SET_ROW_LIST = new TypeToken<List<SetRow>>()
	{
	}.getType();

	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final RuneMarginConfig config;

	@Inject
	public RuneMarginApiClient(OkHttpClient okHttpClient, Gson gson, RuneMarginConfig config)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
		this.config = config;
	}

	/** GET /analytics/best-flips — the top flips right now, ranked by realistic profit. */
	public void bestFlips(int minVolume, int limit, Boolean members,
		Consumer<List<ItemRow>> onOk, Consumer<Throwable> onErr)
	{
		HttpUrl.Builder builder = base().newBuilder()
			.addPathSegments("analytics/best-flips")
			.addQueryParameter("min_volume", Integer.toString(minVolume))
			.addQueryParameter("limit", Integer.toString(limit));
		if (members != null)
		{
			builder.addQueryParameter("members", members.toString());
		}
		enqueue(builder.build(), ITEM_ROW_LIST, onOk, onErr);
	}

	/** GET /favourites/rows — the caller's starred items (needs a personal access token). */
	public void watchlist(Consumer<List<ItemRow>> onOk, Consumer<Throwable> onErr)
	{
		HttpUrl url = base().newBuilder().addPathSegments("favourites/rows").build();
		enqueue(url, ITEM_ROW_LIST, onOk, onErr);
	}

	/** GET /analytics/decanting — best decant-to-(4) plays per potion family. */
	public void decanting(Consumer<List<DecantRow>> onOk, Consumer<Throwable> onErr)
	{
		HttpUrl url = base().newBuilder().addPathSegments("analytics/decanting").build();
		enqueue(url, DECANT_ROW_LIST, onOk, onErr);
	}

	/** GET /analytics/sets — combine/break arbitrage per GE item set. */
	public void sets(Consumer<List<SetRow>> onOk, Consumer<Throwable> onErr)
	{
		HttpUrl url = base().newBuilder().addPathSegments("analytics/sets").build();
		enqueue(url, SET_ROW_LIST, onOk, onErr);
	}

	/** GET /auth/tier — the caller's plan + profit cap (works with a personal access token). */
	public void tier(Consumer<TierStatus> onOk, Consumer<Throwable> onErr)
	{
		HttpUrl url = base().newBuilder().addPathSegments("auth/tier").build();
		enqueue(url, TierStatus.class, onOk, onErr);
	}

	/** GET /items/{id} — a single item with live prices and analysis. */
	public void item(int id, Consumer<ItemRow> onOk, Consumer<Throwable> onErr)
	{
		HttpUrl url = base().newBuilder()
			.addPathSegments("items/" + id)
			.build();
		enqueue(url, ItemRow.class, onOk, onErr);
	}

	/** GET /items?search=... — name search, optionally filtered by membership. */
	public void search(String query, int limit, Boolean members,
		Consumer<List<ItemRow>> onOk, Consumer<Throwable> onErr)
	{
		HttpUrl.Builder builder = base().newBuilder()
			.addPathSegment("items")
			.addQueryParameter("search", query)
			.addQueryParameter("limit", Integer.toString(limit));
		if (members != null)
		{
			builder.addQueryParameter("members", members.toString());
		}
		enqueue(builder.build(), ITEM_ROW_LIST, onOk, onErr);
	}

	private HttpUrl base()
	{
		// Hard-coded (not user-configurable): updated in lockstep with the plugin.
		return HttpUrl.parse(RuneMarginConfig.DEFAULT_API_BASE_URL);
	}

	private <T> void enqueue(HttpUrl url, Type type, Consumer<T> onOk, Consumer<Throwable> onErr)
	{
		Request.Builder builder = new Request.Builder()
			.url(url)
			.header("User-Agent", "RuneMargin-RuneLite plugin")
			.header("Accept", "application/json");

		// A personal access token, if the user pasted one, unlocks their plan's tier
		// server-side (the API applies the caller's profit cap to logged-in requests).
		String token = config.accessToken();
		if (token != null && !token.trim().isEmpty())
		{
			builder.header("Authorization", "Bearer " + token.trim());
		}

		Request request = builder.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Rune Margin request failed: {}", url, e);
				onErr.accept(e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						onErr.accept(new IOException("HTTP " + response.code() + " from " + url));
						return;
					}
					@SuppressWarnings("unchecked")
					T parsed = (T) gson.fromJson(body.charStream(), type);
					onOk.accept(parsed);
				}
				catch (Exception e)
				{
					log.debug("Rune Margin parse failed: {}", url, e);
					onErr.accept(e);
				}
			}
		});
	}
}
