"""Tests of ci/corpus-causes.sh, on a game folder as the harness leaves it: python3 -B -m unittest discover -s ci -p 'test_*.py'."""
import subprocess
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent

# Fabricated's probe of MixinExtras's version on Forge's layers, which fails as expected, at debug level.
PROBE = ("[10Nov2026 02:32:30.102] [main/DEBUG] [io.github.cmdralph.fabricated.loader.FabricDependencyLocator/]: "
         "Could not determine the version of com.llamalad7.mixinextras.MixinExtrasBootstrap\n"
         "java.lang.ClassNotFoundException: com.llamalad7.mixinextras.MixinExtrasBootstrap\n"
         "\tat java.lang.Class.forName(Class.java:377) ~[?:?]\n")
INFO = "[10Nov2026 02:32:31.000] [main/INFO] [FrozenLib/]: Loading config WWBlockConfig from wilderwild\n"
# The Forge 47 advanced client's stop: Mixin's failure, uncaught, as the JVM exits, which only the console has.
STOP = ('Exception in thread "main" java.lang.RuntimeException: java.lang.reflect.InvocationTargetException\n'
        "\tat cpw.mods.bootstraplauncher.BootstrapLauncher.main(BootstrapLauncher.java:141)\n"
        "Caused by: org.spongepowered.asm.mixin.injection.throwables.InjectionError: Critical injection failure: "
        "Multi-argument modifier method useGravity in mixin/frozenlib failed injection check\n"
        "\tat org.spongepowered.asm.mixin.injection.struct.InjectionInfo.postInject(InjectionInfo.java:468)\n"
        "\t... 22 more\n"
        "\tSuppressed: com.llamalad7.mixinextras.sugar.impl.SugarApplicationException: Failed to validate sugar @Local double\n"
        "\t\tat com.llamalad7.mixinextras.sugar.impl.SugarInjector.validateApplicators(SugarInjector.java:183)\n"
        "\tCaused by: org.spongepowered.asm.mixin.injection.modify.InvalidImplicitDiscriminatorException: "
        "Found 4 candidate variables but exactly 1 is required.\n"
        "\t\tat org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator.findImplicitLocal(Unknown Source)\n"
        "\t\t... 33 more\n")
ENDED = ('FABRICATED-TEST game ended from thread "DestroyJavaVM":\n'
         "\tat java.base@17.0.21/java.lang.Shutdown.shutdown(Shutdown.java:185)\n"
         "Minecraft exited with code: 1\n")
EARLIER = ("[02:32:20] [Worker-Main-2/ERROR] [minecraft/]: A recipe failed\n"
           "java.lang.IllegalStateException: Unknown item\n\tat net.minecraft.Recipes.load(Recipes.java:1)\n")


def run(crash=None, console=None):
    """The digest of a client run whose console ends with the stop, or is the given one; its output lines."""
    work = Path(tempfile.mkdtemp())
    logs = work / "run" / "logs"
    logs.mkdir(parents=True)
    (logs / "latest.log").write_text(INFO * 3)
    (logs / "debug.log").write_text(INFO + PROBE + INFO)
    (logs / "fabric-bridge-report.txt").write_text("== Entrypoint failures ==\n\n")
    (work / "client.out").write_text(console or EARLIER + INFO.replace("10Nov2026 ", "") + STOP + ENDED)
    if crash:
        (work / "run" / "crash-reports").mkdir()
        (work / "run" / "crash-reports" / "crash-client.txt").write_text(crash)
    result = subprocess.run(["bash", str(HERE / "corpus-causes.sh"), str(work / "run"), str(work / "client.out"), "client"],
                            capture_output=True, text=True)
    return result.returncode, result.stdout.splitlines()


class StopTest(unittest.TestCase):
    def test_the_consoles_last_chain_comes_first(self):
        status, lines = run()
        self.assertEqual(status, 0)
        stop = lines.index("-- the console's last exception chain before the game ended")
        self.assertLess(stop, lines.index("-- Fabricated's report: entrypoint failures"))
        self.assertLess(stop, lines.index("-- latest.log: Mixin's errors"))
        block = lines[stop:lines.index("-- Fabricated's report: entrypoint failures")]
        self.assertTrue(block[1].endswith('Exception in thread "main" java.lang.RuntimeException: java.lang.reflect.InvocationTargetException'))
        # The suppressed exception's cause is the innermost one: what Mixin found at the point.
        self.assertIn("  innermost cause, from line 12:", block)
        self.assertIn("Found 4 candidate variables but exactly 1 is required.", "\n".join(block))
        self.assertIn("Critical injection failure: Multi-argument modifier method useGravity", "\n".join(block))
        # Nor the test mod's shutdown hook's frames, nor the earlier chain, which the console's chains list below.
        self.assertNotIn("Shutdown", "\n".join(block))
        self.assertNotIn("Unknown item", "\n".join(block))
        below = lines[lines.index("-- the console: exception chains before its last (the last one's innermost frames)"):]
        self.assertIn("2: java.lang.IllegalStateException: Unknown item", below)
        self.assertNotIn("InvalidImplicitDiscriminatorException", "\n".join(below[:4]))

    def test_an_exception_a_debug_line_logged_is_left_out(self):
        status, lines = run()
        self.assertEqual(status, 0)
        self.assertNotIn("MixinExtrasBootstrap", "\n".join(lines))

    def test_an_exception_as_a_started_game_stops_is_no_stop(self):
        started = "[05:03:03] [Server thread/INFO] [minecraft/DedicatedServer]: Done (2.104s)! For help, type \"help\"\n"
        stopping = ("[05:04:14] [Server thread/INFO] [minecraft/MinecraftServer]: Stopping the server\n"
                    "[05:04:15] [Ledger Database/ERROR] [Exposed/]: Transaction attempt #0 failed\n"
                    "org.sqlite.SQLiteException: [SQLITE_CONSTRAINT_NOTNULL] A NOT NULL constraint failed\n"
                    "\tat org.sqlite.core.DB.newSQLException(DB.java:1179)\n")
        status, lines = run(console=started + stopping)
        self.assertEqual(status, 0)
        self.assertEqual(lines[1:3], ["-- no crash report", "-- the game started, and the run stopped it"])
        # The console's chains list it, below.
        self.assertIn("4: org.sqlite.SQLiteException: [SQLITE_CONSTRAINT_NOTNULL] A NOT NULL constraint failed", lines)

    def test_a_crash_report_is_the_stop(self):
        crash = ("---- Minecraft Crash Report ----\nDescription: Initializing game\n\n"
                 "java.lang.IllegalStateException: Can not register to a locked registry\n\tat a.B.c(B.java:1)\n"
                 "-- System Details --\n")
        status, lines = run(crash)
        self.assertEqual(status, 0)
        self.assertEqual(lines[1:3], ["-- crash-client.txt", "3: java.lang.IllegalStateException: Can not register to a locked registry"])
        self.assertNotIn("-- the console's last exception chain before the game ended", lines)
        self.assertIn("-- the console: exception chains (the last one's innermost frames)", lines)


if __name__ == "__main__":
    unittest.main()
