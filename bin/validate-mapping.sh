# !/bin/bash

#
## Validate buildIds were put everywhere they should
## Run this from root of test app after building
#

die() { 
    echo "FAIL: $*" 
}

echo "[Maps]"
maps=$(find . -name mapping.txt)
[[ -z "$maps" ]] && die "NO maps found"   
echo "$maps"

echo "[Tags]"
tags=$(find . -name mapping.txt -exec grep NR_BUILD_ID {} \;)
[[ -z "$tags" ]] && die "Mapping was not tagged with a build ID!"
echo "$tags"

echo "[NewRelicConfig]"
confs=$(find . -name NewRelicConfig.java -exec grep "BUILD_ID =" {} \; )
[[ -z "$confs" ]] && die "Build ID not stamped in NewRelicConfig.java"
echo "$confs"

exit 0
