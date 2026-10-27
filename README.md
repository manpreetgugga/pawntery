# Pawtillery

A turn-based backyard throwing game for Android, for kids and grown-ups.

Two neighbors' pets, Dog and Cat, live on either side of a garden fence. First each player **builds a fort** from household junk to protect their pet. Then they take turns **throwing messy things over the fence**. Every hit fills the other pet's **mess meter**. A pet at 100% mess loses; after 10 throws each, the cleaner pet wins.

## What makes it different

| | Pocket Tanks | Angry Birds | Cat vs Dog | **Pawtillery** |
|---|---|---|---|---|
| You build your own defense | no | no (levels are fixed) | no | **yes: 6 pieces, real physics** |
| Opponent | another tank | fixed pigs | another pet | **another pet who also builds** |
| Damage | health/points | destruction | health | **mess meter, nothing violent** |
| Special throws | weapon list | bird powers | power-ups | **per-pet signature throw** |

## How to play

- **Build:** tap a piece (box, crate, plank, flower pot, gnome, tire), then touch your yard to drop it. Pieces stack and topple for real. "Turn" rotates, "Undo" takes the last one back, "Auto" builds a fort for you.
- **Throw:** drag back anywhere and let go, like a slingshot. Further back throws harder.
- **Items:** Mud Ball (unlimited), Water Balloon (soaks everything nearby, even behind walls), Bowling Ball (smashes forts), plus each pet's signature: Dog's **Boomerang Bone** curves back to hit from behind, Cat's **Hairball** splits in three when you tap.
- **Modes:** *Easy* shows the full aim guide and has no wind. *Pro* has wind that changes every turn and only a short aim guide.
- **Play vs Computer** or **2 Players on one phone**.

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

## Credits

- Fonts: [Titan One](https://fonts.google.com/specimen/Titan+One) and [Varela Round](https://fonts.google.com/specimen/Varela+Round), both under the SIL Open Font License (`assets/fonts/*-OFL.txt`).
- All art is drawn in code and all sounds are synthesized by `tools/gen_sounds.py`.
