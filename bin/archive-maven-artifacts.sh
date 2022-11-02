#!/usr/bin/env bash

source `dirname $0`/releases-repo.sh

[[ -d ${DIST} ]] || die "Can't access distribution artifacts: [${DIST}]"
[[ -d ${DIST}/maven ]] || die "Can't access maven distribution artifacts: [${DIST}/maven]"

find ${WORKSPACE}/dist/gradle-plugin -name "android-gradle-plugin-${AGENT_VERSION}.jar" || die "agent-gradle-plugin not found"
find ${WORKSPACE}/dist/maven -name "android-agent-${AGENT_VERSION}.jar" || die "android-agent not found"
find ${WORKSPACE}/dist/maven -name "class-rewriter-${AGENT_VERSION}.jar" || die "class-rewriter not found"

reset_workdir "maven"

setup_repo

warn_repo_overwrite "maven"

# Prep the directories for copying
cd_repo

# Set up the dirs
mkdir -p ${AGENT_VERSION}/maven/agent-gradle-plugin
mkdir -p ${AGENT_VERSION}/maven/android-agent
mkdir -p ${AGENT_VERSION}/maven/class-rewriter

cp -R ${WORKSPACE}/dist/gradle-plugin/agent-gradle-plugin-${AGENT_VERSION}* ${AGENT_VERSION}/maven/agent-gradle-plugin/
cp -R ${WORKSPACE}/dist/maven/android-agent-${AGENT_VERSION}* ${AGENT_VERSION}/maven/android-agent/
cp -R ${WORKSPACE}/dist/maven/class-rewriter-${AGENT_VERSION}* ${AGENT_VERSION}/maven/class-rewriter/

stamp_version_meta maven

git stage ${AGENT_VERSION}/maven/*

pop_workdir

update_repo "Maven"

