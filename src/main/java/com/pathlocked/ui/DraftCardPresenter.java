package com.pathlocked.ui;

/**
 * Push-based bridge the plugin uses to drive the in-game draft card overlay.
 *
 * <p>The plugin already builds a {@link PanelSnapshot} for the side panel on
 * every {@code refreshPanel()}; handing that same snapshot here keeps the
 * overlay in lock-step with the panel without introducing a second data model.
 * Call {@link #setSnapshot} from the client thread (that is where the plugin
 * builds the snapshot); the overlay stores it behind a volatile reference so
 * the render thread and the AWT mouse thread read a consistent value.
 */
public interface DraftCardPresenter
{
	/**
	 * Feed the latest state. A snapshot whose {@code offers} list is null (no
	 * pending draft) or empty hides the cards; a non-empty list shows them.
	 */
	void setSnapshot(PanelSnapshot snapshot);

	/**
	 * Master on/off, intended to be wired to a config toggle. When disabled the
	 * overlay renders nothing and swallows no clicks, regardless of snapshot.
	 */
	void setEnabled(boolean enabled);
}
