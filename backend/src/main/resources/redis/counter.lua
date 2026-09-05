-- 1초 버킷 슬라이딩 윈도우 카운터.
-- KEYS[1] = ec:{policyId}:{fpKey}   (HASH: field=epoch second, value=count)
-- ARGV[1] = nowSec, ARGV[2] = windowSec, ARGV[3] = ttlSec
-- 반환: 윈도우 [nowSec - windowSec + 1, nowSec] 안의 총 건수 (이번 증가 포함)
local now = tonumber(ARGV[1])
local w = tonumber(ARGV[2])
local minSec = now - w + 1
redis.call('HINCRBY', KEYS[1], now, 1)
local total = 0
local all = redis.call('HGETALL', KEYS[1])
for i = 1, #all, 2 do
  local sec = tonumber(all[i])
  if sec < minSec then
    redis.call('HDEL', KEYS[1], all[i])
  else
    total = total + tonumber(all[i + 1])
  end
end
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
return total
