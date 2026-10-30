import unittest
from probe_bilibili_boundaries import identify


class VideoIdentityTests(unittest.TestCase):
    def test_bv_and_av_are_stable_identities(self):
        self.assertEqual(identify(" BV14u41147YH "), {"bvid": "BV14u41147YH", "part": 1})
        self.assertEqual(identify("AV528990022"), {"aid": 528990022, "part": 1})

    def test_url_keeps_selected_part_and_drops_tracking(self):
        self.assertEqual(identify("https://www.bilibili.com/video/BV14u41147YH/?p=2&spm_id_from=test"), {"bvid": "BV14u41147YH", "part": 2})

    def test_invalid_or_foreign_inputs_are_rejected(self):
        for value in ("av0", "BVshort", "file:///video/BV14u41147YH", "https://www.bilibili.com.evil.test/video/BV14u41147YH", "https://u@www.bilibili.com/video/BV14u41147YH", "https://www.bilibili.com/video/BV14u41147YH?p=-1", "https://www.bilibili.com/video/BV14u41147YH?p=", "https://www.bilibili.com/video/BV14u41147YH?p=1&p=2"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                identify(value)


if __name__ == "__main__":
    unittest.main()
