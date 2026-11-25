-- 时段名额预扣脚本
-- KEYS[1] = slot:cap:{slotId}
-- 返回 -1 未预热（key 不存在）
--       -2 名额已满（未扣减）
--      >=0 扣减成功，值为扣减后的余量；0 表示拿到了最后一个名额
if redis.call('EXISTS', KEYS[1]) == 0 then
    return -1
end
local left = tonumber(redis.call('GET', KEYS[1]))
if left == nil then
    return -1
end
if left <= 0 then
    return -2
end
return redis.call('DECR', KEYS[1])
