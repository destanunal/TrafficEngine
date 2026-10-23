import io
import json
import unittest
import zipfile
import release


class ReleaseChecks(unittest.TestCase):
    def resolve(self, minecraft):
        return "a" * 40, {"mod_version": "1.8.2", "minecraft_version": minecraft}

    def make_plan(self, **overrides):
        args = dict(version="1.8.2", mode="build", targets="both", channel="release",
                    notes="# **Traffic Engine Update 1.8.2**\n\nRelease notes.",
                    tokens={}, resolve=self.resolve)
        args.update(overrides)
        return release.plan(**args)

    def archive(self, minecraft, loader, *, version="1.8.2", major=None, core=True):
        # ZIP fixtures stay in memory. No trial or release JARs are generated.
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as archive:
            if loader == "fabric":
                metadata = {"id": "trafficengine", "version": version + "+" + minecraft,
                            "depends": {"minecraft": "~" + minecraft}}
                archive.writestr(release.METADATA[loader], json.dumps(metadata))
            else:
                archive.writestr(release.METADATA[loader],
                                 '[[mods]]\nmodId="trafficengine"\nversion="' + version + "+" + minecraft
                                 + '"\n[[dependencies.trafficengine]]\nmodId="minecraft"\nversionRange="['
                                 + minecraft + ',2.0)"\n')
            if core:
                if major is None:
                    major = 61 if minecraft == "1.20.1" else 65
                archive.writestr("com/destan/trafficengine/TrafficEngine.class",
                                 b"\xca\xfe\xba\xbe\x00\x00" + major.to_bytes(2, "big"))
        stream.seek(0)
        return stream

    def test_four_builds_and_eight_uploads(self):
        plan = self.make_plan()
        self.assertEqual(len(plan["builds"]), 2)
        self.assertEqual(len(plan["uploads"]), 8)
        self.assertEqual({(i["minecraft"], i["loader"]) for i in plan["uploads"]},
                         {("1.20.1", "fabric"), ("1.20.1", "forge"),
                          ("1.21.1", "fabric"), ("1.21.1", "neoforge")})
        self.assertTrue(all(item["sha"] == "a" * 40 for item in plan["uploads"]))

    def test_upload_order_for_each_destination(self):
        expected = [("1.20.1", "fabric"), ("1.20.1", "forge"),
                    ("1.21.1", "fabric"), ("1.21.1", "neoforge")]
        for target in ("curseforge", "modrinth", "both"):
            uploads = self.make_plan(targets=target)["uploads"]
            for site in {item["destination"] for item in uploads}:
                with self.subTest(target=target, site=site):
                    self.assertEqual([(item["minecraft"], item["loader"]) for item in uploads
                                      if item["destination"] == site], expected)

    def test_two_component_release_version(self):
        plan = self.make_plan(version="1.9", targets="curseforge",
            notes="# **🆕 Traffic Engine Update 1.9**\n\nRelease notes.",
            resolve=lambda mc: ("a" * 40, {"mod_version": "1.9", "minecraft_version": mc}))
        self.assertEqual([(i["minecraft"], i["loader"]) for i in plan["uploads"]],
            [("1.20.1", "fabric"), ("1.20.1", "forge"), ("1.21.1", "fabric"), ("1.21.1", "neoforge")])
        for minecraft, loaders in release.PLATFORMS.items():
            for loader in loaders:
                self.assertEqual(release.filename("1.9", minecraft, loader),
                    f"trafficengine-{loader}-1.9+{minecraft}.jar")
                release.inspect_archive(self.archive(minecraft, loader, version="1.9"), "1.9", minecraft, loader)
        for bad in ("1", "1.9.1.2", "1.9+1.20.1", "../1.9"):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                release.filename(bad, "1.20.1", "fabric")

    def test_build_needs_no_site_tokens(self):
        for target in ("curseforge", "modrinth", "both"):
            self.make_plan(targets=target)

    def test_only_selected_site_needs_a_token(self):
        plan = self.make_plan(mode="publish", targets="curseforge", tokens={"curseforge": "test-only"})
        self.assertEqual(len(plan["uploads"]), 4)
        self.assertTrue(all(item["destination"] == "curseforge" for item in plan["uploads"]))
        self.make_plan(mode="publish", targets="modrinth", tokens={"modrinth": "test-only"})
        with self.assertRaisesRegex(ValueError, "MODRINTH_TOKEN"):
            self.make_plan(mode="publish", tokens={"curseforge": "test-only"})
        with self.assertRaisesRegex(ValueError, "CURSEFORGE_TOKEN"):
            self.make_plan(mode="publish", targets="curseforge")

    def test_rejects_stale_notes_and_invalid_inputs(self):
        for changes in ({"version": "../1.8.2"}, {"version": "1.8.2;echo x"},
                        {"mode": "other"}, {"targets": "other"}, {"channel": "other"},
                        {"notes": ""}, {"notes": "# Update 1.8.20"}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                self.make_plan(**changes)

    def test_rejects_wrong_source_version(self):
        with self.assertRaisesRegex(ValueError, "not version"):
            self.make_plan(resolve=lambda mc: ("a" * 40, {"mod_version": "1.8.1", "minecraft_version": mc}))
        with self.assertRaisesRegex(ValueError, "Minecraft version mismatch"):
            self.make_plan(resolve=lambda mc: ("a" * 40, {"mod_version": "1.8.2", "minecraft_version": "1.19.2"}))

    def test_all_distribution_metadata(self):
        for minecraft, loaders in release.PLATFORMS.items():
            for loader in loaders:
                with self.subTest(minecraft=minecraft, loader=loader):
                    release.inspect_archive(self.archive(minecraft, loader), "1.8.2", minecraft, loader)
                    with self.assertRaisesRegex(ValueError, "version"):
                        release.inspect_archive(self.archive(minecraft, loader, version="1.8.1"),
                                                "1.8.2", minecraft, loader)
                    with self.assertRaisesRegex(ValueError, "classes are missing"):
                        release.inspect_archive(self.archive(minecraft, loader, core=False),
                                                "1.8.2", minecraft, loader)

    def test_rejects_wrong_loader_or_java(self):
        with self.assertRaisesRegex(ValueError, "metadata"):
            release.inspect_archive(self.archive("1.20.1", "fabric"), "1.8.2", "1.20.1", "forge")
        with self.assertRaisesRegex(ValueError, "Java bytecode"):
            release.inspect_archive(self.archive("1.20.1", "forge", major=65), "1.8.2", "1.20.1", "forge")

    def test_only_final_loader_filenames_are_selected(self):
        self.assertEqual(release.filename("1.8.2", "1.20.1", "forge"), "trafficengine-forge-1.8.2+1.20.1.jar")
        with self.assertRaises(ValueError):
            release.filename("1.8.2", "1.21.1", "forge")
        with self.assertRaises(ValueError):
            release.filename("1.8.2", "1.20.1", "common")


if __name__ == "__main__":
    unittest.main()
