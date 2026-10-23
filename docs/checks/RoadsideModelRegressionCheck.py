"""Resolve every multipart state, verify connectivity, face winding and resource integration."""
import itertools
import json
import math
import re
import sys
import zipfile
from pathlib import Path

root = Path(sys.argv[1])
resources = root / 'common/src/main/resources'
assets = resources / 'assets/trafficengine'
vanilla = zipfile.ZipFile(Path.home() / '.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def matches(condition, state):
    if 'OR' in condition:
        return any(matches(c, state) for c in condition['OR'])
    return all(state[key] in str(value).split('|') for key, value in condition.items())


def pieces(name, state):
    return [part['apply'] for part in read(assets / f'blockstates/{name}.json')['multipart'] if 'when' not in part or matches(part['when'], state)]


def check_fence_surfaces(selected):
    elements = []
    for piece in selected:
        for original in read(assets / ('models/' + piece['model'].split(':')[1] + '.json'))['elements']:
            start, end = original['from'][:], original['to'][:]
            directions = list(original['faces'])
            for _ in range(piece.get('y', 0) // 90):
                start, end = [16-end[2], start[1], start[0]], [16-start[2], end[1], end[0]]
                turn = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north', 'up': 'up', 'down': 'down'}
                directions = [turn[d] for d in directions]
            elements.append((start, end, directions))
    axes = {'north': (2, 0), 'south': (2, 1), 'east': (0, 1), 'west': (0, 0), 'up': (1, 1), 'down': (1, 0)}
    for a, b in itertools.combinations(elements, 2):
        for direction in set(a[2]) & set(b[2]):
            axis, endpoint = axes[direction]
            if abs(a[endpoint][axis]-b[endpoint][axis]) > 1e-6:
                continue
            others = [i for i in range(3) if i != axis]
            assert not all(min(a[1][i], b[1][i])-max(a[0][i], b[0][i]) > 1e-6 for i in others), ('Overlapping fence faces can flicker', direction, a, b)


fence_checks = 0
for mask, east_west, water in itertools.product(range(16), (False, True), (False, True)):
    state = {side: str(bool(mask & 1 << i)).lower() for i, side in enumerate(('north', 'east', 'south', 'west'))}
    state.update(east_west=str(east_west).lower(), waterlogged=str(water).lower())
    selected = pieces('temporary_pedestrian_fence', state)
    effective = mask or (10 if east_west else 5)
    rotations = [piece.get('y', 0) for piece in selected if piece['model'].endswith('/arm')]
    assert rotations == [i*90 for i in range(4) if effective & 1 << i], ('Wrong fence arm', state, selected)
    assert sum(p['model'].endswith('/end') for p in selected) == (2 if mask == 0 else 0), 'Isolated sections need end posts'
    assert sum(p['model'].endswith('/post') for p in selected) == 1, 'Use one shared corner/end post'
    check_fence_surfaces(selected)
    fence_checks += 1
panel_checks = 0
for facing, right, red, left_join, right_join, water, single in itertools.product(('north', 'east', 'south', 'west'), *[(False, True)]*6):
    state = dict(facing=facing, arrow_right=str(right).lower(), red_white=str(red).lower(),
                 left_connection=str(left_join).lower(), right_connection=str(right_join).lower(), waterlogged=str(water).lower(), single=str(single).lower())
    selected = pieces('chevron_panel', state)
    assert len(selected) == (3 if single else 1 + (not left_join) + (not right_join)), 'Square signs have two caps; wide boards only outside caps'
    assert all(('/single_' in p['model']) == single for p in selected), 'Select the matching base and cap sizes'
    assert sum(p['model'].rsplit('/', 1)[1].removeprefix('single_').startswith(('black_yellow_', 'red_white_')) for p in selected) == 1
    panel_checks += 1

max_faces = 0
for path in itertools.chain((assets/'models/block/chevron_panel').glob('*.json'), (assets/'models/block/temporary_pedestrian_fence').glob('*.json'),
                            [assets/'models/item/chevron_panel.json', assets/'models/item/temporary_pedestrian_fence.json']):
    model = read(path)
    for texture in model.get('textures', {}).values():
        domain, name = texture.split(':')
        assert ((assets/'textures'/f'{name}.png').exists() if domain == 'trafficengine' else f'assets/minecraft/textures/{name}.png' in vanilla.namelist()), ('Missing texture', texture)
    max_faces = max(max_faces, sum(len(e['faces']) for e in model.get('elements', [])))
    for element in model.get('elements', []):
        assert all(-16 <= v <= 32 and math.isfinite(v) for key in ('from', 'to') for v in element[key]), 'Bounds accepted by vanilla'
        for direction, face in element['faces'].items():
            assert face['texture'][1:] in model['textures']
            corners = face.get('trafficengine_data', {}).get('vertices')
            if corners:
                points = [corners[i:i+3] for i in range(0, 12, 3)]
                ax, ay = points[1][0]-points[0][0], points[1][1]-points[0][1]
                bx, by = points[2][0]-points[0][0], points[2][1]-points[0][1]
                assert ax*by-ay*bx < 0, 'North chevrons must face the viewer'
                assert all(0 < p[0] < 16 and 6.25 < p[1] < 13.75 and p[2] < 6.94 for p in points), 'Arrow must stay inside and in front of the plate'
            if 'chevron_panel' in str(path):
                reflective = element['name'] == 'reflective_face' or element['name'].startswith('chevron_')
                assert face.get('trafficengine_data', {}).get('emissive', False) == reflective, 'Only the existing reflective front faces glow'
screen = (root / 'common/src/main/java/com/destan/trafficengine/client/screen/ChevronPanelScreen.java').read_text(encoding='utf-8')
for single, right in itertools.product((False, True), repeat=2):
    model = read(assets / f'models/block/chevron_panel/{"single_" if single else ""}black_yellow_{"right" if right else "left"}.json')
    board = next(e for e in model['elements'] if e['name'] == 'steel_panel')
    board_top, board_bottom = board['to'][1], board['from'][1]
    board_middle = (board_top+board_bottom)/2
    upper = model['elements'][3]['faces']['north']['trafficengine_data']['vertices']
    points = [upper[i:i+3] for i in range(0, 12, 3)]
    top = [16-p[0] for p in points if p[1] > board_top-1]
    middle = [16-p[0] for p in points if p[1] == board_middle]
    assert (sum(middle)/2 > sum(top)/2) == right, 'Menu direction and physical arrow must agree'
    support = next(e for e in model['elements'] if e['name'] == 'rear_support')
    assert board['to'][0]-board['from'][0] == (8 if single else 16), 'Square or 2:1 board'
    assert board_top-board_bottom == 8 and board_top == 14, 'Shorter post keeps board dimensions'
    assert support['to'][1] == board_middle and support['from'][2] == board['to'][2], 'Support touches the back up to the board centre'
    assert 'north' not in support['faces'] and 'down' not in support['faces'], 'No coplanar support faces'
    label = 'SINGLE_QUADS' if single else 'WIDE_QUADS'
    coords = [float(v.removesuffix('F')) for v in re.search(label + r' = \{([^}]+)\}', screen).group(1).split(', ')]
    offset = 12 if single else 16
    polygons = [e['faces']['north']['trafficengine_data']['vertices'] for e in model['elements'] if e['name'].startswith('chevron_')]
    for i, polygon in enumerate(polygons):
        expected = {(round(offset-polygon[j], 4), round(board_top-polygon[j+1], 4)) for j in range(0, 12, 3)}
        if not right:
            expected = {(round((8 if single else 16)-u, 4), v) for u, v in expected}
        actual = {(round(coords[j], 4), round(coords[j+1], 4)) for j in range(i*8, i*8+8, 2)}
        assert expected == actual, 'Preview and physical sign must have the same polygon proportions'
item = read(assets / 'models/item/chevron_panel.json')
default_model = read(assets / 'models/block/chevron_panel/red_white_right.json')
assert [e for e in item['elements'] if not e['name'].endswith('_cap')] == default_model['elements'], 'Inventory has the same right-facing red/white default'
for piece in ('post', 'arm', 'end'):
    model = read(assets / f'models/block/temporary_pedestrian_fence/{piece}.json')
    assert model['textures']['mesh'] == 'trafficengine:block/pedestrian_fence_mesh', 'Use custom welded mesh, not vanilla bars'
    assert not any(e['name'] == 'foot' for e in model['elements']), 'Remove black feet'
    assert max(e['to'][1] for e in model['elements']) == (31 if piece == 'end' else 32), 'Taller barrier and end posts'
assert 'w = single ? 88 : 176, h = 88' in screen, 'Preview keeps a square or 2:1 aspect ratio'
for block in ('ChevronPanelBlock', 'TemporaryPedestrianFenceBlock', 'TrafficIslandBollardBlock', 'AsphaltSpeedBumpBlock', 'BikeLaneSeparatorBlock'):
    source = (root / f'common/src/main/java/com/destan/trafficengine/block/{block}.java').read_text(encoding='utf-8')
    assert 'appendHoverText(' not in source, 'These five blocks use name-only inventory tooltips'
names = {'tr_tr': ('Viraj Yön Levhası', 'Yaya Güvenlik Bariyeri'),
         'en_us': ('Curve Direction Sign', 'Pedestrian Safety Barrier'),
         'de_de': ('Kurvenleittafel', 'Fußgänger-Absperrgitter')}
for lang, (sign, barrier) in names.items():
    language = read(assets / f'lang/{lang}.json')
    assert language['block.trafficengine.chevron_panel'] == language['gui.trafficengine.chevron.title'] == sign
    assert language['block.trafficengine.temporary_pedestrian_fence'] == barrier
    assert all('gui.trafficengine.chevron.'+key in language for key in ('model', 'single', 'multiple', 'direction', 'colors', 'left', 'right', 'black_yellow', 'red_white'))
for name in ('chevron_panel', 'temporary_pedestrian_fence'):
    recipe = read(resources / f'data/trafficengine/recipes/{name}.json')
    assert recipe['result']['item'] == 'trafficengine:' + name
    loot = read(resources / f'data/trafficengine/loot_tables/blocks/{name}.json')
    assert loot['pools'][0]['entries'][0]['name'] == 'trafficengine:' + name
    for language in ('en_us', 'tr_tr'):
        assert f'block.trafficengine.{name}' in read(assets / f'lang/{language}.json')
    assert 'trafficengine:' + name in read(resources / 'data/minecraft/tags/blocks/mineable/pickaxe.json')['values']
print(f'PASS: {fence_checks} fence and {panel_checks} chevron multipart states; arrow winding/direction, texture/model references, recipes, drops and translations.')
