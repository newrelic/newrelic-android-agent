#!/usr/bin/env bash

source `dirname $0`/artifact-validations.sh

setup_repo

AWS="$(which aws)"

##
# Validate artifacts in releases repo
#
[[ -z ${AGENT_BUILD} ]] && read_version_meta ant
validate_releases_repo

##
# Move artifacts from the release repo to the internal S3 location
##
echo Deploying Android Agent ${AGENT_VERSION}.${AGENT_BUILD} artifacts to staging:

ARTIFACT=${REPO_PATH}/${AGENT_VERSION}/ant/NewRelic_Android_Agent_${AGENT_VERSION}.zip

# Copies JAR zipfile as Ant artifact
${AWS} s3 cp ${ARTIFACT} s3://nr-downloads-main/android_agent/ant-v5/

# Copies Maven/Gradle plugins artifacts
${AWS} s3 cp --recursive ${REPO_PATH}/${AGENT_VERSION}/maven s3://nr-downloads-main/android_agent/jcenter-staging/${AGENT_VERSION}/

##
# Validate S3 locations
##
[[ -z ${AGENT_BUILD} ]] && die "Cannot validate deploy without a build number"
validate_downloads_stage
