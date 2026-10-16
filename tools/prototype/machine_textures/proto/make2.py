# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

_m=open('proto/make.py').read()
exec(_m.split("casing=tile(")[0]); exec("S=8"+_m.split("S=8",1)[1].split("A=row(")[0])
IRON=[(0,(32,40,52)),(45,(65,71,88)),(70,(120,124,132)),(88,(163,163,163)),(105,(183,183,183)),(125,(203,203,203)),(165,(225,225,225)),(255,(252,252,252))]
def ramp2(l,st):
    for (a,ca),(b,cb) in zip(st,st[1:]):
        if l<=b:
            t=(l-a)/(b-a) if b>a else 0
            return tuple(round(ca[i]+(cb[i]-ca[i])*t) for i in range(3))
def tileR(n,st=STOPS,flat=False):
    im=Image.open(UT+n+'.png').convert('RGBA').crop((0,0,16,16)); out=Image.new('RGBA',(16,16))
    ls=sorted(lum(im.getpixel((x,y))) for x in range(16) for y in range(16)); med=ls[128]
    for y in range(16):
        for x in range(16):
            p=im.getpixel((x,y)); l=lum(p)
            if flat: l=med+(l-med)*0.5
            l=l-med+88
            out.putpixel((x,y),((203,203,203) if p[0]>=160 and not flat else ((240,240,240) if lum(p)>=160 else ramp2(l,st)))+(p[3],))
    return out
def flangeR(base,iron=False):
    out=base.copy()
    for y in range(3,13):
        for x in range(3,13):
            p=duct.getpixel((x,y))
            if p[:3]==frame[:3]: continue
            if p[0]>p[1]+30:
                t=min(max((lum(p)-80)/46,0),1)
                g=round(150+90*t) if not iron else round(65+40*t)
                out.putpixel((x,y),(g,g,g+ (12 if iron else 0),255))
            else: out.putpixel((x,y),p)
    return out
slate=dict(intake=tileR('industrial_metal_duct_intake'),vent=tileR('industrial_metal_duct_vent'),
  housing=tileR('industrial_metal_duct_1',flat=True),s1=flangeR(tileR('industrial_metal_duct_sides_1')),
  s2=flangeR(tileR('industrial_metal_duct_sides_2')),flat=flangeR(tileR('industrial_metal_duct_flat_1')))
iron=dict(vent=tileR('industrial_metal_duct_vent',IRON),s1=flangeR(tileR('industrial_metal_duct_sides_1',IRON),True),
  s1i=flangeR(tileR('industrial_metal_duct_sides_1',IRON)))
P=lambda side:cube(slate['housing'],slate['intake'],side,'')
rows=[
 row([P(slate['s2']),cube(slate['s1'],slate['vent'],slate['s1'],''),cube(slate['vent'],slate['s1'],slate['s1'],'')],
     '1  casing pattern: Pump banded sides, Outlet cross-braced sides'),
 row([P(slate['s1']),cube(iron['s1'],iron['vent'],iron['s1'],''),cube(iron['vent'],iron['s1'],iron['s1'],'')],
     '2  body tone: slate Pump, iron-grey Outlet (slate flange)'),
 row([P(slate['s2']),cube(iron['s1'],iron['vent'],iron['s1'],''),cube(iron['vent'],iron['s1'],iron['s1'],'')],
     '3  both: banded slate Pump, iron-grey Outlet'),
]
out=Image.new('RGBA',(rows[0].width,sum(r.height for r in rows)),(52,58,64,255)); y=0
for r in rows: out.alpha_composite(r,(0,y)); y+=r.height
out.save('proto/iso2.png')
