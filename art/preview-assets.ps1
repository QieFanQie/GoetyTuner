$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root=Join-Path $PSScriptRoot '../src/main/resources/assets/goetytuner/textures/entity'
$skin=[System.Drawing.Bitmap]::new("$root/tuner.png")
$cape=[System.Drawing.Bitmap]::new("$root/tuner_cape.png")
$sheet=[System.Drawing.Bitmap]::new(1120,660)
$g=[System.Drawing.Graphics]::FromImage($sheet)
$g.Clear([System.Drawing.Color]::FromArgb(26,29,40))
$g.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
$font=[System.Drawing.Font]::new('Arial',16)
function Tile($img,$sx,$sy,$w,$h,$x,$y,$scale) {
    $g.DrawImage($img,[System.Drawing.Rectangle]::new($x,$y,$w*$scale,$h*$scale),$sx,$sy,$w,$h,[System.Drawing.GraphicsUnit]::Pixel)
}
$g.DrawString('FRONT  /  original head', $font,[System.Drawing.Brushes]::White,20,20)
Tile $skin 8 8 8 8 92 70 12
Tile $skin 20 20 8 12 92 166 12
Tile $skin 44 20 4 12 44 166 12
Tile $skin 36 52 4 12 188 166 12
Tile $skin 4 20 4 12 92 310 12
Tile $skin 20 52 4 12 140 310 12
$g.DrawString('BACK  /  cape separately', $font,[System.Drawing.Brushes]::White,300,20)
Tile $skin 24 8 8 8 380 70 12
Tile $skin 32 20 8 12 380 166 12
Tile $skin 44 52 4 12 332 166 12
Tile $skin 52 20 4 12 476 166 12
Tile $skin 28 52 4 12 380 310 12
Tile $skin 12 20 4 12 428 310 12
$g.DrawString('CAPE / 8 bands', $font,[System.Drawing.Brushes]::White,575,20)
Tile $cape 13 2 10 16 596 80 12
$g.DrawString('64 x 64 UV', $font,[System.Drawing.Brushes]::White,800,20)
Tile $skin 0 0 64 64 780 80 5
$g.DrawString('AI clothing patches assembled into exact vanilla UVs.', $font,[System.Drawing.Brushes]::LightGray,20,510)
$g.DrawString('Head RGBA preserved pixel-for-pixel. Preview is flat UV, not in-game footage.', $font,[System.Drawing.Brushes]::LightGray,20,545)
$sheet.Save("$PSScriptRoot/preview.png",[System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose();$sheet.Dispose();$skin.Dispose();$cape.Dispose();$font.Dispose()
