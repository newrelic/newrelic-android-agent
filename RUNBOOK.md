> # Android Agent Run Book #

Deploys to staging and production are driven by the [Android Agent Runbook](https://mobile-team-build.pdx.vm.datanerd.us/view/AGENT%20-%20Android/job/Agent-Android-RunBook) Jenkins deploy job. This job will build and deploy the Android agent (current master branch) to either [staging](https://staging-rpm-admin.newrelic.com/admin/system_configurations) or [production](https://rpm-admin.newrelic.com/admin/system_configurations). 
The agent version number is specified in [gradle.properties](https://source.datanerd.us/mobile/android_agent/blob/master/gradle.properties#L4). 



## Deployment Steps ###
1. Update the [agent version number](https://source.datanerd.us/mobile/android_agent/blob/master/gradle.properties#L4) in the Git repo if this is an incremental deploy. 
Existing releases _may_ be updated, although there is risk that the new version will not be detected in customer's dev environemnts, an incremental release would be an _explicit_ update.
2. Manually run the [Android Agent Runbook](https://mobile-team-build.pdx.vm.datanerd.us/view/AGENT%20-%20Android/job/Agent-Android-RunBook) Jenkins deploy job. 
* You *must* manually select the `PROD_DEPLOY` option to push the artifacts maintained in the [agent releases repo](https://source.datanerd.us/mobile/releases_android_agent/tree/5.x)
 to the [public download site](https://download.newrelic.com/android_agent/ant/), as well as the [MavenCentral](https://mvnrepository.com/artifact/com.newrelic.agent.android/android-agent)
* Agent artifacts are pushed to [Sonatype (MavenCentral)](https://oss.sonatype.org/#stagingRepositories). However, the new repo must be `closed` and `released` before it becomes available to the public. At this time, this requires [human intervention](https://newrelic.atlassian.net/wiki/spaces/eng/pages/82378941/Android+Release+Process).

## Verification ##
Verification should be made on all deployed artifacts, but for brevity this description will focus on the agent JAR. 
```
All examples use verson 5.15.0 for reference. Replace that value with the deloyment version under test.
```
### Production Configuration ###
* Review the production [System Configuration](https://rpm-admin.newrelic.com/admin/system_configurations) for the agent. 
* Verify `android_agent_version` is set to the deployed version number. This value is used in new app setup ([LSD](https://rpm.newrelic.com/accounts/837973/mobile/setup)) page.

### Artifact validation ###
Validate deployed artifacts by checking the `Specification-Version` attribute. This is found in the JAR's `META-INF/MANIFEST.MF` file. 

Open the JAR using [JD_GUI](http://jd.benow.ca/) or similar tool. You can manually [explode](https://docs.oracle.com/javase/tutorial/deployment/jar/unpack.html) the JAR:
```
jar xf android-agent-5.15.0.jar
```  

Find the manifest, usually found in `./META-INF/MANIFEST.MF`: 
```
Manifest-Version: 1.0
Implementation-Title: Android Agent
Implementation-Version: 5.15.0
Built-Date: 2017-10-18T10:56:12-0700
Created-By: New Relic, Inc.
Specification-Version: 5.15.0-599
Implementation-Vendor: New Relic Inc.
```
The build number of the artifact will be reflected in the `Specification-Version`. Verify that `Built-Date` also reflects the time the agent was constructed.
 
### Releases Repo ###
Verify that artifacts have been populated in the [releases repo](https://source.datanerd.us/mobile/releases_android_agent/tree/5.x) for the deployed version. [ZIP](https://source.datanerd.us/mobile/releases_android_agent/tree/5.x/5.15.0/ant).

Download the ZIP file in the `ant` folder for the deployed release:
```
open https://source.datanerd.us/mobile/releases_android_agent/blob/5.x/5.15.0/ant/NewRelic_Android_Agent_5.15.0.zip
```

Explode the ZIP, and validate the agent JAR (`lib/newrelic.android.jar`) using the method described in [Artifact validation](#artifact-validation).

### Public download site ###
You should be able to download the ZIP from the public download site:
```
open https://download.newrelic.com/android_agent/ant/NewRelic_Android_Agent_5.15.0.zip
```
Once downloaded, validate the agent JAR using the method described in [Releases Repo](#releases-repo).

### Sonatype ###
Prior to `releasing` the deployed repo, drill down into the repo's contents, find `MANIFEST.MF` and verify the buld number as described in [Artifact validation](#artifact-validation)

You can also directly download the manifest, given the `Sonatype repo number` and deployment `build number`. 

```
open https://oss.sonatype.org/service/local/repositories/comnewrelic-1679/archive/com/newrelic/agent/android/android-agent/5.15.0/android-agent-5.15.0.jar/!/META-INF/MANIFEST.MF
```

After the repo has been made public (released),
* download the `New Relic Android Agent` JAR from the [MVN Repo](https://mvnrepository.com/artifact/com.newrelic.agent.android/android-agent), 
``` 
open http://central.maven.org/maven2/com/newrelic/agent/android/android-agent/5.15.0/android-agent-5.15.0.jar
```
* validate the agent JAR using the method described in [Artifact validation](#artifact-validation).

```
open https://bintray.com/kennr/maven/download_file?file_path=com%2Fnewrelic%2Fagent%2Fandroid%2Fagent-gradle-plugin%2F5.15.0%2Fagent-gradle-plugin-5.15.0.jar 
```
* validate the plugin JAR using a similar method as described in [Artifact validation](#artifact-validation). Use `agent-gradle-plugin` JAR instead of the agent JAR.


