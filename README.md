# Pawtillery

A turn-based backyard throwing game for Android, for kids and grown-ups.

Two neighbors' pets live on either side of a garden fence. First each player **builds a fort** from household junk to protect their pet. Then they take turns **throwing messy things over the fence**. Every hit fills the other pet's **mess meter**. A pet at 100% mess loses; after 10 throws each, the cleaner pet wins.

## What makes it different

| | Pocket Tanks | Angry Birds | Cat vs Dog | **Pawtillery** |
|---|---|---|---|---|
| You build your own defense | no | no (levels are fixed) | no | **yes: 6 pieces, real physics** |
| Opponent | another tank | fixed pigs | another pet | **another pet who also builds** |
| Damage | health/points | destruction | health | **mess meter, nothing violent** |
| Special throws | weapon list | bird powers | power-ups | **per-pet signature throw** |

## How to play

- **Build:** tap a piece (box, crate, plank, flower pot, gnome, tire), then touch your yard to drop it. Pieces stack and topple for real. "Turn" rotates, "Undo" takes the last one back, "Auto" builds a fort for you.
- **Throw:** drag back anywhere and let go, like a slingshot. Further back throws harder. For precise, repeatable shots, use the **angle and power** steppers (one degree or one percent per tap, hold to repeat) and tap **Throw!**. Your aim is kept between turns, so you can adjust from your last throw.
- **Trick shots:** a boomerang that hits on its way back, a bowling ball or shell that banks off something first, or two or more split pieces landing (Double!, Triple!) each earn a 5-treat bonus.
- **Items:** Mud Ball (unlimited), Water Balloon (soaks everything nearby, even behind walls), Bowling Ball (smashes forts), plus each pet's signature throw.
- **Modes:** *Easy* shows the full aim guide and has no wind. *Pro* has wind that changes every turn and only a short aim guide.
- **Play vs Computer** (Rookie, Normal or Champion; Champion wins pay 50% more treats) or **2 Players on one phone**, as a *Quick* (5 throws each) or *Full* (10 throws each) match.

### Trick Shots

Ten single-player puzzle levels. Each gives you a pet and a set of throws against a ready-made fort; mess the target up to the goal within the throw limit. Fewer throws earn up to 3 stars, and every new star is worth 10 treats. Levels open one at a time and teach each item: lobbing over walls, water splash past a roof, smashing a bunker, boomerang from behind, hairball splits, headwinds, carrots in a gale, shells banked off the hedge, and a seed storm. Later levels let you try the pets you haven't unlocked yet. A unit test plays every level with a sharp computer player to prove each one can be beaten.

### Pets

| Pet | Signature throw | How to get it |
|---|---|---|
| Dog | **Boomerang Bone**: glides out and curves back to hit from behind | Free |
| Cat | **Hairball**: tap in flight to split it in three | Free |
| Bunny | **Carrot Rocket**: fast and almost straight, for sneaky gaps | 60 treats |
| Turtle | **Shell Slide**: bounces and rolls through forts | 120 treats |
| Parrot | **Seed Shower**: tap in flight to scatter five seeds | 180 treats |

Every finished game earns **treats** (win 30, draw 18, loss 12 for a full match, plus 2 per direct hit). The computer can play any pet, so players see what they can unlock.

## Build the Android app

1. Install [Android Studio](https://developer.android.com/studio) (it includes the Android SDK and JDK 17+).
2. **File > Open** this folder. Let Gradle sync finish.
3. Plug in an Android phone with USB debugging on (or start an emulator), pick the **android** run configuration and press **Run**.
4. For a Play Store build: **Build > Generate Signed App Bundle / APK**, choose *Android App Bundle*, and create an upload key (keep the `.jks` file and passwords safe and out of git).

From a terminal with the SDK installed: `./gradlew :android:assembleDebug` (APK in `android/build/outputs/apk/debug/`).

## Project layout

| Folder | What's in it |
|---|---|
| `core/` | The whole game in Kotlin with libGDX + Box2D: rules (`Match.kt`), computer player, drawing, screens |
| `android/` | Android launcher, manifest, icon |
| `lwjgl3/` | Desktop window for quick testing on a computer (`./gradlew :lwjgl3:run`) |
| `assets/` | Fonts and sound effects shared by both |
| `tools/gen_sounds.py` | Regenerates the sound effects (`python3 tools/gen_sounds.py`) |

Run the unit tests with `./gradlew :core:test`. They play full computer-vs-computer matches on the real physics.

No Android SDK on the machine (for example on CI)? `./gradlew -p tools/desktop-build :core:test :lwjgl3:build` builds and tests everything except the Android launcher.

`./gradlew :lwjgl3:tour --args="<out-dir> 1280 720"` plays a scripted session through real touch input and saves a screenshot of every screen, for checking the look without a phone.

## Credits

- Fonts: [Titan One](https://fonts.google.com/specimen/Titan+One) and [Varela Round](https://fonts.google.com/specimen/Varela+Round), both under the SIL Open Font License (`assets/fonts/*-OFL.txt`).
- All art is drawn in code and all sounds are synthesized by `tools/gen_sounds.py`.
