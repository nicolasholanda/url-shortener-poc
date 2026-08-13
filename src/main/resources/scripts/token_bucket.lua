local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_per_second = tonumber(ARGV[2])
local now_millis = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'timestamp')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

if tokens == nil then
    tokens = capacity
    last_refill = now_millis
end

local elapsed = math.max(0, now_millis - last_refill)
local refilled = elapsed * refill_per_second / 1000.0
tokens = math.min(capacity, tokens + refilled)

local allowed = 0
local retry_after_millis = 0

if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
else
    local missing = requested - tokens
    retry_after_millis = math.ceil(missing * 1000.0 / refill_per_second)
end

redis.call('HSET', key, 'tokens', tokens, 'timestamp', now_millis)

local idle_millis = math.ceil(capacity * 1000.0 / refill_per_second) * 2
redis.call('PEXPIRE', key, idle_millis)

return { allowed, math.floor(tokens), retry_after_millis }
