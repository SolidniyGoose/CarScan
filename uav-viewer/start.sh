#!/bin/sh
# Linux: положите бинарник Node.js >= 22 в runtime/node или установите node в системе
cd "$(dirname "$0")"
export HOST=127.0.0.1
export UAV_OPEN="${UAV_OPEN:-1}"
export PORT="${PORT:-3000}"
NODE=./runtime/node
[ -x "$NODE" ] || NODE=node
exec "$NODE" --disable-warning=ExperimentalWarning app/server.mjs
