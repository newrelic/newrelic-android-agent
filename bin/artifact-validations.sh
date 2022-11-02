#!/usr/bin/env bash

source $(dirname $0)/releases-repo.sh

SCRIPT_PATH=$(dirname $(which $0))

WGET="$(which wget) -q"
UNZIP="$(which unzip) -o -q"
JAVA="$(which java)"
JAR="$(which jar)"

##
# Uses a the passed JAR's MANIFEST to provide build metadata
##
validate_jar() {
    AGENT_JAR=${1:-android-agent-${AGENT_VERSION}.jar}

    [[ -r ${JAR} ]] || die "Can't read [${JAR}]"

    echo "validate_jar[${AGENT_JAR}]"

    ${JAR} -xf "${AGENT_JAR}"

    if [[ -e META-INF/MANIFEST.MF ]] ; then

        # Specification-Version: 5.15.1-602
        SPEC_VERSION=`grep 'Specification-Version: ' < META-INF/MANIFEST.MF | cut -c24- | tr -d '\r\n'`

        SPEC_BUILD=${SPEC_VERSION##*-}
        SPEC_VERSION=${SPEC_VERSION%-*}

        echo "SPEC_VERSION[${SPEC_VERSION}]"
        [[ "${SPEC_VERSION}" != "${AGENT_VERSION}" ]] && die "JAR version[$SPEC_VERSION] != Reported version[$VERSION]"

        echo "SPEC_BUILD[${SPEC_BUILD}]"
        [[ "${SPEC_BUILD}" != "${AGENT_BUILD}" ]] && die "JAR build[${AGENT_BUILD}] != Reported build[$SPEC_BUILD]"

        # Built-Date: 2017-10-18T12:38:17-0700
        BUILT_DATE=`grep 'Built-Date: ' < META-INF/MANIFEST.MF | cut -c13- | tr -d '\r\n'`
        echo "BUILT_DATE[${BUILT_DATE}]"

        echo
    else
        die "Unable to parse manifest from [${AGENT_JAR}]"
    fi  
}

##
# Uses a built-in reporter to provide build metadata
##
validate_agentJar() {
    AGENT_JAR=${1:-android-agent-${AGENT_VERSION}.jar}
    [ -r ${AGENT_JAR} ] || die "Can't read [${AGENT_JAR}]"

    echo "validate_agentJar[${AGENT_JAR}]"

    ${JAVA} -classpath "${AGENT_JAR}" "com.newrelic.agent.android.util.AgentBuildOptionsReporter" > jar_metadata.log

    AGENT_JAR_VERSION=`grep 'Agent version' < jar_metadata.log`
    AGENT_UNITY_FLAG=`grep 'Unity instrumentation:' < jar_metadata.log`

    AGENT_JAR_VERSION=${AGENT_JAR_VERSION#Agent version: }
    IS_UNITY=${AGENT_UNITY_FLAG#Unity instrumentation: }

    # cat jar_metadata.log
    echo "AGENT_JAR_VERSION[${AGENT_JAR_VERSION}]"
    echo "UNITY[${IS_UNITY}]"

    [ "${AGENT_JAR_VERSION}" != "${AGENT_VERSION}" ] && die "Agent JAR version [${AGENT_JAR_VERSION}] != version param [${AGENT_VERSION}]"
    echo
}

validate_agentZip() {
    ${UNZIP} "${1}"

    echo "validate_agentZip[${1}]"

    validate_agentJar "newrelic-android-${AGENT_VERSION}/lib/newrelic.android.jar"
    validate_jar "newrelic-android-${AGENT_VERSION}/lib/newrelic.android.jar"
}

##
# validate_releases_repo:
#   Called to validate the committed release artifacts for the releases repo, usually after a deploy build
#
# Artifacts:
#   ZIP file containing newrelic.agent.jar and class.rewriter.jar
#   MAVEN artifacts to be deployed to Jcenter
##
validate_releases_repo() {
    echo "## "
    echo "# validate_releases_repo: Called to validate the committed release artifacts for the releases repo, usually after a deploy build"
    echo "#   ZIP file containing newrelic.agent.jar and class.rewriter.jar"
    echo "#   MAVEN artifacts to be deployed to Jcenter"
    echo "##"

    setup_repo

    reset_workdir "repo"
    ARTIFACT="${REPO_PATH}/${AGENT_VERSION}/ant/NewRelic_Android_Agent_${AGENT_VERSION}.zip"
    validate_agentZip "${ARTIFACT}"
    [ "${AGENT_JAR_VERSION}" != "${AGENT_VERSION}" ] && die "Agent JAR version [${AGENT_JAR_VERSION}] != version param [${AGENT_VERSION}]"
    [ "${IS_UNITY}" == "YES" ] && die "Agent JAR [${AGENT_VERSION}.${AGENT_BUILD}] was built for Unity"
    ARTIFACT="${REPO_PATH}/${AGENT_VERSION}/maven/android-agent/android-agent-${AGENT_VERSION}.jar"
    validate_jar "${ARTIFACT}"
    popd 2>&1 > /dev/null
}

##
# validate_downloads_stage:
#   Called to validate internal NR download site artifacts, usually after a staging deploy
#
# Artifacts:
#   ZIP file containing newrelic.agent.jar and class.rewriter.jar
#   MAVEN artifacts to be deployed to Jcenter
##
validate_downloads_stage() {
    echo "##"
    echo "# validate_downloads_stage: Called to validate internal NR download site artifacts, usually after a staging deploy"
    echo "#   ZIP file containing newrelic.agent.jar and class.rewriter.jar"
    echo "#   MAVEN artifacts to be deployed to Jcenter"
    echo "##"

    # STAGE: https://download.newrelic.com/android_agent/ant-v5/NewRelic_Android_Agent_${AGENT_VERSION}.zip
    reset_workdir "downloads/stage"
    ARTIFACT="NewRelic_Android_Agent_${AGENT_VERSION}.zip"
    URL="https://download.newrelic.com/android_agent/ant-v5/NewRelic_Android_Agent_${AGENT_VERSION}.zip"
    echo "Validate download[${URL}]"
    ${WGET} "${URL}" -O "${ARTIFACT}"
    [ -s ${ARTIFACT} ] || die "Could not download [${URL}]"
    validate_agentZip "${ARTIFACT}"
    [ "${AGENT_JAR_VERSION}" != "${AGENT_VERSION}" ] && die "Agent JAR version [${AGENT_JAR_VERSION}] != version param [${AGENT_VERSION}]"
    [ "${IS_UNITY}" == "YES" ] && die "Agent JAR [${AGENT_VERSION}.${AGENT_BUILD}] was built for Unity"
    popd 2>&1 > /dev/null

    # STAGE: https://download.newrelic.com/android_agent/jcenter-staging/${AGENT_VERSION}
    reset_workdir "publish/maven/stage"
    ARTIFACT="android-agent-${AGENT_VERSION}.jar"
    URL="https://download.newrelic.com/android_agent/jcenter-staging/${AGENT_VERSION}/android-agent/android-agent-${AGENT_VERSION}.jar"
    echo "Validate download[${URL}]"
    ${WGET} "${URL}" -O "${ARTIFACT}"
    [ -s ${ARTIFACT} ] || die "Could not download [${URL}]"
    validate_jar "${ARTIFACT}"
    popd 2>&1 > /dev/null
}

##
# validate_downloads_prod:
#   Called to validate public download site artifacts, usually after a prod deploy
#
# Artifacts:
#   ZIP file containing newrelic.agent.jar and class.rewriter.jar
#   MAVEN artifacts deployed to Jcenter
##
validate_downloads_prod() {
    echo "##"
    echo "# validate_downloads_prod: Called to validate public download site artifacts, usually after a prod deploy"
    echo "#   ZIP file containing newrelic.agent.jar and class.rewriter.jar"
    echo "#   MAVEN artifacts deployed to Jcenter"
    echo "##"

    # PROD: https://download.newrelic.com/android_agent/ant/NewRelic_Android_Agent_${AGENT_VERSION}.zip
    reset_workdir "downloads/prod"
    ARTIFACT="NewRelic_Android_Agent_${AGENT_VERSION}.zip"
    URL="https://download.newrelic.com/android_agent/ant/NewRelic_Android_Agent_${AGENT_VERSION}.zip"
    echo "Validate download[${URL}]"
    ${WGET} "${URL}" -O "${ARTIFACT}"
    [ -s ${ARTIFACT} ] || die "Could not download [${URL}]"
    validate_agentZip "${ARTIFACT}"
    [ "${AGENT_JAR_VERSION}" != "${AGENT_VERSION}" ] && die "Agent JAR version [${AGENT_JAR_VERSION}] != version param [${AGENT_VERSION}]"
    [ "${IS_UNITY}" == "YES" ] && die "Agent JAR [${AGENT_VERSION}.${AGENT_BUILD}] was built for Unity"
    popd 2>&1 > /dev/null

    # PROD: https://download.newrelic.com/android_agent/jcenter/${AGENT_VERSION}
    ARTIFACT="android-agent-${AGENT_VERSION}.jar"
    reset_workdir "publish/maven/prod"
    URL="https://download.newrelic.com/android_agent/jcenter/${AGENT_VERSION}/android-agent/android-agent-${AGENT_VERSION}.jar"
    echo "Validate download[${URL}]"
    ${WGET} "${URL}" -O "${ARTIFACT}"
    validate_jar "android-agent-${AGENT_VERSION}.jar"
    popd 2>&1 > /dev/null
}

##
# validate_sonatype_stage:
#   Called to validate MAVEN artifacts deployed to Sonatype snapshot (requires repo #)
#
# Artifact URLs:
#   https://oss.sonatype.org/service/local/repositories/comnewrelic-${SNAPSHOT}/content/com/newrelic/agent/android/android-agent/${AGENT_VERSION}/android-agent-${AGENT_VERSION}.jar
#
validate_sonatype_stage() {
    echo "##"
    echo "# validate_sonatype_stage: Sonatype staging repo artifacts"
    echo "#   MAVEN artifacts deployed to Sonatype snapshot (requires repo#)"
    echo "##"

    [ -z ${SONA_SNAPSHOT} ] && die "Requires Sonatype snapshot repo #"
    reset_workdir "sonatype/stage"
    ARTIFACT="android-agent-${AGENT_VERSION}.jar"
    URL="https://oss.sonatype.org/service/local/repositories/comnewrelic-${SONA_SNAPSHOT}/content/com/newrelic/agent/android/android-agent/${AGENT_VERSION}/android-agent-${AGENT_VERSION}.jar"
    echo "Validate Sonatype artifact [${URL}]"
    ${WGET} "${URL}" -O ${ARTIFACT}
    [ -s ${ARTIFACT} ] || die "Could not download [${URL}]"
    validate_jar "${ARTIFACT}"
    popd 2>&1 > /dev/null
}


##
# validate_sonatype_prod: Sonatype prod (released) repo artifacts
#   MAVEN artifacts deployed to Sonatype snapshot repo (requires version# or 'LATEST')
#
# Artifact URLs:
#   http://central.maven.org/maven2/com/newrelic/agent/android/android-agent/${AGENT_VERSION}/android-agent-${AGENT_VERSION}.jar
##
validate_sonatype_prod() {
    echo "##"
    echo "# validate_sonatype_prod: Sonatype prod (released) repo artifacts"
    echo "#   MAVEN artifacts deployed to Sonatype snapshot repo (requires version# or 'LATEST')"
    echo "##"

    reset_workdir "sonatype/prod"
    ARTIFACT="android-agent-${AGENT_VERSION}.jar"
    URL="http://oss.sonatype.org/service/local/repositories/releases/content/com/newrelic/agent/android/android-agent/${AGENT_VERSION}/android-agent-${AGENT_VERSION}.jar"
    echo "Validate Sonatype artifact [${URL}]"
    ${WGET} "${URL}" -O "${ARTIFACT}"
    [ -s ${ARTIFACT} ] || die "Could not download [${URL}]"
    validate_jar "${ARTIFACT}"

    popd 2>&1 > /dev/null
}


[ -z ${AGENT_VERSION} ] && die "Version not specified"
[ -z ${AGENT_BUILD} ] && read_version_meta ant
[ -z ${AGENT_BUILD} ] && echo "[WARNING] Cannot validate deploy without a build number"

echo Validating agent release ${AGENT_VERSION}:

