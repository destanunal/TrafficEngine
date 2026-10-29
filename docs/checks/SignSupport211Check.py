"""Audit authored sign hardware, diagonal parity and inventory slot extents."""
import json, math, itertools
from pathlib import Path
root=Path(__file__).resolve().parents[2]
assets=root/'common/src/main/resources/assets/trafficengine'
count=faces=0
for stem in ('road_direction_sign','highway_direction_sign','large_road_direction_sign'):
    for path in (assets/'models/block').glob(stem+'*.json'):
        model=json.loads(path.read_text())
        if not any('panel' in e.get('name','') for e in model['elements']):continue
        hardware=[e for e in model['elements'] if 'panel' not in e.get('name','')]
        assert model['textures']['metal']=='trafficengine:block/sign'
        assert model['ambientocclusion'] is False
        if stem=='road_direction_sign':
            spines=[e for e in hardware if e['name'].startswith('rear_')]
            assert {e['name'] for e in spines}=={'rear_lower_rail','rear_upper_rail','rear_left_spine','rear_right_spine'}
            mounts=[e for e in hardware if e['name'].endswith('_mount')]
            assert len(mounts)==1 and mounts[0]['name'] in ('center_mount','post_mount')
        if 'diagonal' in path.stem:
            straight=json.loads(path.with_name(path.name.replace('_diagonal','')).read_text())
            expected=[e for e in straight['elements'] if 'panel' not in e.get('name','')]
            unrotated=[{k:v for k,v in e.items() if k!='rotation'} for e in hardware]
            assert unrotated==expected, path.name
        for e in hardware:
            for f in e['faces'].values():
                faces+=1
                assert f['texture']=='#metal' and f['trafficengine_data']['match_straight_shade']
                assert all(0<=v<=16 for v in f['uv'])
                v=f['trafficengine_data']['vertices']
                p=[v[i:i+3] for i in range(0,12,3)]
                a=[p[1][i]-p[0][i] for i in range(3)];b=[p[2][i]-p[0][i] for i in range(3)]
                normal=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
                assert sum(t*t for t in normal)>1e-12, (path.name,e['name'])
                assert all(-32<=t<=64 for t in v)
        count+=1
    item=json.loads((assets/'models/item'/f'{stem}.json').read_text())
    assert item['parent']=='trafficengine:block/'+stem and 'elements' not in item
    geometry=json.loads((assets/'models/block'/f'{stem}.json').read_text())
    gui=item['display']['gui'];assert gui['rotation']==[30,225,0]
    points=[f.get('trafficengine_data',{}).get('vertices',[]) for e in geometry['elements'] for f in e['faces'].values()]
    # A frontal 180 degree turn only reverses X; GUI transforms are in model units.
    for data in points:
        for x,y,z in zip(data[::3],data[1::3],data[2::3]):
            dx,dy,dz=x-8,y-8,z-8
            c,s=math.cos(math.radians(225)),math.sin(math.radians(225))
            X=c*dx+s*dz;Z=-s*dx+c*dz;Y=math.cos(math.radians(30))*dy-.5*Z
            assert abs(X*gui['scale'][0]+gui['translation'][0])<=7.2
            assert abs(Y*gui['scale'][1]+gui['translation'][1])<=7.2
print(f'PASS: {count} sign variants; {faces} nondegenerate hardware faces; diagonal geometry/texture parity; one centered small connection and matching rear frame; all three icons fit their slots.')
