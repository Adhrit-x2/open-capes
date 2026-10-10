# Cape Creator

A client-side Fabric mod for Minecraft 1.21.5. You design a cape in a pixel editor in-game and wear it.

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
    ./gradlew build              # jar in build/libs/
    ./gradlew runClient          # dev client
    ./gradlew runClientGameTest  # automated test, saves screenshots to build/run/clientGameTest/screenshots

## Ready-made animated capes
`capes/` has two sets of GIFs:
- **20x32:** fire, rainbow, galaxy, matrix, ocean, portal and storm (`tools/generate_capes.py`)
- **HD 40x64:** neon_edge, aurora, emblem_pulse, gradient_flow, crystal, ember_runes and sakura (`tools/generate_hd_capes.py`)
 To use one, drag it onto the editor window and click Save & Close.
To remake them or add your own, edit and run either script. It needs Pillow.

### Converting any GIF
`python3 tools/gif_to_cape.py input.gif capes/name.gif` crops a GIF to the cape shape, resizes it to 160x256 (use `--size 40` for chunky pixel art), and cuts it down to 64 frames while keeping the loop's speed.
Use `--align top|bottom` to choose which part of a tall GIF to keep, or `--fit` to fit the whole image inside the cape without cropping.
