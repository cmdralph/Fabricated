"""Tests that GitHub can read the repository's workflows and issue forms: python3 -B -m unittest discover -s ci -p 'test_*.py'.

GitHub drops an issue form it can't parse from the new-issue page without a word, and with blank issues disabled that
leaves no way to report a bug; a workflow it can't parse never runs.
"""
import unittest
from pathlib import Path

try:
    import yaml
except ImportError:  # the runner's and a contributor's Python have PyYAML; without it the test fails, saying so
    yaml = None

GITHUB = Path(__file__).resolve().parent.parent / ".github"
# The field types of GitHub's issue form schema.
FIELD_TYPES = {"markdown", "textarea", "input", "dropdown", "checkboxes"}


def load(path):
    with path.open(encoding="utf-8") as f:
        return yaml.safe_load(f)


class GitHubFilesTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(yaml, "PyYAML is needed to read the workflows and issue forms (pip install pyyaml)")

    def test_every_workflow_parses_and_has_jobs(self):
        workflows = sorted((GITHUB / "workflows").glob("*.y*ml"))
        self.assertTrue(workflows, "no workflow found")
        for path in workflows:
            with self.subTest(workflow=path.name):
                workflow = load(path)
                self.assertIsInstance(workflow, dict)
                # PyYAML reads the bare key `on` as True.
                self.assertTrue("on" in workflow or True in workflow, "no trigger")
                self.assertIsInstance(workflow.get("jobs"), dict)
                self.assertTrue(workflow["jobs"], "no job")

    def test_every_issue_form_parses_and_follows_the_schema(self):
        forms = sorted(p for p in (GITHUB / "ISSUE_TEMPLATE").glob("*.y*ml") if p.stem != "config")
        self.assertTrue(forms, "no issue form found")
        for path in forms:
            with self.subTest(form=path.name):
                form = load(path)
                self.assertIsInstance(form, dict)
                for key in ("name", "description"):
                    self.assertIsInstance(form.get(key), str, key)
                    self.assertTrue(form[key].strip(), key)
                body = form.get("body")
                self.assertIsInstance(body, list)
                self.assertTrue(any(field.get("type") != "markdown" for field in body), "no field to fill in")
                ids = set()
                for field in body:
                    self.assertIn(field.get("type"), FIELD_TYPES, field)
                    attributes = field.get("attributes")
                    self.assertIsInstance(attributes, dict, field)
                    if field["type"] == "markdown":
                        self.assertIsInstance(attributes.get("value"), str, field)
                        continue
                    self.assertIsInstance(attributes.get("label"), str, field)
                    if "id" in field:
                        self.assertNotIn(field["id"], ids, "two fields have the id " + str(field["id"]))
                        ids.add(field["id"])
                    if field["type"] in ("dropdown", "checkboxes"):
                        self.assertIsInstance(attributes.get("options"), list, field)
                        self.assertTrue(attributes["options"], field)

    def test_the_issue_chooser_config_parses(self):
        config = load(GITHUB / "ISSUE_TEMPLATE" / "config.yml")
        self.assertIsInstance(config.get("blank_issues_enabled"), bool)
        for link in config.get("contact_links", []):
            for key in ("name", "url", "about"):
                self.assertIsInstance(link.get(key), str, key)


if __name__ == "__main__":
    unittest.main()
