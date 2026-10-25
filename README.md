# PvP Bot (Fabric, Minecraft 26.3) - server-side, no client mod needed

Uses Mojang names (26.x has no Yarn). Java 25, Loom 1.19.1, Fabric API 0.163.0+26.3.

## Commands (singleplayer, survival; Bot/bot both work)
- /bot <name> easy|mid|hard|extreme   spawn a bot with that player's skin
- /bot clear / /bot skin <username>
- /botkit diamond sword | nethpot | crystal | mace | elytra mace | spearmace
- /botsetting start | pause
- /botsetting sprint | crit | nocrit | strafing | heal | pearl | macepvp | elytramace | spearmace  [on|off] (no on/off = toggle; streffing also works)

## Build on a phone (no PC)
1. Make a new GitHub repo, upload everything in this folder (keep the .github folder).
2. Actions tab -> "build" -> open the latest run -> download artifact "pvpbot-jar".
3. Use the jar WITHOUT "-sources" in its name. Put it + Fabric API 0.163.0+26.3 in mods.
Build errors: copy the red text to me and I'll fix it.
