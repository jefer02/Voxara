# Watch redesign: pass 3

Fills the screen (every page is fractions of the display) and adds headphones mode. See `PROGRESS.md` ("Watch redesign:
pass 3", "Headphones mode"), `DESIGN.md` and `docs/design/STYLE.md` §5c.

| Folder | What |
|---|---|
| `before/` | Pass 2 build: `watch_p1…p6.png` on the real Galaxy Watch 7 (432 px, 203 dp), `emu_p*.png` on the XL round emulator (480 px, 240 dp) |
| `after/` | Pass 3 build, same devices: `watch_p*` / `emu_p*` (normal), `watch_hp_p*` / `emu_hp_p*` (headphones mode on, simulated 78 / 85 dBA listening), `watch_f13_p*` (font 1.3), `watch_f20_p*` and `watch_f20_hp_p*` (font 2.0) |
| `compare/` | `watch_before_after.png`, `emulator_before_after.png` (before | after, pages 1 to 5), page sheets for headphones mode |
| `jvm/` | JVM renders (Roborazzi, not goldens) at 192 / 216 / 227 / 240 dp × font 1.0 / 1.3 / 2.0: `home`, `now`, `today`, `week`, `headphones`, `home_hp`, `home_hp_idle`, `home_hp_loud`, `es_home_long`, `mode_hp_section`, `ambient_hp` |

Page order in the real-watch and emulator shots: Overview, Now, Today, Week, Headphones, More (in headphones mode: Overview,
Headphones, Now, Today, Week, More). The watch is set to Spanish.
The transition itself is in `../headphones-mode/`.
