"""Check highway support geometry and state coverage without launching Minecraft."""
import itertools
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
assets = root / 'common/src/main/resources/assets/trafficengine'


def read(name):
    return json.loads((assets / name).read_text(encoding='utf-8'))


def metal(model):
    return [e for e in model['elements'] if any(f['texture'] == '#metal' for f in e['faces'].values())]


axes = {'east': (0, 1), 'west': (0, 0), 'up': (1, 1),
        'down': (1, 0), 'south': (2, 1), 'north': (2, 0)}
states = read('blockstates/highway_direction_sign.json')['variants']
tested = 0
for facing, mounted, upper, wall in itertools.product(('north', 'east', 'south', 'west'), (False, True), (False, True), (False, True)):
    props = dict(facing=facing, post_mounted=str(mounted).lower(),
                 upper_connection=str(upper).lower(), wall_mounted=str(wall).lower())
    matches = [v for k, v in states.items() if all(props[key] == value for key, value in (p.split('=') for p in k.split(',')))]
    assert len(matches) == 1, (props, 'Ambiguous or missing model')
    model = read('models/' + matches[0]['model'].split(':')[1] + '.json')
    pieces = metal(model)
    for a, b in itertools.combinations(pieces, 2):
        for face in a['faces'].keys() & b['faces'].keys():
            axis, endpoint = axes[face]
            point_a = a['to' if endpoint else 'from'][axis]
            point_b = b['to' if endpoint else 'from'][axis]
            if abs(point_a - point_b) > 1e-6:
                continue
            others = [d for d in range(3) if d != axis]
            overlap = all(min(a['to'][d], b['to'][d]) - max(a['from'][d], b['from'][d]) > 1e-6 for d in others)
            assert not overlap, (props, a['name'], b['name'], face, 'Coplanar support faces cause flicker')
    if upper and not mounted:
        post = {'from': [7, 16, 7], 'to': [9, 32, 9]}
        for piece in pieces:
            overlap = all(min(piece['to'][d], post['to'][d]) - max(piece['from'][d], post['from'][d]) > 1e-6 for d in range(3))
            assert not overlap, (props, piece['name'], 'Upper pole drawn twice')
    tested += 1

base = metal(read('models/block/highway_direction_sign.json'))
connected = metal(read('models/block/highway_direction_sign_connected.json'))
post = {'from': [7, 16, 7], 'to': [9, 32, 9]}
original = [
    {'from': [7, 3, 4.625], 'to': [9, 5, 9]},
    {'from': [7, 19, 4.625], 'to': [9, 21, 9]},
    {'from': [7, 0, 7], 'to': [9, 24, 9]},
]


def contains(pieces, point):
    return any(all(p['from'][d] < point[d] < p['to'][d] for d in range(3)) for p in pieces)


# Sampling each interval between all box boundaries verifies the entire solid union.
boundaries = [sorted({p[side][d] for p in original + base + connected + [post] for side in ('from', 'to')}) for d in range(3)]
midpoints = [[(a + b) / 2 for a, b in zip(bounds, bounds[1:])] for bounds in boundaries]
for point in itertools.product(*midpoints):
    assert contains(original, point) == contains(base, point), ('Freestanding support changed shape', point)
    assert contains(original + [post], point) == contains(connected + [post], point), ('Connected support has a gap', point)
print(f'PASS: {tested} highway states; no coplanar support faces, no duplicated upper pole, unchanged solid support.')
