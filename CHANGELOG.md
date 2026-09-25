# Changelog

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
