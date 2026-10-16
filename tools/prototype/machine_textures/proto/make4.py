# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

exec(open('proto/make3.py').read().split("pump=render(")[0])
def P(top,label): return render([((0,0,0,16,16,16),lambda f:{'up':top,'south':sl['intake'],'east':sl['s1']}[f])],label)
ims=[P(tileR('industrial_metal_duct_1'),'a  housing, shading kept'),P(tileR('industrial_metal_duct_1',flat=True),'b  housing, flattened'),P(tileR('industrial_metal_duct_flat_1'),'c  flat panel')]
r=Image.new('RGBA',(W*3,H),(52,58,64,255))
for i,im in enumerate(ims): r.alpha_composite(im,(i*W,0))
r.save('proto/iso4.png')
