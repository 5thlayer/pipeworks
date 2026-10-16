# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

_m2=open("proto/make2.py").read(); exec(_m2.split("P=lambda")[0])
from PIL import Image, ImageDraw, ImageEnhance
K=8; W,H=300,330
def proj(x,y,z,O): return (O[0]+(x-z)*K, O[1]+(x+z)/2*K-y*K)
def quad(img,tex,uv,P0,PU,PV,shade):
    u0,v0,u1,v1=uv; t=tex.crop((u0,v0,u1,v1)); w,h=max(1,round((u1-u0)*K)),max(1,round((v1-v0)*K))
    t=ImageEnhance.Brightness(t.resize((w,h),Image.NEAREST)).enhance(shade)
    A=((PU[0]-P0[0])/w,(PU[1]-P0[1])/w); B=((PV[0]-P0[0])/h,(PV[1]-P0[1])/h)
    a,b,c,d=A[0],B[0],A[1],B[1]; det=a*d-b*c; ia,ib,ic,id_=d/det,-b/det,-c/det,a/det
    data=(ia,ib,-(ia*P0[0]+ib*P0[1]),ic,id_,-(ic*P0[0]+id_*P0[1]))
    img.alpha_composite(t.transform(img.size,Image.AFFINE,data,resample=Image.NEAREST,fillcolor=(0,0,0,0)))
def render(boxes,label):
    img=Image.new('RGBA',(W,H),(0,0,0,0)); O=(W/2,150)
    for (x0,y0,z0,x1,y1,z1),tex in sorted(boxes,key=lambda b:(max(b[0][3],b[0][5],b[0][4]) if False else 0, b[0][2]+b[0][0]+b[0][1])):
        # up face: u=x, v=z
        quad(img,tex('up'),(x0,z0,x1,z1),proj(x0,y1,z0,O),proj(x1,y1,z0,O),proj(x0,y1,z1,O),1.0)
        # south: u=x, v=16-y
        quad(img,tex('south'),(x0,16-y1,x1,16-y0),proj(x0,y1,z1,O),proj(x1,y1,z1,O),proj(x0,y0,z1,O),0.8)
        # east: u=16-z, v=16-y
        quad(img,tex('east'),(16-z1,16-y1,16-z0,16-y0),proj(x1,y1,z1,O),proj(x1,y1,z0,O),proj(x1,y0,z1,O),0.62)
    ImageDraw.Draw(img).text((8,H-16),label,fill='white'); return img
sl=slate; casing=tileR('industrial_metal_duct_sides_1'); ironplain=tileR('industrial_metal_duct_flat_1',IRON)
ironside=tileR('industrial_metal_duct_sides_1',IRON)
hole=flangeR(ironplain,True)  # slate ring + dark hole on iron
holeS=flangeR(casing)          # iron ring on slate
def T(front,side,frontdir): return lambda f: front if f==frontdir else side
def rot_up(b):  # horizontal (front +z/south) -> vertical (front +y/up)
    x0,y0,z0,x1,y1,z1=b; return (x0,z0,16-y1,x1,z1,16-y0)
def designs(up):
    fd='up' if up else 'south'; R=(rot_up if up else (lambda b:b))
    return [
     ('4a nozzle', [(R((0,0,0,16,16,12)),T(sl['vent'],sl['s1'],fd)), (R((5,5,12,11,11,16)),T(hole,ironplain,fd))]),
     ('4b stepped spout', [(R((0,0,0,16,16,10)),T(casing,sl['s1'],fd)), (R((2,2,10,14,14,13)),T(ironside,ironside,fd)), (R((4,4,13,12,12,16)),T(hole,ironplain,fd))]),
     ('4c narrow body, back plate', [(R((0,0,0,16,16,3)),T(sl['s1'],sl['s1'],fd)), (R((2,2,3,14,14,16)),T(sl['vent'],casing,fd))]),
    ]
pump=render([((0,0,0,16,16,16),lambda f:{'up':sl['housing'],'south':sl['intake'],'east':sl['s1']}[f])],'Pump (full cube)')
rows=[]
for up in (False,True):
    ims=[pump]+[render(bx,n+(' (vertical)' if up else '')) for n,bx in designs(up)]
    r=Image.new('RGBA',(W*len(ims),H),(52,58,64,255))
    for i,im in enumerate(ims): r.alpha_composite(im,(i*W,0))
    rows.append(r)
out=Image.new('RGBA',(rows[0].width,H*2),(52,58,64,255)); out.alpha_composite(rows[0],(0,0)); out.alpha_composite(rows[1],(0,H)); out.save('proto/iso3.png')
