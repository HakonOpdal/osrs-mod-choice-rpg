# Lane A — Draft card overlay: integration notes

Delivered in `com.pathlocked.ui`:

- **`DraftCardPresenter`** — the interface the plugin talks to (`setSnapshot`, `setEnabled`).
- **`DraftCardOverlay`** — a center-screen `Overlay` that renders the pending
  draft as trading cards and hit-tests clicks. Implements `DraftCardPresenter`.
- Reuses the existing **`PathlockedPanel.Actions`** (`pickOption`, `rerollDraft`)
  and the existing **`PanelSnapshot`** — no new data model, no new plugin methods.

The overlay is a drop-in twin of the side panel: the plugin already builds a
`PanelSnapshot` on every `refreshPanel()`, so feeding that same snapshot to the
overlay keeps the two perfectly in sync.

## Wiring (all in `PathlockedPlugin.java`)

`PathlockedPlugin` already `implements PathlockedPanel.Actions`, so it *is* the
`actions` callback. Three edits:

1. **Field** (next to `statusOverlay`):

   ```java
   private DraftCardOverlay cardOverlay;
   ```

2. **`startUp()`** — construct, register the overlay, and register its mouse
   listener. `MouseManager` must be injected (add the field if absent):

   ```java
   @Inject
   private net.runelite.client.input.MouseManager mouseManager;
   ```

   ```java
   cardOverlay = new DraftCardOverlay(client, this);
   cardOverlay.setEnabled(config.showCardOverlay());   // see config note below
   overlayManager.add(cardOverlay);
   mouseManager.registerMouseListener(cardOverlay.getMouseListener());
   ```

   (Place after the existing `overlayManager.add(statusOverlay);`.)

3. **`shutDown()`** — mirror the teardown:

   ```java
   overlayManager.remove(cardOverlay);
   mouseManager.unregisterMouseListener(cardOverlay.getMouseListener());
   ```

4. **`refreshPanel()`** — after the existing `panel.refresh(...)` call, push the
   same snapshot to the overlay. Easiest: capture the built snapshot in a local
   and feed both:

   ```java
   PanelSnapshot snapshot = PanelSnapshot.builder()
       .loggedIn(true)
       // ...existing builder chain unchanged...
       .build();
   panel.refresh(snapshot);
   if (cardOverlay != null)
   {
       cardOverlay.setSnapshot(snapshot);
   }
   ```

   Also feed the logged-out branch so the cards clear on logout:

   ```java
   PanelSnapshot loggedOut = PanelSnapshot.builder().loggedIn(false).build();
   panel.refresh(loggedOut);
   if (cardOverlay != null)
   {
       cardOverlay.setSnapshot(loggedOut);
   }
   ```

   The overlay hides itself whenever `offers` is null/empty, so no separate
   "draft cleared" call is needed — a normal `refreshPanel()` after a pick or a
   reroll turns the cards off/on automatically.

That is the entire integration. No changes to `DraftService`, the draft data, or
the pick semantics.

## Config toggle (expected, not yet added)

`DraftCardOverlay` calls no config method itself; gating is the plugin's job via
`setEnabled(...)`. Add to `PathlockedConfig`:

```java
@ConfigItem(
    keyName = "showCardOverlay",
    name = "In-game draft cards",
    description = "Show the center-screen draft cards. Off = draft only in the side panel.",
    position = <next>
)
default boolean showCardOverlay() { return true; }
```

Then in `startUp()` set the initial state (shown above) and, if you already
handle `ConfigChanged`, call `cardOverlay.setEnabled(config.showCardOverlay())`
when the key changes. When disabled the overlay draws nothing and swallows no
clicks, so the side-panel draft path is entirely unaffected.

## Clickability — what was implemented and why

RuneLite overlays are not natively clickable, so the proven pattern was used:

- The overlay records the on-screen `Rectangle` of every card and the reroll
  pill on each render (canvas-space coordinates; the overlay uses
  `OverlayPosition.DYNAMIC` + `OverlayLayer.ABOVE_WIDGETS`, so graphics and
  mouse events share one coordinate space).
- A `MouseAdapter` registered with `MouseManager` hit-tests those rectangles on
  `mousePressed`. A hit runs `actions.pickOption(index, name)` /
  `actions.rerollDraft()` and **consumes** the event; a press anywhere else on
  the card surface is also consumed so it never leaks through as a walk-here
  click on the world beneath. Presses outside the surface pass through untouched.
- Picks stay identified by `(index, expectedName)`. Clicks arrive on the AWT
  thread, but `pickOption`/`rerollDraft` already re-dispatch onto the client
  thread via `clientThread.invokeLater` and re-validate the offer name, so a
  click that raced a reroll is rejected exactly as a stale panel click is —
  no extra synchronization needed on this side.
- Hover is tracked via `mouseMoved`/`mouseDragged` and brightens the hovered
  card's border.

Hit-testing proved workable, so the display-only fallback was not needed.

## Known limitations / notes for the integrator

- **Draw order vs. right-click menus.** `ABOVE_WIDGETS` puts the cards over the
  game UI. If a card visually collides with an open right-click menu, consider
  `OverlayLayer.ALWAYS_ON_TOP`. Not observed as a problem in testing.
- **No keybind.** Clicking (or the existing side panel) is the only input.
  A keybind (1/2/3 to pick, R to reroll) would be a small follow-up if wanted.
- **Fonts.** Uses RuneLite's runescape fonts, with a plain-AWT fallback so
  headless unit tests never fail on a missing font bundle.
- **FREE drafts** (up to 6 offers) lay out 3-per-row across two rows and are
  centered; standard 1-of-3 drafts sit in a single row. Verified from 520×340
  up to 1280×720.

## Verification done in this lane

- `JAVA_HOME=<jbr-17> ./gradlew build` green; new `DraftCardOverlayTest`
  (6 cases) passing alongside the existing 19.
- Render + hit-test proven headless against a `BufferedImage`; preview PNGs
  written to `build/card-overlay-previews/` (`standard-3-region.png`,
  `free-6-mixed.png`, `small-client.png`).
