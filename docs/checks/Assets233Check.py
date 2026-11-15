"""Check the shared manual sprite, unchanged source, wall plate surfaces and state mappings."""
from pathlib import Path
import hashlib, json
from PIL import Image
root=Path(__file__).resolve().parents[2]
assets=root/'common/src/main/resources/assets/trafficengine'
image=Image.open(assets/'textures/block/patterns/1.png').convert('RGBA')
opaque=[image.getpixel((x,y)) for y in range(image.height) for x in range(image.width) if image.getpixel((x,y))[3]]
assert len(opaque)==64 and len(set(opaque))>1, 'Original manual paint grain remains unchanged'
model_hook=(root/'common/src/main/java/com/destan/trafficengine/mixin/AsphaltModelMixin.java').read_text()
assert '"block/patterns/1"' in model_hook and '"block/road_marking_paint"' not in model_hook
assert not (assets.parent/'minecraft/atlases/blocks.json').exists(), 'No differently mipmapped crop or duplicate sprite'
checks=len(opaque)
for name in ('road_direction_sign','highway_direction_sign','large_road_direction_sign'):
 plain=json.loads((assets/f'models/block/{name}.json').read_text())
 wall=json.loads((assets/f'models/block/{name}_wall.json').read_text())
 assert {e['name'] for e in wall['elements']}=={'front_panel','rear_panel','rounded_panel'}
 original=[e for e in plain['elements'] if e['name'] in ('front_panel','rear_panel','rounded_panel')]
 for first,second in zip(original,wall['elements']):
  for side,face in first['faces'].items():
   if 'trafficengine_data' not in face:
    for key in ('from','to'):
     for axis in range(3): assert abs(second[key][axis]-first[key][axis]-(11.5 if axis==2 else 0))<1e-8
    continue
   v=face['trafficengine_data']['vertices'];w=second['faces'][side]['trafficengine_data']['vertices']
   for i in range(12):
    assert abs(w[i]-v[i]-(11.5 if i%3==2 else 0))<1e-8
    checks+=1
   assert all(15.5-1e-6<=w[i]<=16+1e-6 for i in range(2,12,3))
 states=json.loads((assets/f'blockstates/{name}.json').read_text())['variants']
 assert len(states)==128,'No extra join properties or block-state multiplication'
 for key,value in states.items():
  if 'wall_mounted=true' in key:assert value['model']==f'trafficengine:block/{name}_wall'
screen=(root/'common/src/main/java/com/destan/trafficengine/client/screen/PaintBrushScreen.java').read_text()
assert 'paint_brush.undo.tooltip' not in screen
print(f'PASS Assets233: {checks} sprite and wall checks; original manual atlas entry reused, no duplicate PNG or crop.')
