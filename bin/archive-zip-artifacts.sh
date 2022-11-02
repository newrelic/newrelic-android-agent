#!/usr/bin/env bash

source `dirname $0`/artifact-validations.sh

[ -d ${DIST} ] || die "Can't access distribution artifacts: [${DIST}]"

ARTIFACT="${WORKSPACE}/dist/NewRelic_Android_Agent_${AGENT_VERSION}.zip"

[ -r "${ARTIFACT}" ] || die "${ARTIFACT} not found"

reset_workdir "ant"
validate_agentZip "${ARTIFACT}"

[ "${IS_UNITY}" == "YES" ] &&  die " Unity build flag == YES."

# pull the releases repo
setup_repo

# Prep the directories for copying
cd_repo

warn_repo_overwrite "ant"

# Set up the dirs
mkdir -p ${AGENT_VERSION}/ant

cp -R "${ARTIFACT}" ${AGENT_VERSION}/ant

stamp_version_meta ant

git stage ${AGENT_VERSION}/ant/*

pop_workdir

update_repo "Ant"

