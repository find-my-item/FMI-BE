local quota, pending, request, cache = KEYS[1], KEYS[2], KEYS[3], KEYS[4]
local operation, fingerprint, token = ARGV[1], ARGV[2], ARGV[3]
local day, startAt, resetAt = ARGV[4], tonumber(ARGV[5]), tonumber(ARGV[6])
local language, translated, limit = ARGV[7], ARGV[8], tonumber(ARGV[9])
local cacheTtl = tonumber(ARGV[10])
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local expiresAt = resetAt + 86400000
local used = tonumber(redis.call('GET', quota) or '0')

local function reply(status, count, active, date, reset, lang, text)
    return {status, tostring(count or used), tostring(active or 0), date or day,
            tostring(reset or resetAt), lang or language, text or ''}
end

local state = redis.call('HGET', request, 'state')
if operation ~= 'usage' and state then
    if redis.call('HGET', request, 'fingerprint') ~= fingerprint then
        return reply('CONFLICT')
    end
    if state == 'SUCCEEDED' then
        return reply('SUCCEEDED', redis.call('HGET', request, 'used'), 0,
            redis.call('HGET', request, 'day'), redis.call('HGET', request, 'reset'),
            redis.call('HGET', request, 'language'), redis.call('HGET', request, 'text'))
    end
    if state == 'FAILED' then return reply('FAILED') end
end

if operation == 'reserve' or operation == 'usage' then
    if now < startAt or now >= resetAt then return reply('DAY_CHANGED') end
end
redis.call('ZREMRANGEBYSCORE', pending, '-inf', now)
local active = redis.call('ZCARD', pending)

if operation == 'usage' then return reply('USAGE', used, active) end

if operation == 'reserve' then
    if state then
        if tonumber(redis.call('HGET', request, 'deadline')) <= now then
            redis.call('HSET', request, 'state', 'FAILED')
            return reply('FAILED')
        end
        if redis.call('HGET', request, 'token') == token then
            return reply('RESERVED', used, active, redis.call('HGET', request, 'day'),
                redis.call('HGET', request, 'reset'))
        end
        return reply('IN_PROGRESS', used, active)
    end
    if used >= limit then return reply('LIMIT', used, active) end
    if used + active >= limit then return reply('IN_PROGRESS', used, active) end
    local deadline = now + 45000
    redis.call('SET', quota, tostring(used), 'PXAT', expiresAt)
    redis.call('ZADD', pending, deadline, token)
    redis.call('PEXPIREAT', pending, expiresAt)
    redis.call('HSET', request, 'state', 'PENDING', 'fingerprint', fingerprint,
        'token', token, 'deadline', tostring(deadline), 'day', day,
        'reset', tostring(resetAt), 'language', language)
    redis.call('PEXPIREAT', request, expiresAt)
    return reply('RESERVED', used, active + 1)
end

if not state or redis.call('HGET', request, 'token') ~= token
    or redis.call('HGET', request, 'day') ~= day then
    return reply('FAILED')
end
if operation == 'fail' then
    redis.call('ZREM', pending, token)
    redis.call('HSET', request, 'state', 'FAILED')
    return reply('FAILED')
end
if operation == 'complete' then
    if tonumber(redis.call('HGET', request, 'deadline')) <= now
        or not redis.call('ZSCORE', pending, token) or redis.call('EXISTS', quota) == 0 then
        redis.call('ZREM', pending, token)
        redis.call('HSET', request, 'state', 'FAILED')
        return reply('FAILED')
    end
    used = redis.call('INCR', quota)
    redis.call('ZREM', pending, token)
    redis.call('HSET', request, 'state', 'SUCCEEDED', 'text', translated, 'used', tostring(used))
    redis.call('SET', cache, translated, 'PX', cacheTtl)
    return reply('SUCCEEDED', used, redis.call('ZCARD', pending), day, resetAt, language, translated)
end
return reply('FAILED')
