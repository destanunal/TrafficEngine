import unittest
from changelog_format import tight_changelog


class ChangelogFormatChecks(unittest.TestCase):
    def test_flat_lists_become_tight(self):
        for marker in ("-", "*", "+", "1.", "1)"):
            with self.subTest(marker=marker):
                source = f"## 🪧 Signs\n\n{marker} First.\n\n\n{marker} Second.\n\n## Next\n"
                expected = f"## 🪧 Signs\n\n{marker} First.\n{marker} Second.\n\n## Next\n"
                self.assertEqual(tight_changelog(source), expected)

    def test_paragraph_and_heading_separation_remain(self):
        source = "# Update\n\nIntro.\n\n- First\n\nExtra paragraph.\n\n- Second\n"
        self.assertEqual(tight_changelog(source), source)

    def test_fenced_code_is_unchanged(self):
        for delimiter in ("```", "~~~"):
            source = f"{delimiter}markdown\n- Code\n\n- Still code\n{delimiter}\n"
            self.assertEqual(tight_changelog(source), source)

    def test_nested_blocks_are_not_collapsed_into_the_parent(self):
        source = "- Parent\n\n  - Child\n\n- Next parent\n"
        self.assertEqual(tight_changelog(source), source)

    def test_idempotent_and_windows_lines(self):
        source = "# Update\r\n\r\n- First\r\n\r\n- Second\r\n"
        fixed = tight_changelog(source)
        self.assertEqual(tight_changelog(fixed), fixed)
        self.assertNotIn("\r", fixed)
        self.assertEqual([line for line in source.splitlines() if line], [line for line in fixed.splitlines() if line])


if __name__ == "__main__":
    unittest.main()
