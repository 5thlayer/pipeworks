# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

# Temporary cover and icon, until an artist draws the real ones, in the style
# of Groundworks' and Craftworks' publish/make-cover.py. Run from the repo root with Pillow
# installed, Impact and Arial Black installed (macOS has them; elsewhere in
# /usr/share/fonts/TTF or ~/.local/share/fonts/msfonts), and the
# Minecraft 26.1.2 client jar NeoForm caches (for the fluid textures) at CLIENT.
#
# Each row is a pipe run laid in segments: a segment is one fluid, in its pipes and at the same
# level in each of its tanks, and the pipe that would have joined two fluids stands refused.
import os, zipfile, io
from PIL import Image, ImageDraw, ImageFont
CLIENT=os.path.expanduser('~/.gradle/caches/neoformruntime/artifacts/minecraft_26.1.2_client.jar')
P='src/main/resources/assets/pipeworks/textures/block/'
BG=(24,26,32)
ACCENT=(63,118,228)   # water's own tint
REFUSED=(235,70,70)   # the mixed-fluids refusal
FADE=70  # 0 leaves the rows at full strength, 255 hides them
COPPER=(0,0,16,3)          # pipe.png: one copper band of a pipe's side
GLASS=(32,0,46,14)         # storage_tank.png: the glass side
RIM=(48,0,64,6)            # storage_tank.png: the copper rim
# A fluid: the still texture, and the tint the game gives it (None keeps the texture's colours).
FLUIDS={
    'water':('water_still',(63,118,228)),
    'lava':('lava_still',None),
    'oil':('water_still',(95,78,62)),
    'acid':('water_still',(120,200,60)),
    'steam':('water_still',(215,220,226)),
}
# Rows, left to right: segments as (fluid, blocks, tank positions, level), each next one set
# apart by a refused pipe.
ROWS=[
    [('water',7,[2],0.7),('lava',9,[3,7],0.45),('oil',8,[4],0.8)],
    [('acid',5,[2],0.55),('steam',10,[3,8],0.35),('water',9,[5],0.6)],
    [('oil',9,[2,6],0.5),('water',6,[3],0.85),('acid',9,[4],0.4)],
    [('lava',6,[3],0.65),('acid',8,[2,6],0.75),('steam',9,[4],0.5)],
]
_jar=zipfile.ZipFile(CLIENT)
_cache={}
def fluid(name,w,h):
    key=('fluid',name,w,h)
    if key not in _cache:
        tex,tint=FLUIDS[name]
        t=Image.open(io.BytesIO(_jar.read('assets/minecraft/textures/block/'+tex+'.png'))).convert('RGBA').crop((0,0,16,16))
        if tint:
            # the game multiplies the grey texture by the tint
            g=t.convert('L')
            t=Image.merge('RGB',[g.point(lambda v,c=c:min(255,int(v*c/255*1.25))) for c in tint]).convert('RGBA')
        s=max(w,h)
        _cache[key]=t.resize((s,s),Image.NEAREST).crop((0,0,w,h))
    return _cache[key]
def part(name,box,w,h):
    key=(name,box,w,h)
    if key not in _cache:
        _cache[key]=Image.open(P+name+'.png').convert('RGBA').crop(box).resize((w,h),Image.NEAREST)
    return _cache[key]
def pipe(img,x,cy,s,name,refused=False):
    # one pipe seen from the side: copper walls, the fluid between them, a flange at each end
    h=s//2; wall=max(2,s//10); top=cy-h//2
    if refused:
        # the pipe that would have joined two fluids: a ghost washed red, outlined, as placement shows it
        ghost=Image.new('RGBA',(s,h),(0,0,0,0))
        ghost.alpha_composite(part('pipe',COPPER,s,wall),(0,0))
        ghost.alpha_composite(part('pipe',COPPER,s,wall),(0,h-wall))
        ghost=Image.alpha_composite(ghost,Image.new('RGBA',(s,h),REFUSED+(150,)))
        ghost.putalpha(200)
        img.alpha_composite(ghost,(x,top))
        d=ImageDraw.Draw(img); lw=max(2,s//20); m=h//4
        d.rectangle([x,top,x+s-1,top+h-1],outline=(255,255,255,230),width=lw)
        d.line([(x+s//2-m,top+m),(x+s//2+m,top+h-m)],fill=(255,255,255,230),width=lw)
        d.line([(x+s//2-m,top+h-m),(x+s//2+m,top+m)],fill=(255,255,255,230),width=lw)
        return
    for xx in range(x,x+s,s//2):
        img.alpha_composite(fluid(name,s//2,h-2*wall),(xx,top+wall))
    img.alpha_composite(part('pipe',COPPER,s,wall),(x,top))
    img.alpha_composite(part('pipe',COPPER,s,wall),(x,top+h-wall))
    d=ImageDraw.Draw(img); fw=max(2,s//12)
    d.rectangle([x,top-wall//2,x+fw-1,top+h+wall//2-1],fill=(150,82,48,255))
def tank(img,x,cy,s,name,level):
    # a storage tank: glass between copper rims, its fluid at the segment's level
    h=s; top=cy-h//2; rim=max(3,s//7)
    img.alpha_composite(part('storage_tank',GLASS,s,h-2*rim),(x,top+rim))
    fh=int((h-2*rim)*level); inset=max(2,s//10)
    if fh>0:
        f=fluid(name,s-2*inset,fh).copy(); f.putalpha(225)
        img.alpha_composite(f,(x+inset,top+h-rim-fh))
    img.alpha_composite(part('storage_tank',RIM,s,rim),(x,top))
    img.alpha_composite(part('storage_tank',RIM,s,rim),(x,top+h-rim))
def row(img,segments,cy,s,offset,W):
    x=-offset
    while x<W:
        for name,blocks,tanks,level in segments:
            for i in range(blocks):
                if i in tanks: tank(img,x,cy,s,name,level)
                else: pipe(img,x,cy,s,name)
                x+=s
            pipe(img,x,cy,s,None,refused=True)
            x+=s
def font(names,size):
    for n in names:
        if os.path.exists(n):
            return ImageFont.truetype(n,size)
    raise SystemExit('needs one of '+', '.join(names))
def make(W,H,out,title_size,s):
    img=Image.new('RGBA',(W,H),BG+(255,))
    bh=int(title_size*1.6); free=(H-bh)//2; pitch=s+s//4
    n=max(1,free//pitch); top=(free-n*pitch+s//4)//2
    ys=[top+i*pitch+s//2 for i in range(n)]+[H//2]+[H-free+top+i*pitch+s//2 for i in range(n)]
    for i,cy in enumerate(ys):
        row(img,ROWS[i%len(ROWS)],cy,s,(i*s*3)%(s*7),W)
    img=Image.alpha_composite(img,Image.new('RGBA',(W,H),BG+(FADE,)))
    cy=H//2
    band=Image.new('RGBA',(W,H),(0,0,0,0)); bd=ImageDraw.Draw(band)
    bd.rectangle([0,cy-bh//2,W,cy+bh//2],fill=(16,17,22,225))
    img=Image.alpha_composite(img,band)
    d=ImageDraw.Draw(img)
    lt=max(3,title_size//25)
    d.rectangle([0,cy-bh//2,W,cy-bh//2+lt],fill=ACCENT); d.rectangle([0,cy+bh//2-lt,W,cy+bh//2],fill=ACCENT)
    f1=font(['/System/Library/Fonts/Supplemental/Impact.ttf','/usr/share/fonts/TTF/impact.ttf',
             os.path.expanduser('~/.local/share/fonts/msfonts/impact.ttf')],title_size)
    f2=font(['/System/Library/Fonts/Supplemental/Arial Black.ttf','/usr/share/fonts/TTF/ariblk.ttf',
             os.path.expanduser('~/.local/share/fonts/msfonts/ariblk.ttf')],int(title_size*0.62))
    a,w='PIPE','works'
    b1=d.textbbox((0,0),a,font=f1); b2=d.textbbox((0,0),w,font=f2)
    w1=b1[2]-b1[0]; w2=b2[2]-b2[0]; h2=b2[3]-b2[1]
    px=int(title_size*0.16); gap=int(title_size*0.1)
    bw=w2+2*px; tot=w1+gap+bw; x=(W-tot)//2
    h1=b1[3]-b1[1]; y=cy-h1//2-b1[1]; sh=max(3,title_size//20)
    d.text((x+sh-b1[0],y+sh),a,font=f1,fill=(0,0,0))
    d.text((x-b1[0],y),a,font=f1,fill=ACCENT)
    bx=x+w1+gap; bt=cy-h1//2; bb=bt+h1
    d.rounded_rectangle([bx+sh,bt+sh,bx+bw+sh,bb+sh],radius=px,fill=(0,0,0))
    d.rounded_rectangle([bx,bt,bx+bw,bb],radius=px,fill=(240,240,236))
    ty=bt+(h1-h2)//2-b2[1]
    d.text((bx+px-b2[0],ty),w,font=f2,fill=BG)
    img.convert('RGB').save(out)
make(1280,640,'publish/pipeworks-cover.png',150,80)
make(512,512,'publish/pipeworks-icon.png',80,60)
