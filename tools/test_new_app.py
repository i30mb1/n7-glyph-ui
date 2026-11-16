"""Checks of new_app.py's argument checks. Run: python -m unittest discover -s tools -p "test_*.py" """
import contextlib
import io
import unittest
from argparse import Namespace

import new_app


def accepted(name):
    args = Namespace(package="n7.glyphcoin", repo="glyph-coin", name=name)
    try:
        with contextlib.redirect_stderr(io.StringIO()):
            new_app.check_args(args)
    except SystemExit:
        return False
    return True


class CheckNameTest(unittest.TestCase):
    def test_plain_names_pass(self):
        for name in ["Glyph Coin", "Glyph Flame", "Glyph 8-Ball", "Glyph Coin!", "Glyph (3) Coin", "Glyph Coin+"]:
            self.assertTrue(accepted(name), name)

    def test_names_that_break_the_build_are_refused(self):
        # $ starts a Kotlin template; / : ? * | are refused by Gradle as rootProject.name; the rest break XML or JSON.
        for name in ["Glyph $Coin", "Glyph On/Off", "Glyph: Coin", "Glyph Coin?", "Glyph*Coin", "Glyph|Coin",
                     "Glyph <Coin>", "Glyph & Coin", 'Glyph "Coin"', "Glyph's Coin", "Glyph\\Coin", "Glyph\tCoin"]:
            self.assertFalse(accepted(name), name)

    def test_the_first_character_must_be_a_letter(self):
        # A leading @ or ? makes the string resource a reference.
        for name in ["@Glyph Coin", "1 Glyph", " ", "", "-Glyph"]:
            self.assertFalse(accepted(name), name)


if __name__ == "__main__":
    unittest.main()
