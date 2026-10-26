/**
 * Stand-ins for the few classes of Minecraft 1.20.1 the forge-legacy test mod uses, in Intermediary's names, as a Fabric
 * mod for 1.20.1 is compiled (Loom remaps it to them): the test mod compiles against these, Fabricated remaps its
 * references to the names Forge 47 runs the game with, and the stand-ins are not shipped. Each name was checked in
 * Intermediary's 1.20.1 mappings (its obfuscated class and descriptor); the members are those of Mojang's names given in
 * each class's comment.
 */
package net.minecraft;
