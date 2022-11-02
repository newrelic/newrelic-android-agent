#!/usr/bin/env bash

SCRIPT_PATH=$(dirname $0)
SCRIPT_PATH=`(cd ${SCRIPT_PATH} ; pwd)`
WORKSPACE=${WORKSPACE:-`(cd ${SCRIPT_PATH}/.. ; pwd)`}
DIST="${DIST:-${WORKSPACE}/dist}"
REPO_ROOT="${REPO_ROOT:-releases}"
REPO_DIR="${REPO_DIR:-${SCRIPT_PATH}}"
REPO_BRANCH=${REPO_BRANCH:-${RELEASE_BRANCH}}
REPO_PATH="${REPO_PATH:-${REPO_DIR}/${REPO_ROOT}}"
BUILD_META_ROOT="${REPO_PATH}/${AGENT_VERSION}/xxx/build.meta"
TOOLSET=(java jar wget aws unzip curl)

die() {
    echo
    echo "[ERROR]: $1"
    echo
    exit 1
}

cd_repo() {
    pushd "${REPO_PATH}" 2>&1 > /dev/null || die "Can't open releases repo [${REPO_PATH}]"
}

reset_workdir() {
    workdir="${SCRIPT_PATH}/$1"
    rm -rf "$workdir"
    mkdir -p "$workdir"
    pushd "$workdir"    2>&1 > /dev/null
}

pop_workdir() {
	popd 2>&1 >/dev/null
}

setup_repo() {
    mkdir -p "${REPO_DIR}" 2>&1 > /dev/null
    pushd "${REPO_DIR}" 2>&1 > /dev/null

    [ -d ${REPO_ROOT} ] || git clone git@source.datanerd.us:mobile/releases_android_agent.git -b ${REPO_BRANCH} ${REPO_ROOT}
    cd "${REPO_ROOT}" || die "Can't open release repo"

    git checkout ${REPO_BRANCH}
    git rebase --abort
    git clean -fd
    git reset --hard HEAD
    git pull

	pop_workdir
}

update_repo() {
    cd_repo

    # Add to GHE
    git pull
    git status
    git commit -m "Releasing ${1:-} Version ${AGENT_VERSION}"
    git status
    git push origin ${REPO_BRANCH}

	pop_workdir
}

reset_repo() {
    pushd "${PATH_DIR}" 2>&1 > /dev/null
    rm -rf ${REPO_ROOT}
    pop_workdir
}

stamp_version_meta() {
    BUILD_META=`echo ${BUILD_META_ROOT} | sed -e "s/xxx/${1:-}/g"`

    echo AGENT_VERSION=${AGENT_VERSION}-${AGENT_BUILD} > ${BUILD_META}
    echo MD5=`tar --exclude "*.meta" -cf - ${AGENT_VERSION}/$1 | md5` >> ${BUILD_META}
}

read_version_meta() {
    BUILD_META=`echo ${BUILD_META_ROOT} | sed -e "s/xxx/${1:-}/g"`

    [[ -r "${BUILD_META}" ]] || echo "[WARNING] Build meta file [${BUILD_META}] not found!!"
    [[ -r "${BUILD_META}" ]] && echo "Using build meta file [${BUILD_META}]"

    VERSION=`grep 'AGENT_VERSION=' < ${BUILD_META} | cut -c15- | tr -d '\r\n'`

    AGENT_VERSION=${VERSION%-*}
    AGENT_BUILD=${VERSION#*-}

    echo "[INFO] Using AGENT_VERSION[${AGENT_VERSION}] AGENT_BUILD[${AGENT_BUILD}] from ${BUILD_META}"
}

warn_repo_overwrite() {
    find ${REPO_PATH}/${AGENT_VERSION}/$1 && echo "[WARNING] Existing artifacts detected!!"
}

verifyRequiredTool() {
    local tool="$(which $1)"
    [[ -n "$tool" && -x "$tool" ]] || echo "Tool[$1] does not exist or is not executable!"
    return 0
}


[[ -z ${AGENT_VERSION} ]] && die "Version not specified"
[[ -d ${WORKSPACE} ]] || die "Can't access project workspace: [${WORKSPACE}]"

echo "PWD[${PWD}]"
echo "WORKSPACE[${WORKSPACE}]"
echo "REPO_DIR=[${REPO_DIR}]"
echo "REPO_PATH=[${REPO_PATH}]"

echo "Verify toolset[${TOOLSET[*]}]"
for tool in "${TOOLSET[@]}"; do
    verifyRequiredTool $tool
done

