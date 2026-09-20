param([string]$Generated = "$PSScriptRoot/body-source.png")
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$entityDir = Join-Path $PSScriptRoot '../src/main/resources/assets/goetytuner/textures/entity'
$source = [System.Drawing.Bitmap]::new($Generated)
$old = [System.Drawing.Bitmap]::new("$PSScriptRoot/tuner-before.png")
$skin = [System.Drawing.Bitmap]::new(64,64,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
# Production assembly only: nearest-neighbour sampling of imagegen artwork into exact vanilla UVs.
# Keep all original head/hat pixels, including transparent RGB values.
for ($y=0; $y -lt 16; $y++) { for ($x=0; $x -lt 64; $x++) { $skin.SetPixel($x,$y,$old.GetPixel($x,$y)) } }
function Patch($dstX,$dstY,$w,$h,$sx,$sy,$sw,$sh) {
    for($y=0;$y -lt $h;$y++) { for($x=0;$x -lt $w;$x++) {
        $c=$source.GetPixel([int][Math]::Floor($sx+($x+0.5)*$sw/$w),[int][Math]::Floor($sy+($y+0.5)*$sh/$h))
        $skin.SetPixel($dstX+$x,$dstY+$y,[System.Drawing.Color]::FromArgb(255,$c.R,$c.G,$c.B))
    } }
}
function Box($u,$v,$w,$d,$front,$back,$side,$cap) {
    Patch ($u+$d) $v $w $d @cap
    Patch ($u+$d+$w) $v $w $d @cap
    Patch $u ($v+$d) $d 12 @side
    Patch ($u+$d) ($v+$d) $w 12 @front
    Patch ($u+$d+$w) ($v+$d) $d 12 @side
    Patch ($u+2*$d+$w) ($v+$d) $w 12 @back
}
# Source rectangles measured from the generated atlas (1263 x 1246).
Box 16 16 8 4 @(427,376,173,288) @(669,376,145,288) @(602,377,62,287) @(440,383,25,35)
Box 40 16 4 4 @(83,377,86,289) @(896,377,85,289) @(220,377,43,289) @(100,385,40,30)
Box 32 48 4 4 @(1046,377,99,289) @(896,377,85,289) @(220,377,43,289) @(100,385,40,30)
Box 0 16 4 4 @(81,864,88,295) @(563,864,38,295) @(220,864,44,295) @(105,869,35,20)
Box 16 48 4 4 @(426,864,95,295) @(563,864,38,295) @(220,864,44,295) @(450,869,35,20)
$skin.Save("$entityDir/tuner.png",[System.Drawing.Imaging.ImageFormat]::Png)
# Simple geometric resources: eight discrete two-row bands and a neutral cube net.
$cape=[System.Drawing.Bitmap]::new(64,32,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$bands=@('1E0D32','38204D','523367','6C4882','875E9E','A078BA','BA96D8','D4B6F2')
for($row=0;$row -lt 16;$row++) {
    $c=[System.Drawing.ColorTranslator]::FromHtml('#'+$bands[[int][Math]::Floor($row/2)])
    for($x=1;$x -lt 23;$x++) {$cape.SetPixel($x,$row+2,$c)}
}
# Minecraft puts both horizontal faces ABOVE the side strip, not at its bottom.
for($x=2;$x -lt 12;$x++) {$cape.SetPixel($x,1,[System.Drawing.ColorTranslator]::FromHtml('#'+$bands[0]))}
for($x=12;$x -lt 22;$x++) {$cape.SetPixel($x,1,[System.Drawing.ColorTranslator]::FromHtml('#'+$bands[7]))}
$cape.Save("$entityDir/tuner_cape.png",[System.Drawing.Imaging.ImageFormat]::Png)
$orb=[System.Drawing.Bitmap]::new(16,16,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
foreach($face in @(@(4,0),@(8,0),@(0,4),@(4,4),@(8,4),@(12,4))) {
    for($y=0;$y -lt 4;$y++) {for($x=0;$x -lt 4;$x++) {
        $shade=if($x -eq 0 -or $y -eq 0 -or $x -eq 3 -or $y -eq 3){185}else{245}
        $orb.SetPixel($face[0]+$x,$face[1]+$y,[System.Drawing.Color]::FromArgb(255,$shade,$shade,$shade))
    }}
}
$orb.Save("$entityDir/tuner_orb.png",[System.Drawing.Imaging.ImageFormat]::Png)
$white=[System.Drawing.Bitmap]::new(1,1,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$white.SetPixel(0,0,[System.Drawing.Color]::White)
$white.Save("$entityDir/accent_wave.png",[System.Drawing.Imaging.ImageFormat]::Png)
$source.Dispose(); $old.Dispose(); $skin.Dispose(); $cape.Dispose(); $orb.Dispose(); $white.Dispose()
