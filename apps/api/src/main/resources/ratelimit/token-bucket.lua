local now = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local refill_tokens = tonumber(ARGV[3])
local refill_period = tonumber(ARGV[4])

local state = redis.call('HMGET', KEYS[1], 'tokens', 'last_refill_ms')
local tokens = tonumber(state[1])
local last_refill = tonumber(state[2])
if tokens == nil then tokens = capacity end
if last_refill == nil then last_refill = now end

local elapsed = math.max(0, now - last_refill)
local intervals = math.floor(elapsed / refill_period)
if intervals > 0 then
  tokens = math.min(capacity, tokens + intervals * refill_tokens)
  last_refill = last_refill + intervals * refill_period
end

local allowed = 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
end

local retry_after = 0
if allowed == 0 then
  retry_after = math.max(1, (last_refill + refill_period) - now)
end

redis.call('HSET', KEYS[1], 'tokens', tokens, 'last_refill_ms', last_refill)
local intervals_to_full = math.max(1, math.ceil(capacity / refill_tokens))
redis.call('PEXPIRE', KEYS[1], intervals_to_full * refill_period * 2)
return tostring(allowed) .. ':' .. tostring(tokens) .. ':' .. tostring(retry_after)
