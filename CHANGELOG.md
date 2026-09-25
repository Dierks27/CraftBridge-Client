# Changelog

## 0.4.0

**Needs CraftBridge 0.14.0 on the server.** The link is now protocol version 3. On a 0.13 server
the mod says so once in chat and stays out of the way (phantom slots keep working).

### New

* **Choose how many to craft.** Over JEI's [+], scroll the mouse wheel to pick a count (shift
  steps by 8); the tooltip shows it and the next left-click fills the grid for that many. Right-click
  [+] to type a number or pick 1, 8, 16, 64, Max or **All but one**, which leaves one of every
  ingredient in storage.
* **Middle-click sort.** Middle-click (your pick-block button) over a container or your inventory
  sorts it, when the server allows it and you have it on in `/sort settings`. Not in creative.
* **Storage panel at the Combo Chest.** The panel now also appears beside an open Combo Chest.
* **Custom items show up in JEI right away.** A new custom item gets its JEI tile without
  rejoining: JEI restarts itself when the server's catalog brings new items, and on Fabric the
  items are also read from the server's synced recipes, so they no longer land on the base item's
  page (the "Sweet Berry Soup under beetroot soup" case).

### Fixed

* Clicking the storage panel with an item on the cursor could drop that item on the ground.
* Middle and side mouse buttons acted as a left-click on the panel.
* The panel lost its link after one unreadable message, and a hidden panel could leave the player
  with neither panel nor phantom slots.
* The panel scrolls now (mouse wheel) instead of cutting off item types, and keeps clear of an open
  recipe book.
* [+] was red when nothing was in range even if the player's own inventory had everything, counted
  worn armour and the offhand, and ignored damaged, enchanted or renamed items that the server
  would accept.
* NeoForge: in singleplayer or LAN, the integrated server handled the client's own hello.
* Better log lines for what the item catalog holds, and one bad entry no longer drops the rest.

## 0.3.0

**Minecraft 26.3 support.** The mod now builds for Minecraft 26.2 and 26.3 from the same
sources, on both loaders: four jars, named `craftbridge-client-<minecraft>-<loader>-0.3.0.jar`.

| Target | NeoForge | Fabric Loader | Fabric API | JEI |
|---|---|---|---|---|
| Minecraft 26.3 | 26.3.0.16-beta | 0.19.5 | 0.161.0+26.3 | 31.7.0.34 |
| Minecraft 26.2 | 26.2.0.82 | 0.19.5 | 0.160.0+26.2 | 30.32.0.209 |

* The 26.2 jars are unchanged in behavior and remain the ones for the live server until the
  switch. Build a target with `./gradlew build -PmcTarget=26.2` (the default) or
  `-PmcTarget=26.3`; its versions are pinned in `versions/<minecraft>.properties`.
* Right-click on the storage panel (take half a stack) now names the button through
  `InputConstants.MOUSE_BUTTON_RIGHT` rather than the literal `1`. Minecraft 26.3 replaced
  GLFW with SDL3 for input, and a hard-coded GLFW button number is not safe across that.
* The NeoForge jar now declares an upper bound on Minecraft (`[26.2,26.3)` / `[26.3,26.4)`),
  so a jar built for one version refuses to load on the next instead of failing at runtime.
  The Fabric jar already did (`~26.2`).
* No protocol changes. The client link is still v2 on `craftbridge:hello`. JEI 31.7's
  recipe-transfer channels and payloads, and Fabric API 0.161's `fabric:recipe_sync` payload,
  are identical to their 26.2 counterparts, so the CraftBridge 0.13 server plugin needs no
  update for 26.3 clients.
