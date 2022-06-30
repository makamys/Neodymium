# Neodymium

Neodymium is a mod that reimplements chunk rendering in Minecraft 1.7.10 using modern OpenGL. This should improve performance on most hardware.

## Benchmarks

Tests were made staying in one place without moving the camera, on Peaceful difficulty. The FPS was measured once the chunk update number had reached the single digits and the FPS had stabilized.

| Mods                     | GPU          | OS         | Resolution | Baseline | With Neodymium | Change |
|--------------------------|--------------|------------|------------|----------|----------------|--------|
| OF + FC                  | GTX 1050Ti   | Ubuntu 20  | 854x480    | 480 FPS  |        840 FPS |   +75% |
| OF + FC                  | GTX 1050Ti   | Ubuntu 20  | 1920x1080  | 440 FPS  |        560 FPS |   +27% |
| OF + FC                  | GTX 1050Ti   | Windows 10 | 1920x1080  | 230 FPS  |        270 FPS |   +17% |
| OF + FC                  | Intel HD 630 | Windows 10 | 1920x1080  | 90 FPS   |        130 FPS |   +44% |
| GTNH 2.1.2.3qf + OF + FC | GTX 1050Ti   | Ubuntu 20  | 1920x1080  | 300 FPS  |        390 FPS |   +30% |

#### Legend
* OF: OptiFine HD_U_D6
* FC: FastCraft 1.23
* Nd: Neodymium 0.1

## Usage

Just drop it in your mods folder. You can confirm that the mod is working by pressing F3 and looking at the right side of the screen. The number of rendered meshes will be shown, along with the amount of used memory.

In the mod's config file you can find various options you can use to fine-tune the mod to suit your hardware. The config is reloaded when the chunks are reloaded (e.g. when you press F3+A), or immediately upon saving the config file if the hot swap feature is enabled.

### Debug

There are some debug key combinations provided. You have to hold down the *debug prefix key* (F4 by default) while pressing them.

* **F**: switch between Neodymium's renderer and the vanilla chunk renderer. Can be used to compare the difference the mod makes.
* **V**: toggle whether the world is rendered or not. Can be used to see the theoretical maximum FPS that can be achieved via chunk renderer optimization.
* **M**: show the VRAM debugger. The positions of the white pixels shown correspond to the offsets of memory sections allocated in the vertex buffer on the GPU.
* **Left**: reload renderers. Provided for convenience because F3+A makes you strafe left.
* **Right**: toggle renderer update speedup. While this is enabled, chunk updates will be sped up 300x. This kills your FPS but reduces the time you have to wait until all the renderers have loaded.

## How it works

The mod injects callbacks that run when world renderers (16x16x16 sections of the world) change. Right after a world renderer has finished rendering, Neodymium captures the tessellator's state and converts the mesh to its own format. The vanilla chunk renderer is disabled, and a different implementation runs instead. The mod doesn't change how meshes are constructed, only the way they get drawn. It should work with any mod that uses the tessellator to draw blocks.

There are also some additional optimizations:

* Face culling: Faces that aren't facing the camera won't be submitted for rendering. This reduces GPU workload, and will increase the framerate if the GPU was choking. Inspired by a similar optimization in Sodium.
* Mesh simplification: Experimental feature. Identical adjacent quads are merged together into a single large quad with a wrapped texture. This reduces the amount of quads that have to be rendered without affecting the visuals. However, it also slows down the fragment shader. I found it to reduce performance overall, so it's disabled by default.

The mod increases memory usage, since the chunk meshes have to be stored somewhere. On Normal render distance in a vanilla world, it uses ~70-150 MB, both in RAM and VRAM.

## Incompatibilities
* The mod performs poorly if Advanced OpenGL (occlusion culling) is turned on.
* The mod is only compatible with FastCraft if OptiFine is also present.
* OptiFine shaders aren't compatible, and probably never will be, as I have neither the knowledge nor the interest to add compatibility for them.
* Various coremods may be incompatible with Mixin. Use [Mixingasm](https://github.com/makamys/Mixingasm) to fix this.

## License

This mod is licensed under version 3 of the GNU Lesser General Public License. See [LICENSE](LICENSE) for additional information.

## Contributing

When running the mod in an IDE, add these program arguments:
```
--tweakClass org.spongepowered.asm.launch.MixinTweaker --mixin neodymium.mixin.json
```

Additionally, the following VM arguments may come in handy:
* `-ea`: enable assertions
* `-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump`: You will get terrible performance on an integrated Intel GPU if you don't add these magic words.
