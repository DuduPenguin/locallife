# 秒杀压测准备：给 300 个"虚拟用户"写登录态到 Redis
#
# 用法（在项目根目录执行）：
#   powershell -ExecutionPolicy Bypass -File script\prepare_tokens.ps1
#
# 原理：RefreshTokenInterceptor 只读 login:token:{token} 这个 Hash，
#      所以我们直接写 Redis 就能"伪造"登录态，不用真的走 300 次短信登录。

$ErrorActionPreference = "Stop"

$container = "redis-vector"
$db        = 1
$userStart = 1001
$userCount = 300
$ttl       = 7200

Write-Host "生成 $userCount 个用户的 token（#$userStart ~ #$($userStart + $userCount - 1)）..."

$lines = New-Object System.Collections.Generic.List[string]
for ($i = $userStart; $i -lt ($userStart + $userCount); $i++) {
    $key = "login:token:bench_$i"
    $lines.Add("HSET $key id $i nickName bench$i icon /imgs/avatar2.png")
    $lines.Add("EXPIRE $key $ttl")
}

$tmp = Join-Path $env:TEMP "locallife_tokens.txt"
$lines | Set-Content -LiteralPath $tmp -Encoding ASCII

Write-Host "写入 Redis（--pipe 批量，几秒完成）..."
cmd /c "docker exec -i $container redis-cli -n $db --pipe < `"$tmp`""

Remove-Item -LiteralPath $tmp -Force

Write-Host ""
Write-Host "验证："
Write-Host "  docker exec -it $container redis-cli -n $db hget login:token:bench_1001 id"
Write-Host "  docker exec -it $container redis-cli -n $db hget login:token:bench_1300 id"
Write-Host ""
Write-Host "准备完成，可以跑压测了："
Write-Host "  java script\SeckillBench.java 200"
