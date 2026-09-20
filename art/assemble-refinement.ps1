param([string]$Generated = "$PSScriptRoot/body-refined-source.png")
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$entityDir = Join-Path $PSScriptRoot '../src/main/resources/assets/goetytuner/textures/entity'
$source = [System.Drawing.Bitmap]::new($Generated)
$old = [System.Drawing.Bitmap]::new("$PSScriptRoot/tuner-before-refinement.png")
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
# Source rectangles measured from the generated atlas (1277 x 1232).
Box 16 16 8 4 @(474,417,177,309) @(716,417,171,309) @(717,417,28,309) @(478,420,28,40)
Box 40 16 4 4 @(115,417,96,309) @(936,417,95,309) @(257,417,56,309) @(130,420,50,35)
Box 32 48 4 4 @(1075,417,101,309) @(936,417,95,309) @(257,417,56,309) @(130,420,50,35)
Box 0 16 4 4 @(116,849,98,314) @(259,849,54,314) @(259,849,54,314) @(120,851,28,20)
Box 16 48 4 4 @(476,849,102,314) @(259,849,54,314) @(615,849,42,314) @(480,851,25,20)
$skin.Save("$entityDir/tuner.png",[System.Drawing.Imaging.ImageFormat]::Png)

$source.Dispose(); $old.Dispose(); $skin.Dispose()