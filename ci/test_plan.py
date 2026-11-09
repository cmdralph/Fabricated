"""Tests of ci/plan.py: python3 -B -m unittest discover -s ci -p 'test_*.py'."""
import json
import tempfile
import unittest
from pathlib import Path

import plan

PROPERTIES = {
    ("neoforge", "1.21.1"): "neoforge_version=21.1.265\njava_version=21\nci_plugin_hosts=jei@a rei@b\n",
    ("neoforge", "1.21.11"): "neoforge_version=21.11.45\njava_version=21\n",
    ("neoforge", "26.1.2"): "neoforge_version=26.1.2.116\njava_version=25\nci_renderers=sodium@x iris@y\n",
    ("forge", "26.3"): "forge_version=66.1.0\njava_version=25\nci_plugin_hosts=wthit@w\n",
}


def tree(*held):
    """A repository with the given platforms' version folders, and a Fabric API plan for each."""
    root = Path(tempfile.mkdtemp())
    for (platform, version), text in PROPERTIES.items():
        if platform not in held:
            continue
        folder = root / "mc" / platform / version
        folder.mkdir(parents=True)
        (folder / "gradle.properties").write_text(text)
        plan_file = folder / plan.FABRIC_API_PLAN
        plan_file.parent.mkdir(parents=True)
        plan_file.write_text(json.dumps({"fabricApiVersion": f"0.1.0+{version}"}))
    return root


def built(result):
    return [(t["platform"], t["minecraft"]) for t in result["targets"]]


class TouchedTest(unittest.TestCase):
    def setUp(self):
        self.root = tree("neoforge")

    def test_a_version_folder_alone_touches_that_version(self):
        self.assertEqual(plan.touched(self.root, ["mc/neoforge/26.1.2/src/main/java/A.java"]), {"neoforge": {"26.1.2"}})

    def test_shared_code_touches_every_platform_the_tree_holds(self):
        self.assertEqual(plan.touched(self.root, ["core/src/main/java/A.java"]), {"neoforge": {None}})

    def test_documentation_and_the_release_touch_nothing(self):
        paths = ["docs/README.md", "README.md", ".github/workflows/release.yml", "ci/curseforge.py"]
        self.assertEqual(plan.touched(self.root, paths), {})

    def test_another_platforms_files_touch_nothing_here(self):
        self.assertEqual(plan.touched(self.root, ["mc/forge/26.3/gradle.properties", "loader/forge-shared/A.java"]), {})

    def test_the_forge_shared_folders_belong_to_both_forge_platforms(self):
        root = tree("forge")
        self.assertEqual(plan.touched(root, ["ci/forge-shared/platform.sh"]), {"forge": {None}})
        self.assertEqual(plan.platforms_of("mc/forge-mixinextras.gradle"), ("forge", "forge-legacy"))
        self.assertEqual(plan.platforms_of("mc/platform-forge.gradle"), ("forge", "forge-legacy"))

    def test_the_platforms_own_folders(self):
        self.assertEqual(plan.platforms_of("ci/neoforge/platform.sh"), ("neoforge",))
        self.assertEqual(plan.platforms_of("mc/platform-forge-legacy.gradle"), ("forge-legacy",))
        self.assertIsNone(plan.platforms_of("ci/smoke-server.sh"))
        self.assertIsNone(plan.platforms_of(".github/workflows/ci.yml"))


class TargetsTest(unittest.TestCase):
    def setUp(self):
        self.root = tree("neoforge")

    def test_the_full_rule(self):
        self.assertEqual([t["minecraft"] for t in plan.targets(self.root, ["mc/neoforge/1.21.1/x", "mc/neoforge/26.1.2/y"])],
                         ["1.21.1", "26.1.2"])
        self.assertEqual([t["minecraft"] for t in plan.targets(self.root, ["mc/neoforge/1.21.1/x", "ci/neoforge/platform.sh"])],
                         ["1.21.1", "1.21.11", "26.1.2"])
        self.assertEqual([t["minecraft"] for t in plan.targets(self.root, None)], ["1.21.1", "1.21.11", "26.1.2"])

    def test_the_quick_rule_boots_the_version_alone_touched_else_the_newest(self):
        self.assertEqual([t["minecraft"] for t in plan.targets(self.root, ["mc/neoforge/1.21.1/x"], quick=True)], ["1.21.1"])
        self.assertEqual([t["minecraft"] for t in plan.targets(self.root, ["mc/neoforge/1.21.1/x", "mc/neoforge/1.21.11/x"], quick=True)],
                         ["26.1.2"])
        self.assertEqual(plan.targets(self.root, ["docs/a.md"], quick=True), [])

    def test_a_target_reads_its_pins(self):
        target = plan.target(self.root, "neoforge", "26.1.2")
        self.assertEqual((target["loader"], target["java"], target["fabric_api"], target["renderers"], target["fabric_renderers"]),
                         ("26.1.2.116", "25", "0.1.0+26.1.2", "sodium@x iris@y", "sodium iris"))
        self.assertNotEqual(target["pins"], plan.target(self.root, "neoforge", "1.21.11")["pins"])

    def test_a_value_a_shell_would_read_is_refused(self):
        (self.root / "mc/neoforge/26.1.2/gradle.properties").write_text("neoforge_version=1;rm -rf /\njava_version=25\n")
        with self.assertRaises(SystemExit):
            plan.target(self.root, "neoforge", "26.1.2")

    def test_a_folder_that_isnt_a_version_is_refused(self):
        (self.root / "mc/neoforge/$(x)").mkdir()
        (self.root / "mc/neoforge/$(x)/gradle.properties").write_text("")
        with self.assertRaises(SystemExit):
            plan.versions(self.root, "neoforge")


class PlanTest(unittest.TestCase):
    def setUp(self):
        self.root = tree("neoforge")

    def test_a_push_to_a_working_branch_gets_the_fast_job(self):
        result = plan.plan(self.root, "push", "refs/heads/claude/dev", ["mc/neoforge/1.21.11/a"])
        self.assertTrue(result["fast"] and result["core"])
        self.assertEqual(result["boot"]["minecraft"], "1.21.11")
        self.assertEqual((result["targets"], result["tests"], result["save"]), ([], [], False))

    def test_documentation_alone_boots_nothing(self):
        result = plan.plan(self.root, "push", "refs/heads/claude/dev", ["docs/roadmap.md"])
        self.assertTrue(result["core"])
        self.assertFalse(result["fast"])
        self.assertEqual(result["boot"], {})

    def test_an_open_pull_request_covers_the_push(self):
        result = plan.plan(self.root, "push", "refs/heads/claude/dev", ["core/A.java"], covered=True)
        self.assertFalse(result["fast"] or result["core"])
        self.assertEqual(result["targets"], [])

    def test_a_merge_builds_what_it_changed_and_fills_the_caches(self):
        result = plan.plan(self.root, "push", "refs/heads/main", ["mc/neoforge/26.1.2/a"])
        self.assertEqual(built(result), [("neoforge", "26.1.2")])
        self.assertTrue(result["save"] and result["core"])
        self.assertFalse(result["game"] or result["tests"] or result["fast"])
        self.assertFalse(plan.plan(self.root, "push", "refs/heads/forge", ["docs/a.md"])["core"])

    def test_a_pull_request_runs_the_parts_of_what_it_touches(self):
        result = plan.plan(self.root, "pull_request", "refs/pull/1/merge", ["mc/neoforge/26.1.2/a"])
        self.assertEqual(built(result), [("neoforge", "26.1.2")])
        self.assertTrue(result["game"] and result["core"])
        self.assertFalse(result["save"])
        self.assertEqual([(t["part"], t["sides"]) for t in result["tests"]],
                         [("client", "client"), ("mods", "client"), ("server", "server")])

    def test_extended_adds_the_corpus_part(self):
        result = plan.plan(self.root, "workflow_dispatch", "refs/heads/main", None, extended=True)
        self.assertEqual(len(result["targets"]), 3)
        self.assertIn(("corpus", "server client"), [(t["part"], t["sides"]) for t in result["tests"]])

    def test_a_call_runs_every_version_whatever_its_callers_event(self):
        for event, ref in (("push", "refs/heads/main"), ("push", "refs/tags/v1.0.0"), ("pull_request", "refs/pull/1/merge")):
            result = plan.plan(self.root, event, ref, ["docs/a.md"], called=True)
            self.assertEqual(len(result["targets"]), 3, event)
            self.assertFalse(result["save"] or result["fast"])

    def test_forge_mods_part_without_extended_is_the_plugin_hosts(self):
        root = tree("forge")
        self.assertEqual(sorted(plan.parts(root, "forge", "26.3", False)), ["client", "mods", "server"])
        self.assertEqual([r["run"] for r in plan.runs(root, "forge", "26.3", "mods", False)], ["client-plugins"])
        self.assertEqual(sorted(plan.parts(root, "forge", "26.3", True)), ["client", "corpus", "mods", "server"])

    def test_a_run_of_pinned_builds_the_version_pins_none_of_is_left_out(self):
        root = tree("neoforge")
        self.assertNotIn("client-plugins", [r["run"] for r in plan.runs(root, "neoforge", "1.21.11", "mods", False)])
        self.assertIn("client-plugins", [r["run"] for r in plan.runs(root, "neoforge", "1.21.1", "mods", False)])


class ManifestTest(unittest.TestCase):
    ROW = {"neoforge": "* !26.2", "forge": "26.1.2 26.3", "forge-legacy": "",
           "why_not": "neoforge 26.2: none there; forge: only on 26.x; when: provided"}

    def test_where_a_row_applies(self):
        self.assertTrue(plan.applies(self.ROW, "neoforge", "1.21.1"))
        self.assertFalse(plan.applies(self.ROW, "neoforge", "26.2"))
        self.assertTrue(plan.applies(self.ROW, "forge", "26.3"))
        self.assertFalse(plan.applies(self.ROW, "forge", "1.21.11"))
        self.assertFalse(plan.applies(self.ROW, "forge-legacy", "1.20.1"))

    def test_why_it_doesnt(self):
        self.assertEqual(plan.why_not(self.ROW, "neoforge", "26.2"), "none there")
        self.assertEqual(plan.why_not(self.ROW, "forge", "1.21.11"), "only on 26.x")
        self.assertEqual(plan.why_not(self.ROW, "when"), "provided")
        self.assertEqual(plan.why_not(self.ROW, "forge-legacy", "1.20.1"), "forge-legacy's test mods have no such check")

    def test_runs_fill_in_the_versions_pins(self):
        root = tree("neoforge")
        rows = {r["run"]: r for r in plan.runs(root, "neoforge", "26.1.2", "mods", extended=True)}
        self.assertIn("modrinth:neoforge:sodium@x,iris@y", rows["client-sodium"]["mods"])
        self.assertIn("modrinth:neoforge:sodium@x ", rows["client-sodium-addons"]["mods"])
        self.assertNotIn("client-ffapi-sodium", rows)
        self.assertEqual([r["run"] for r in plan.runs(root, "neoforge", "26.1.2", "server", False, ["server"])], ["server"])


if __name__ == "__main__":
    unittest.main()
