export CL_HIGHWAY_ENABLED=true
. scripts/highway-env.sh
CL_HIGHWAY_INIT_ROUND_EXPONENT=12 make node-0/up
CL_HIGHWAY_INIT_ROUND_EXPONENT=12 make node-1/up
CL_HIGHWAY_INIT_ROUND_EXPONENT=12 make node-2/up

