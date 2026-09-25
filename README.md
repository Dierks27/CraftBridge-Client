# CraftBridge Client

An **optional** client mod for servers running the [CraftBridge](https://github.com/Dierks27/CraftBridge)
Paper plugin. It exists to remove one ceiling, and it does nothing else.

At a Linked Workbench the plugin shows JEI what is in nearby storage by writing item stacks
into the 36 free slots of the open crafting menu, because that is where JEI looks when it
decides whether a recipe's [+] is available. That works, and it caps out at **36 item types**.
A real base has hundreds. With this mod installed the counts are sent to the client directly,
so the [+] sees everything in range — and the plugin skips the phantom slots entirely for you,
because you no longer need them.

**Without a CraftBridge server, the mod is inert.** It says hello once after joining; if nothing
answers, it stays dormant for the whole session and JEI behaves exactly as it does with the mod
uninstalled. It has no items, no blocks, no keybinds of its own and no config.

## Requirements

| | Minecraft 26.2 | Minecraft 26.3 |
|---|---|---|
| Fabric | loader 0.19.5+, Fabric API 0.160.0+26.2 | loader 0.19.5+, Fabric API 0.161.0+26.3 |
| NeoForge | 26.2.0.82+ | 26.3.0.16-beta+ |
| JEI | 30.32.0.209 | 31.7.0.34 |
| Side | **Client only.** Do not put it on the server. | |
| Server | CraftBridge 0.14+ (link protocol 3) | CraftBridge 0.14+ on Paper 26.3 (link protocol 3) |

Every target is built from the same sources; download the jar whose name matches your
Minecraft version and loader, e.g. `craftbridge-client-26.3-fabric-0.3.0.jar`.

## What it does

* **Shows you your storage.** A panel down the left of the crafting screen lists every item
  type in range with its count, most numerous first. This is the surface the phantom slots
  used to be, without their 36-type ceiling.
* **At a Combo Chest too.** Opening a Combo Chest shows the same panel beside its terminal
  GUI, over the containers the terminal reads; the terminal itself looks and works as before.
* **Click to take.** Left-click takes a stack to the cursor, right-click takes half a stack,
  shift-click takes as many as fit into your inventory — the same rules as a phantom slot,
  because the server runs both through the same code. The mod moves no items itself: it names
  the item and the click, and the server decides the amount from what is really in range.
* **Keeps it current.** On opening a Linked Workbench the server sends a snapshot of every
  item type in range, then small deltas as things change. Each message carries a sequence
  number, so a dropped or reordered one is noticed rather than quietly leaving the view lying
  about counts; the mod asks for a fresh snapshot instead.
* **Never leaves you with less.** The server keeps your phantom slots until the panel has
  actually drawn a frame — not when the snapshot arrives, but when it is on the screen. A mod
  that cannot show you anything therefore falls back to the server's own view rather than to
  nothing at all.
* **Fills the grid through the server.** JEI's [+] sends the plugin the *name of the recipe*
  and nothing else. The mod never moves an item itself and never tells the server what it has:
  the server holds the inventory, does its own permission and reach checks, and decides what
  may be taken and from where. A modified client gets nothing it could not get by clicking.
* **Choose how many.** At a Linked Workbench, JEI's [+] for a crafting recipe takes a count:
  scroll the mouse wheel over it (shift: steps of 8) and its tooltip shows "Crafts: N"; a
  left-click then fills the grid for that many. Right-click the [+] for a small prompt: type a
  number and press Craft, or pick 1, 8, 16, 64, Max (what shift-click does) or **All but one**
  (as many as possible while every chest slot an ingredient comes from keeps one of it). The
  server fills the grid for at most that many — bounded by what is in range and by stack
  sizes — and says so in chat when it cannot. Without a CraftBridge session the [+] is JEI's
  own and none of this applies.
* **Middle-click to sort.** In a chest, barrel or shulker box screen, middle-click (your
  pick-block binding) over the container's slots sorts the container; over your own slots it
  sorts your main rows. The server does the sorting, by the same rules as `/sort`, and only
  when it has said it will (a flag in its hello: sorting on, `craftbridge.sort`, and the
  "Middle-click sort" toggle in `/sort settings`, which is on by default). Anywhere else, or
  in creative (where middle-click copies a stack), or with an item on the cursor, the click is
  left alone. Another sorting mod that also uses middle-click will not fire while this is on;
  turn the toggle off in `/sort settings` to give the click back to it.
* **Shows the server's custom items.** Renamed items carrying plugin data are not registry
  entries of their own, so JEI has no tile for them and no way to look their recipes up. The
  server sends a catalog of them and the mod registers each as a JEI subtype.

## Not in scope

No client-side inventory manipulation, no automation, no rendering changes, no replacement for
the server's own permission checks. If the server says no, the answer is no.

## Building

```
./gradlew build                    # Minecraft 26.2, the default (mcTarget in gradle.properties)
./gradlew build -PmcTarget=26.3    # Minecraft 26.3
```

Jars land in `fabric/build/libs/` and `neoforge/build/libs/`, named after their Minecraft
version, so both targets' jars can sit side by side. CI builds every target on each push.

Each target's Minecraft, JEI, Fabric and NeoForge versions are pinned in
`versions/<minecraft>.properties`; everything else is shared. Adding a Minecraft version means
adding a file there and a matrix entry in `.github/workflows/build.yml`.

## Layout

```
versions/  per-Minecraft dependency versions, one .properties file per target
common/    shared sources, compiled by both loader modules (not a Gradle project of its own)
fabric/    Fabric entry point and metadata
neoforge/  NeoForge entry point and metadata
```

There is deliberately no Architectury layer. Minecraft ships unobfuscated from the 1.21.11
snapshots onwards, so both loaders build against the same real names: one copy of the shared
code compiles unchanged on either side, and each loader module simply adds
`common/src/main/java` to its own source set. (This is also why the Fabric module declares no
`mappings` dependency — there are no mappings left to apply.)

`common/src/main/java/com/dierks/craftbridge/link/` is **the same code as the plugin's own
`link` package**, copied rather than shared. It is the wire contract: pure framing, no
Minecraft and no Bukkit, so both sides can be sure they agree. Every payload starts with a
protocol version, and a mismatched pair refuses to talk rather than misreading each other:
this release speaks version 3, and on a server that speaks 2 (CraftBridge 0.13) the mod stays
dormant, the log says why, and the server or the mod says so once in chat.
When you change it, change it in both repositories and release them together.

The two copies are identical today but for one import line, because the plugin still keeps
`VarInts` beside its JEI code; it moves next to `link` there so the copies can be diffed byte
for byte.

The loader-specific classes do two things each: register the payload types, and pass the
connection's join/leave/tick events to `CraftBridgeClient`. Everything the mod actually does
is in `common/`.

## Notes

* **How the [+] learns about the mouse.** JEI's API has no hook for its transfer button's
  input, but it does call a transfer error's `showError` while the pointer is on the button.
  So for a recipe CraftBridge can fill, the handler answers with a *cosmetic* error that blocks
  nothing and draws no highlight: it keeps the button active, adds the count to its tooltip,
  and records which recipe's button is under the pointer and where. The mod's own screen mouse
  hooks, which see a click or a scroll before JEI's recipe screen does, act only when the
  pointer is exactly where that button was drawn a moment ago.

* **How custom items reach JEI.** JEI decides its item list and its subtypes when it loads its
  plugins, and on joining it does that before the server has answered the hello, so before the
  catalog has arrived. The catalog is written to `config/craftbridge-client/<server>.catalog`
  as it arrives and read back whenever JEI loads its plugins. When a catalog arrives that lists
  items JEI was not given, the mod restarts JEI a couple of seconds later (not while JEI's
  recipe screen is open) so they get their tiles without a rejoin. That restart uses JEI's
  internal `restartJei` hook, because JEI's API has none; if it ever fails, the log says so and
  a rejoin does the same job. On Fabric the results of the server's own recipes are used as
  well: they arrive with the recipes, so a recipe an admin saves mid-session brings its custom
  item along. A resource reload (F3+T) does not help: it rebuilds JEI's ingredient filter
  without re-running its plugins, so the catalog is not re-read.
* **The hello is sent a second after joining, not on the join tick.** A Bukkit server announces
  the channels its plugins listen on shortly after the player joins; a message sent before that
  announcement can be dropped as unknown. It is retried twice and then dropped.
* **On NeoForge the channels are registered as optional**, because the other end is a Paper
  server rather than a NeoForge one. A required payload would refuse the connection outright.
* **The panel needs elbow room**: it keeps to the space left of the crafting window and of an
  open recipe book (JEI says where they are), narrows to fit, and is skipped rather than drawn
  over either when there is no room for at least three columns. Without JEI running it falls
  back to needing a screen 420 scaled pixels wide. Whenever it cannot be shown, the server is
  told, and the phantom slots come back. The mouse wheel scrolls it when there are more item
  types than fit.
