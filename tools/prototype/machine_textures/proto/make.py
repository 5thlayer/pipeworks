# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

from PIL import Image, ImageDraw, ImageEnhance
UT='ut/blocks/'; OR='oritech_candidates/'
STOPS=[(0,(18,22,30)),(45,(32,40,52)),(88,(65,71,88)),(105,(86,92,109)),(125,(150,152,156)),(165,(203,203,203)),(255,(252,252,252))]
def ramp(l):
    for (a,ca),(b,cb) in zip(STOPS,STOPS[1:]):
        if l<=b:
            t=(l-a)/(b-a) if b>a else 0
            return tuple(round(ca[i]+(cb[i]-ca[i])*t) for i in range(3))
def lum(p): return 0.299*p[0]+0.587*p[1]+0.114*p[2]
def retint(im):
    im=im.convert('RGBA').crop((0,0,16,16)); out=Image.new('RGBA',(16,16))
    ls=sorted(lum(im.getpixel((x,y))) for x in range(16) for y in range(16)); shift=88-ls[128]
    for y in range(16):
        for x in range(16):
            p=im.getpixel((x,y)); l=lum(p)
            out.putpixel((x,y),((203,203,203) if l>=160 else ramp(l+shift))+(p[3],))
    return out
def tile(n): return retint(Image.open(UT+n+'.png'))
duct=Image.open(OR+'fluid_pipe_duct_block.png').convert('RGBA')
frame=duct.getpixel((0,0))
def flange(base):
    out=base.copy()
    for y in range(3,13):
        for x in range(3,13):
            p=duct.getpixel((x,y))
            if p[:3]==frame[:3]: continue
            if p[0]>p[1]+30:
                t=min(max((lum(p)-80)/46,0),1); g=round(150+90*t); out.putpixel((x,y),(g,g,g,255))
            else: out.putpixel((x,y),p)
    return out
casing=tile('industrial_metal_duct_sides_1'); side=flange(casing)
T={'intake':tile('industrial_metal_duct_intake'),'vent':tile('industrial_metal_duct_vent'),'housing':tile('industrial_metal_duct_1'),
   'housing2':tile('industrial_metal_duct_flat_1'),'side':side,'casing':casing}
for k,v in T.items(): v.save(f'proto/{k}.png')
S=8
def face(tex,O,A,B,shade):
    tex=ImageEnhance.Brightness(tex.resize((16*S,16*S),Image.NEAREST)).enhance(shade)
    n=16*S; a,b=A[0]/n,B[0]/n; c,d=A[1]/n,B[1]/n; det=a*d-b*c
    ia,ib,ic,id_=d/det,-b/det,-c/det,a/det
    data=(ia,ib,-(ia*O[0]+ib*O[1]),ic,id_,-(ic*O[0]+id_*O[1]))
    return tex.transform((W,H),Image.AFFINE,data,resample=Image.NEAREST,fillcolor=(0,0,0,0))
s=16*S; W,H=2*s+40,2*s+40
def cube(top,left,right,label):
    img=Image.new('RGBA',(W,H+24),(0,0,0,0))
    T0=(W/2,20); R=(s,s/2); L=(-s,s/2); D=(0,s)
    add=lambda p,q:(p[0]+q[0],p[1]+q[1])
    img.alpha_composite(face(top,T0,R,L,1.0))
    img.alpha_composite(face(left,add(T0,L),R,D,0.8))
    img.alpha_composite(face(right,add(add(T0,L),R),(s,-s/2),D,0.62))
    ImageDraw.Draw(img).text((10,H+4),label,fill='white'); return img
def row(cubes,title):
    out=Image.new('RGBA',(W*len(cubes),H+60),(52,58,64,255)); ImageDraw.Draw(out).text((10,8),title,fill='white')
    for i,c in enumerate(cubes): out.alpha_composite(c,(i*W,28))
    return out
A=row([cube(T['housing'],T['intake'],T['side'],'Pump: front intake, top housing'),
       cube(T['side'],T['vent'],T['side'],'Outlet horizontal: front vent'),
       cube(T['vent'],T['side'],T['side'],'Outlet vertical (up): front vent'),
       cube(T['side'],T['side'],T['side'],'back/sides: casing + flange')],'A (proposed)')
B=row([cube(T['housing2'],T['vent'],T['side'],'Pump: front vent, top flat housing'),
       cube(T['side'],T['intake'],T['side'],'Outlet horizontal: front intake'),
       cube(T['intake'],T['side'],T['side'],'Outlet vertical (up): front intake'),
       cube(T['casing'],T['casing'],T['casing'],'plain casing, no flange')],'B (swapped fronts)')
sheet=Image.new('RGBA',(A.width,A.height*2),(52,58,64,255)); sheet.alpha_composite(A,(0,0)); sheet.alpha_composite(B,(0,A.height)); sheet.save('proto/iso.png')
