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
uninstalled. It has no items, no blocks, no GUI, no keybinds and no config.

## Requirements

| | |
|---|---|
| Minecraft | 26.2 |
| Loader | Fabric (loader 0.19.5+, Fabric API) **or** NeoForge 26.2.0.82+ |
| JEI | 30.32.0.209 |
| Side | **Client only.** Do not put it on the server. |
| Server | CraftBridge 0.10+ |

Both loaders are built from the same sources; download whichever jar matches your instance.

## What it does

* **Shows you your storage.** A panel down the left of the crafting screen lists every item
  type in range with its count, most numerous first. This is the surface the phantom slots
  used to be, without their 36-type ceiling.
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
* **Shows the server's custom items.** Renamed items carrying plugin data are not registry
  entries of their own, so JEI has no tile for them and no way to look their recipes up. The
  server sends a catalog of them and the mod registers each as a JEI subtype.

## Not in scope

No client-side inventory manipulation, no automation, no rendering changes, no replacement for
the server's own permission checks. If the server says no, the answer is no.

## Building

```
./gradlew build
```

Jars land in `fabric/build/libs/` and `neoforge/build/libs/`.

## Layout

```
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
protocol version, and a mismatched pair refuses to talk rather than misreading each other.
When you change it, change it in both repositories and release them together.

The two copies are identical today but for one import line, because the plugin still keeps
`VarInts` beside its JEI code; it moves next to `link` there so the copies can be diffed byte
for byte.

The loader-specific classes do two things each: register the payload types, and pass the
connection's join/leave/tick events to `CraftBridgeClient`. Everything the mod actually does
is in `common/`.

## Notes

* **The item catalog applies from the next time you join.** JEI decides its item list and its
  subtypes when it loads its plugins, which can happen before the server has told us anything.
  The catalog is written to `config/craftbridge-client/<server>.catalog` as it arrives and read
  back at plugin load, so a brand new custom item appears in the list the next time you connect,
  rather than the instant it is sent. A resource reload (F3+T) is not enough: it rebuilds JEI's
  ingredient filter without re-running its plugins, so the catalog is not re-read.
* **The hello is sent a second after joining, not on the join tick.** A Bukkit server announces
  the channels its plugins listen on shortly after the player joins; a message sent before that
  announcement can be dropped as unknown. It is retried twice and then dropped.
* **On NeoForge the channels are registered as optional**, because the other end is a Paper
  server rather than a NeoForge one. A required payload would refuse the connection outright.
* **The panel needs elbow room**: it is skipped on screens narrower than 420 scaled pixels
  rather than drawn over the crafting GUI.
