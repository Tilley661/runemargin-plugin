package com.runemargin.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * A transparent, click-through overlay that rains a one-shot confetti sprinkle across the
 * whole sidebar. Sits above the panel content in a layered pane; triggered when the Session
 * profit target is reached. Being non-opaque and reporting {@link #contains} as false, it
 * never blocks clicks to the tabs and buttons beneath it.
 */
class ConfettiLayer extends JComponent
{
	private static final Color[] PALETTE = {Brand.GOLD, Brand.GOLD_BRIGHT, Brand.PARCHMENT, Brand.UP};

	private final List<Particle> particles = new ArrayList<>();
	private final Random rnd = new Random();
	private Timer timer;

	ConfettiLayer()
	{
		setOpaque(false);
	}

	/** Seed a fresh sprinkle across the full width and start it falling. */
	void burst()
	{
		int w = Math.max(getWidth(), 200);
		for (int i = 0; i < 90; i++)
		{
			Particle p = new Particle();
			p.x = rnd.nextFloat() * w;
			p.y = -rnd.nextFloat() * 60f;          // stagger the start above the top edge
			p.vx = (rnd.nextFloat() - 0.5f) * 1.6f;
			p.vy = 2f + rnd.nextFloat() * 3.5f;
			p.size = 3 + rnd.nextInt(4);
			p.color = PALETTE[rnd.nextInt(PALETTE.length)];
			particles.add(p);
		}
		if (timer == null)
		{
			timer = new Timer(30, e -> tick());
		}
		timer.start();
		repaint();
	}

	private void tick()
	{
		int h = getHeight();
		for (Iterator<Particle> it = particles.iterator(); it.hasNext(); )
		{
			Particle p = it.next();
			p.vy += 0.07f; // gravity
			p.x += p.vx;
			p.y += p.vy;
			if (p.y > h + 6)
			{
				it.remove();
			}
		}
		if (particles.isEmpty())
		{
			timer.stop();
		}
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		if (particles.isEmpty())
		{
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		for (Particle p : particles)
		{
			g2.setColor(p.color);
			g2.fillRect((int) p.x, (int) p.y, p.size, p.size);
		}
		g2.dispose();
	}

	/** Click-through: never intercept mouse events meant for the content below. */
	@Override
	public boolean contains(int x, int y)
	{
		return false;
	}

	@Override
	public void removeNotify()
	{
		if (timer != null)
		{
			timer.stop();
		}
		super.removeNotify();
	}

	private static final class Particle
	{
		float x;
		float y;
		float vx;
		float vy;
		int size;
		Color color;
	}
}
