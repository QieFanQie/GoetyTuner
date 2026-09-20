"""Read-only resource checks and executable tests of the production Java visual methods."""
from pathlib import Path
from PIL import Image
import json, struct, subprocess, tempfile

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art'
RES = ROOT / 'src/main/resources/assets/goetytuner'
JAVA = ROOT / 'src/main/java/com/tiaolvshi/goetytuner'
JDK = Path('C:/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot/bin')

def png(name, size):
    path = RES / 'textures/entity' / name
    im = Image.open(path)
    assert im.size == size and im.mode == 'RGBA', (name, im.size, im.mode)
    data = path.read_bytes()
    assert data[28] == 0, 'Interlaced PNG'
    offset, chunks = 8, []
    while offset < len(data):
        length = struct.unpack('>I', data[offset:offset+4])[0]
        chunks.append(data[offset+4:offset+8])
        offset += length + 12
    assert b'zTXt' not in chunks
    return im

skin = png('tuner.png', (64, 64))
old = Image.open(ART / 'tuner-before.png')
assert skin.crop((0, 0, 64, 16)).tobytes() == old.crop((0, 0, 64, 16)).tobytes()
for u, v, w, d, h in [(16,16,8,4,12),(40,16,4,4,12),(32,48,4,4,12),
                       (0,16,4,4,12),(16,48,4,4,12)]:
    for box in [(u+d,v,u+d+2*w,v+d), (u,v+d,u+2*(w+d),v+d+h)]:
        assert skin.crop(box).getchannel('A').getextrema() == (255,255), box
cape = png('tuner_cape.png', (64,32))
rows = [cape.getpixel((1,y)) for y in range(2,18)]
assert len(set(rows)) == 8
assert all(sum(rows[i][:3]) <= sum(rows[i+1][:3]) for i in range(15))
for y in range(2,18):
    assert all(cape.getpixel((x,y)) == rows[y-2] for x in range(1,23))
assert all(cape.getpixel((x,1)) == rows[0] for x in range(2,12))
assert all(cape.getpixel((x,1)) == rows[-1] for x in range(12,22))
orb = png('tuner_orb.png', (16,16))
for x,y in [(4,0),(8,0),(0,4),(4,4),(8,4),(12,4)]:
    assert orb.crop((x,y,x+4,y+4)).getchannel('A').getextrema() == (255,255)
assert png('accent_wave.png',(1,1)).getpixel((0,0)) == (255,255,255,255)
assert json.loads((RES/'models/item/tuner_spawn_egg.json').read_text()) == {
    'parent':'minecraft:item/template_spawn_egg'}
assert not (RES/'textures/item/tuner_spawn_egg.png').exists()
print('PASS: RGBA/PNG structure, all six UV faces, exact head, eight monotone cape bands, vanilla egg')

def method(text, signature):
    start = text.index(signature)
    left = text.index('{',start)
    depth = 1
    right = left+1
    while depth:
        depth += (text[right]=='{') - (text[right]=='}')
        right += 1
    return text[start:right]

boss = (JAVA/'entity/TunerBoss.java').read_text(encoding='utf-8')
wave = (JAVA/'client/AccentWaveRenderer.java').read_text(encoding='utf-8')
category = method(boss,'private void changeCastCategory(').replace(
    'com.tiaolvshi.goetytuner.focus.FocusEntry','Entry')
height = method(wave,'private static float height(')
harness = '''import java.util.*;
public class VisualChecks {
 enum Category { ATTACK, DEFENSE, SUMMON, OTHER }
 record Entry(Category category) { Category getCategory() { return category; } }
 static final int DATA_CAST_CATEGORIES=0;
 static class Data { int mask; void set(int key,int value) {mask=value;} }
 final Data entityData=new Data();
 final int[] activeByCategory=new int[4];
 final Set<Entry> activeVisualCasts=Collections.newSetFromMap(new IdentityHashMap<>());
 int getActiveCastCategories(){return entityData.mask;}
''' + category + '\n' + height + '''
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args) {
  VisualChecks state=new VisualChecks();
  Entry a=new Entry(Category.ATTACK), b=new Entry(Category.ATTACK);
  Entry d=new Entry(Category.DEFENSE), s=new Entry(Category.SUMMON);
  state.changeCastCategory(a,1); state.changeCastCategory(b,1);
  state.changeCastCategory(d,1); state.changeCastCategory(s,1);
  check(state.getActiveCastCategories()==7);
  state.changeCastCategory(a,-1); check(state.getActiveCastCategories()==7);
  state.changeCastCategory(a,-1); check(state.getActiveCastCategories()==7);
  state.changeCastCategory(new Entry(Category.ATTACK),-1);
  check(state.getActiveCastCategories()==7);
  state.changeCastCategory(b,-1); check(state.getActiveCastCategories()==6);
  state.changeCastCategory(d,-1); check(state.getActiveCastCategories()==4);
  state.changeCastCategory(s,-1); check(state.getActiveCastCategories()==0);
  double max=0;
  for(int ring=0;ring<10;ring++)for(int tick=0;tick<=340;tick++) {
   float t=tick/10F, p=(t-ring*2)/16;
   if(p<0||p>1)continue;
   float envelope=(float)Math.sin(Math.PI*p);
   for(int aIndex=0;aIndex<360;aIndex++) {
    float h=height(aIndex*Math.PI/180,t,ring,envelope);
    check(h>=-0.00001F && h+0.01<=0.8);
    max=Math.max(max,h+0.01);
   }
  }
  System.out.println("PASS: concurrent same/different-category casts, duplicate/unknown ends; max top="+max);
 }
}'''
with tempfile.TemporaryDirectory(prefix='tuner-visual-check-') as directory:
    path=Path(directory)/'VisualChecks.java'
    path.write_text(harness,encoding='utf-8')
    subprocess.run([str(JDK/'javac.exe'),'-encoding','UTF-8',str(path)],check=True)
    subprocess.run([str(JDK/'java.exe'),'-cp',directory,'VisualChecks'],check=True)

# The actual knockback and sound implementation must remain byte-for-byte unchanged.
original=subprocess.check_output(['git','show','HEAD:src/main/java/com/tiaolvshi/goetytuner/entity/TunerBoss.java'],cwd=ROOT).decode('utf-8')
for start,end in [('List<LivingEntity> around =','// 【2026-08-19 第十九轮】重音特效'),
                  ('if (TunerCommonConfig.ACCENT_SOUND.get()) {','// ---- 传送行为 ----')]:
    assert original[original.index(start):original.index(end)] == boss[boss.index(start):boss.index(end)]
print('PASS: knockback and accent audio code unchanged from HEAD')
