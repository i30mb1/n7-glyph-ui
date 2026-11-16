"""Which characters a TrueType font has. Used to refuse a headline the dot type cannot set: the browser would quietly
fall back to another font for the missing letters, and a store picture must never show that."""
import struct
from functools import lru_cache
from pathlib import Path


@lru_cache(maxsize=None)
def codepoints(path):
    """The set of Unicode code points mapped to a glyph by the font's cmap table (formats 4 and 12)."""
    data = Path(path).read_bytes()
    tables = {}
    for i in range(struct.unpack_from(">H", data, 4)[0]):
        tag, _checksum, offset, _length = struct.unpack_from(">4sIII", data, 12 + 16 * i)
        tables[tag] = offset
    if b"cmap" not in tables:
        raise ValueError(f"{path}: no cmap table")
    cmap = tables[b"cmap"]
    found = set()
    for i in range(struct.unpack_from(">H", data, cmap + 2)[0]):
        platform, encoding, offset = struct.unpack_from(">HHI", data, cmap + 4 + 8 * i)
        if not (platform == 0 or (platform == 3 and encoding in (1, 10))):
            continue
        sub = cmap + offset
        fmt = struct.unpack_from(">H", data, sub)[0]
        if fmt == 4:
            found |= _format4(data, sub)
        elif fmt == 12:
            found |= _format12(data, sub)
    return frozenset(found)


def _format4(data, sub):
    seg = struct.unpack_from(">H", data, sub + 6)[0] // 2
    ends = struct.unpack_from(f">{seg}H", data, sub + 14)
    starts = struct.unpack_from(f">{seg}H", data, sub + 16 + 2 * seg)
    deltas = struct.unpack_from(f">{seg}H", data, sub + 16 + 4 * seg)
    range_pos = sub + 16 + 6 * seg
    ranges = struct.unpack_from(f">{seg}H", data, range_pos)
    found = set()
    for i in range(seg):
        for code in range(starts[i], ends[i] + 1):
            if code == 0xFFFF:
                continue
            if ranges[i] == 0:
                glyph = (code + deltas[i]) & 0xFFFF
            else:
                glyph = struct.unpack_from(">H", data, range_pos + 2 * i + ranges[i] + 2 * (code - starts[i]))[0]
                if glyph:
                    glyph = (glyph + deltas[i]) & 0xFFFF
            if glyph:
                found.add(code)
    return found


def _format12(data, sub):
    found = set()
    for i in range(struct.unpack_from(">I", data, sub + 12)[0]):
        start, end, _glyph = struct.unpack_from(">III", data, sub + 16 + 12 * i)
        found.update(range(start, end + 1))
    return found


def missing(text, path):
    """Characters of `text` the font has no glyph for, in order of appearance, each once."""
    have = codepoints(str(path))
    out = []
    for ch in text:
        if ch not in out and ch not in "\n\r\t" and ord(ch) not in have:
            out.append(ch)
    return out
