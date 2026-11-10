from pathlib import Path
import json,math
for version in (Path(__file__).resolve().parents[2].name.removeprefix('TrafficEngine-'),):
    root=Path(__file__).resolve().parents[2]
    folder=root/'common/src/main/resources/assets/trafficengine/models/block/curved_traffic_pole'
    offsets={0:(0,0),3:(16,-16),4:(16,-32),5:(16,-48)}
    joined={};count=0;old_jump=0
    def shade(n):
        x,y,z=n;return min(1,.6*x*x+.8*z*z+y*y*(.75+.25*y))
    for part,(oy,oz) in offsets.items():
        model=json.loads((folder/f'part_{part}.json').read_text())
        assert not model['ambientocclusion']
        for e in model['elements']:
            for side,f in e['faces'].items():
                d=f['trafficengine_data']
                if 'shade_normals' not in d:continue
                v=d['vertices'];normals=d['shade_normals'];assert len(normals)==12
                a=[v[3+i]-v[i] for i in range(3)];b=[v[6+i]-v[i] for i in range(3)]
                n=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];length=math.sqrt(sum(x*x for x in n));n=[x/length for x in n]
                family=side if side in ('east','west') else ('outer' if n[1]+n[2]>0 else 'inner')
                for i in range(4):
                    key=(family,round(v[i*3],5),round(v[i*3+1]+oy,5),round(v[i*3+2]+oz,5))
                    smooth=normals[i*3:i*3+3];assert abs(sum(x*x for x in smooth)-1)<1e-6
                    if key in joined:
                        previous,old=joined[key]
                        for turn in range(4):
                            angle=turn*math.pi/2;c,s=math.cos(angle),math.sin(angle)
                            rotated=lambda q:(c*q[0]+s*q[2],q[1],-s*q[0]+c*q[2])
                            assert abs(shade(rotated(smooth))-shade(rotated(previous)))<1e-7,(part,side,key)
                        old_jump=max(old_jump,abs(shade(n)-old));count+=1
                    joined[key]=(smooth,shade(n))
    assert count>=80,(count,old_jump)
    assert old_jump>.04,old_jump
    print(version,':',count,'joined corners / four facings: new shade discontinuity < 0.0000001; old maximum',round(old_jump,6))
