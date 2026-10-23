"""Check slim warning heads against the renderer positions and standard sign-post texture."""
import itertools
import json
import math
import sys
from pathlib import Path

root = Path(sys.argv[1])
assets = root / 'common/src/main/resources/assets/trafficengine'


def read(name):
    return json.loads((assets / name).read_text(encoding='utf-8'))


pole_texture = read('models/block/traffic_sign_post.json')['textures']['0']
states = read('blockstates/warning_light.json')['variants']
checks = 0
for facing, model, horizontal in itertools.product(('north', 'east', 'south', 'west'), ('single', 'double', 'tripple', 'quadruple'), (False, True)):
    key = f'facing={facing},model={model},horizontal={str(horizontal).lower()}'
    definition = read('models/' + states[key]['model'].split(':')[1] + '.json')
    body = [e for e in definition['elements'] if e['name'].startswith('slim_case_')]
    assert len(body) == 14, 'Chamfered shell uses bounded baked geometry'
    case = {'from': [min(e['from'][a] for e in body) for a in range(3)],
            'to': [max(e['to'][a] for e in body) for a in range(3)]}
    face = next(e for e in definition['elements'] if e['name'] == 'thin_face')
    pole = next(e for e in definition['elements'] if e['name'] == 'sign_post_stem')
    assert case['to'][2] - case['from'][2] == 2.75, 'Head must be thinner than the old 5-pixel case'
    assert pole['from'] == [7, 0, 7] and pole['to'] == [9, case['from'][1], 9], 'Use standard post width and touch the housing'
    assert definition['textures']['pole'] == pole_texture, 'Match traffic sign-post material'
    assert face['from'][0] - case['from'][0] == .25, 'Thin metal edge'
    assert sum(len(element['faces']) for element in definition['elements']) <= 22, 'Bounded baked shell and lens faces'
    assert definition['ambientocclusion'] is False, 'Preserve the ground-darkening correction'
    assert definition['render_type'] == 'cutout', 'Transparent lens corners use cutout rendering'
    assert definition['textures']['case'] == 'trafficengine:block/warning_housing', 'Matte modern housing'
    for texture in definition['textures'].values():
        namespace, path = texture.split(':')
        if namespace == 'trafficengine':
            assert (assets/'textures'/f'{path}.png').exists(), 'Model texture exists'
    for element in body:
        for data in element['faces'].values():
            coords = data['trafficengine_data']['vertices']
            assert len(coords) == 12 and all(math.isfinite(v) for v in coords)
            assert all(0 <= coords[i] <= 16 for i in range(12)), 'Static corners stay within the existing block bounds'
    count = 1 if model == 'single' else 2
    lenses = [e for e in definition['elements'] if e['name'].startswith('lens_')]
    assert len(lenses) == count, 'The off-state lenses are baked into the model'
    for slot in range(count):
        x = 3 + 6 * slot if count == 2 and horizontal else 6
        y = 3 + 6 * slot if count == 2 and not horizontal else 6
        assert case['from'][0] < x < x + 4 < case['to'][0]
        assert case['from'][1] < y < y + 4 < case['to'][1]
        assert lenses[slot]['from'] == [x, y, 6.42] and lenses[slot]['to'] == [x+4, y+4, 6.42]
        assert 16-(8.6+1.002) < lenses[slot]['from'][2] < face['from'][2], 'Animated glow is in front of the baked lens, without coplanar faces'
        assert 16 - (8.6 + 1.002) < face['from'][2] < case['from'][2], 'Keep lenses in front of the thin face'
    checks += 1
assert read('models/item/warning_light.json')['parent'].endswith('/single'), 'Inventory must show the new single default'
print(f'PASS: {checks} warning states; chamfered matte shells, baked lenses aligned to the animated glow, standard posts and ground shading.')
