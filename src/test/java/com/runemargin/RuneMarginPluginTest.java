package com.runemargin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Dev bootstrap: launches a full RuneLite client with the Rune Margin plugin
 * sideloaded. Run {@link #main(String[])} from the IDE to test in-game.
 */
public class RuneMarginPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RuneMarginPlugin.class);
		RuneLite.main(args);
	}
}
