# 生成天体用的灰度球面贴图（32x32）。
# 作者 2026-11-07：不要那么圆，要有「方形感」——所以用超椭圆（squircle，指数 3.2）做轮廓，
# 明暗量化成 4 段，边缘不做抗锯齿，保留像素块的感觉。颜色由渲染器按天体染（金/白/蓝）。
Add-Type -AssemblyName System.Drawing

$size = 32
$bmp = New-Object System.Drawing.Bitmap($size, $size)
$half = ($size - 1) / 2.0
$n = 3.2
# 光源方向（左上偏前）
$lx = -0.45; $ly = -0.50; $lz = 0.74

for ($y = 0; $y -lt $size; $y++) {
    for ($x = 0; $x -lt $size; $x++) {
        $dx = ($x - $half) / $half
        $dy = ($y - $half) / $half
        $ax = [math]::Abs($dx); $ay = [math]::Abs($dy)
        # 超椭圆轮廓：|x|^n + |y|^n <= 1 是圆润的方形
        $shape = [math]::Pow($ax, $n) + [math]::Pow($ay, $n)
        if ($shape -gt 1.0) {
            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
            continue
        }
        # 球面法线（轮廓内才成立）
        $r2 = [math]::Min(1.0, $dx * $dx + $dy * $dy)
        $nz = [math]::Sqrt([math]::Max(0.0, 1.0 - $r2))
        $lit = $dx * $lx + $dy * $ly + $nz * $lz
        if ($lit -lt 0) { $lit = 0 }
        # 量化成 4 段，突出像素块
        $levels = @(0.16, 0.42, 0.68, 0.94)
        $level = $levels[0]
        foreach ($lv in $levels) { if ($lit -ge ($lv - 0.12)) { $level = $lv } }
        # 轮廓最外一圈压暗，读起来像一块方块而不是一张贴图
        if ($shape -gt 0.86) { $level = $level * 0.55 }
        $v = [int]([math]::Round(255 * $level))
        $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $v, $v, $v))
    }
}

$out = "D:\MCModDev\forge-mdk\src\main\resources\assets\yanscriptures\textures\entity\orb.png"
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Write-Output "written: $out"
