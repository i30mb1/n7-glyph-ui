"""The Phone (3) Glyph Matrix as the design system draws it. Mirrors `GlyphMatrix` in ds/components/bundle.js."""

SIZE = 25
MAX = 4095

# Visible x-range per row (measured on the device). The same table as BOUNDS in bundle.js and GlyphMatrixSpec in the library.
BOUNDS = ((9, 15), (7, 17), (5, 19), (4, 20), (3, 21), (2, 22), (2, 22), (1, 23), (1, 23),
          (0, 24), (0, 24), (0, 24), (0, 24), (0, 24), (0, 24), (0, 24),
          (1, 23), (1, 23), (2, 22), (2, 22), (3, 21), (4, 20), (5, 19), (7, 17), (9, 15))

# Geometry of GlyphMatrix in cell units: LED radius, lens disc radius, rim stroke width. The matrix centre is (12.5, 12.5).
LED_R = 0.38
LENS_R = 13.85
RIM = 0.22


def visible(x, y):
    return 0 <= y < SIZE and BOUNDS[y][0] <= x <= BOUNDS[y][1]


def visible_cells():
    """Every LED of the round matrix, row by row."""
    return [(x, y) for y in range(SIZE) for x in range(BOUNDS[y][0], BOUNDS[y][1] + 1)]


def level(value):
    """Frame brightness 0..4095 to a cell level 0..1. The curve of `MatrixCanvas.cells` in the library:
    perceptual, (v / MAX) ^ 0.45, and at least 0.12 for any lit LED."""
    if value <= 0:
        return 0.0
    return max(0.12, (min(value, MAX) / MAX) ** 0.45)


def cells_from_frame(values):
    """625 brightness values, row by row, to the lit visible cells {(x, y): level}."""
    cells = {}
    for x, y in visible_cells():
        v = values[y * SIZE + x]
        if v > 0:
            cells[(x, y)] = level(v)
    return cells


def cell_list(cells):
    """{(x, y): level} to the JSON form the web components take: [[x, y, level], ...], row by row."""
    return [[x, y, round(lv, 3)] for (x, y), lv in sorted(cells.items(), key=lambda item: (item[0][1], item[0][0]))]
