# Open Capes

A client-side Fabric mod for Minecraft. You design a cape in a pixel editor in-game and wear it.

## Supported versions
Every Minecraft release from **1.21 to 1.21.11** and **26.1 to 26.3**, on Fabric with Fabric API. Each Minecraft version has its own jar, `open-capes-<version>+mc<minecraft>.jar`, so use the one that matches your game.

## Use
- Press **J** in-game to open the editor. You can rebind it under Controls → Cape Creator.
- Left click paints and right click picks a color. Use the palette or type a hex code.
- **Tool** switches between pencil and bucket fill. **Mirror** paints both halves symmetrically.
- **Size** cycles through 1x, 2x, 4x, 8x and 16x, which are 10x16, 20x32, 40x64, 80x128 and 160x256 pixels. 8x and 16x suit detailed images. They're smoothly filtered and too fine to paint by hand.
- Imported images are cropped to the cape's shape from the middle, not stretched. Long GIFs keep every n-th frame so the whole loop fits in 64 frames.
- Drag a PNG onto the window to import it into the current frame.

### Animated capes
- The `<` `>` buttons (or the arrow keys) switch between frames. `+` adds a copy of the current frame, and `-` deletes it.
- **FPS** sets the animation speed.
- Drag a GIF onto the window to import all of its frames. The GIF's speed and resolution are used too.
- Cape textures from cape sites work too. That means the 64x32 layout, any HD multiple, or animated ones with frames stacked vertically in a PNG, as well as the old 22x17 layout. The back panel is cut out of each frame automatically.
- The preview and your in-game cape play the animation. The frames are saved stacked vertically in `design.png`.
- **Saved** opens your saved capes. Click one to wear it, save the cape you're wearing under a name, or delete one. In game, **]** and **[** switch to the next and previous saved cape. You can also drop GIFs into `config/capecreator/saved/`.
- **Link** imports a GIF, PNG or JPG from a web link. You can also copy a link and press Ctrl/Cmd+V in the editor. Use the image address: right-click the GIF and choose "Copy Image Address". Every cape imported from a link is added to your saved capes automatically, named after the link's file name, or "link cape" if the name is just random letters and numbers.
- Links and dropped images open a **crop screen**. Drag the cape-shaped box over the part you want, scroll or use −/+ to resize, and check the live preview. The import size is picked from the crop's size, up to 16x.
- **Save & Close** (or Esc) saves your design. **Cancel** throws away your changes.
- Your design is saved to `.minecraft/config/capecreator/design.png`.

**Cape** turns your custom cape on or off.
The player preview is 3D. Drag left/right to turn it and up/down to look from above or below, scroll to zoom, and right-click to reset.
**Sides** sets the colors of the inside of the cape and its edges. Each one is either **Auto**, the cape's main background color (its most common color across all frames), or any color from the palette or a hex code. Saved capes remember their side colors. Importing a new image resets them to Auto.
The cape also shows up on elytra. Only you can see it, because it is client-side.
The cape layer must be turned on (Options → Skin Customization → Cape).

## Build
    ./gradlew dist                          # builds every version into dist/
    ./gradlew -Pmc=1.21.5 :1.21.5:build     # one version (jar in versions/1.21.5/build/libs/)
    ./gradlew -Pmc=26.3 :26.3:runClient     # play-test a version
    tools/test-versions.sh                  # in-game test of every version (or list versions to test)

`-Pmc=a,b` limits the build to those versions, which is much faster while working on one.

### How multi-version works
- There is **one copy of the code** in `src/`, written against Minecraft 1.21.5 with Mojang's official names. `versions/<mc>/gradle.properties` holds each version's Minecraft, Fabric API and Java versions.
- Each version's code is preprocessed from `src/` (see the top of `build.gradle`):
  - Code that only applies to some versions is wrapped in `//#if MC >= 1.21.6` … `//#elif …` … `//#else` … `//#endif`.
  - Renamed classes and methods (for example `ResourceLocation` → `Identifier` from 1.21.11, or `GuiGraphics` → `GuiGraphicsExtractor` from 26.1) are listed once in `renames()` and swapped automatically.
- Most version differences live in `Compat.java`, and the screen input changes from 1.21.9 live in `CompatScreen.java`. The rest of the code doesn't need version checks.
- 1.21.x builds with Loom's remapping plugin. 26.x is unobfuscated and uses `net.fabricmc.fabric-loom` with Java 25.

### Testing
- **1.21.4 and later:** Fabric's client game test (`src/gametest`). It drives the real game: it draws, imports GIFs and links, crops, saves and switches capes, uses Sides, rotates the preview with the mouse, and screenshots the cape in the world. Screenshots go to `versions/<mc>/build/run/clientGameTest/screenshots`.
- **1.21 – 1.21.3:** these predate that API, so `src/smoketest` instead creates a world, checks the cape is on the player and in its texture, opens the editor, and takes screenshots.

## Ready-made animated capes
`capes/` has two sets of GIFs:
- **20x32:** fire, rainbow, galaxy, matrix, ocean, portal and storm (`tools/generate_capes.py`)
- **HD 40x64:** neon_edge, aurora, emblem_pulse, gradient_flow, crystal, ember_runes and sakura (`tools/generate_hd_capes.py`)
 To use one, drag it onto the editor window and click Save & Close.
To remake them or add your own, edit and run either script. It needs Pillow.

### Converting any GIF
`python3 tools/gif_to_cape.py input.gif capes/name.gif` crops a GIF to the cape shape, resizes it to 160x256 (use `--size 40` for chunky pixel art), and cuts it down to 64 frames while keeping the loop's speed.
Use `--align top|bottom` to choose which part of a tall GIF to keep, or `--fit` to fit the whole image inside the cape without cropping.
