"""Check assembled button carriers at every height, facing and support mode."""
from pathlib import Path
from itertools import product
from functools import cache
import json
assets = Path(__file__).resolve().parents[2] / 'common/src/main/resources/assets/trafficengine'
@cache
def model(name):
    data = json.loads((assets / 'models' / (name.removeprefix('trafficengine:') + '.json')).read_text())
    if 'parent' not in data:
        return data
    parent = model(data['parent'])
    return parent | data | {'textures': parent.get('textures', {}) | data.get('textures', {})}
def matches(when, state):
    return all(state[key] in str(value).lower().split('|') for key, value in when.items())
parts = json.loads((assets / 'blockstates/traffic_light_request_button.json').read_text())['multipart']
checks = 0
for sink, lower, facing, powered, active, water in product(range(9), ('false','true'), ('north','south','east','west'), ('false','true'), ('false','true'), ('false','true')):
    state = dict(sink=str(sink), lower_pole=lower, facing=facing, powered=powered, activated=active, waterlogged=water)
    models = [model(part['apply']['model']) for part in parts if matches(part.get('when', {}), state)]
    assert len(models) == (2 if sink and lower == 'false' else 1), state
    stems = [e for data in models for e in data['elements'] if e.get('name') in ('standard_post','upper_post_connection','lower_post_connection')]
    main = next(e for e in stems if e['name'] == 'standard_post')
    assert main['from'] == [7,0,7] and main['to'] == [9,16,9], state
    assert main['faces']['up']['texture'] == '#metal', state
    assert len(stems) == (2 if sink and lower == 'false' else 1), state
    for data in models:
        for element in data['elements']:
            if element.get('name') not in ('standard_post','lower_post_connection'):
                continue
            for face in element['faces'].values():
                # Minecraft face textures resolve named slots, not literal asset paths.
                assert face['texture'].startswith('#'), (state, face)
                texture = face['texture']
                while texture.startswith('#'):
                    texture = data['textures'][texture[1:]]
                assert texture == 'trafficengine:block/sign', state
    if sink and lower == 'false':
        extension = next(e for e in stems if e['name'] == 'lower_post_connection')
        assert extension['from'] == [7,-2*sink,7] and extension['to'] == [9,0,9], state
        assert 'up' not in extension['faces'] and 'down' not in main['faces'], state
        assert all(f['uv'] == [0,0,2,2*sink] for f in extension['faces'].values()), state
    if lower == 'true':
        # A lower sign pole/footing already owns y=-16..0 at the same x/z footprint.
        assert all(e['from'][1] >= 0 for e in stems), state
    if sink:
        housing = next(e for e in models[0]['elements'] if e.get('name') == 'top_endcap')
        assert housing['from'][1] == 14-2*sink, state
    checks += 1
print(f'PASS {checks} complete button assemblies: no duplicate lower stem, continuous upper pole, valid metal cap and unchanged lowered housing')
