#!/usr/bin/env bash

CURL=`which curl`
WEBHOOK_URL="T02D34WJD/B0Q64TYH0/CMFOdH0Xa2cqHc7asUV0qdDE"

# Post a message to Slack
echo curl -X POST -H 'Content-type: application/json' \
--data '{"attachments": [{"fallback": "Android agent version '"${AGENT_VERSION}"' has been deployed to Sonatype repo ####.", "color": "#A020F0", "pretext": "Android agent version '"${AGENT_VERSION}"' has been deployed to Sonatype repo ####.","fields": [{"title": "Login to Sonatype Repository Manager: ", "value": "You must go to the Sonatype Repository Manager and Close the repo #### before it can be used as a SNAPSHOT. For production deployments, the repo must then be Released.", "short": false}, {"title": "Sonatype", "value": "https://oss.sonatype.org", "short": false}]}],"channel": "#mobile-notifications", "username": "Jenkins"}' \
https://hooks.slack.com/services/${WEBHOOK_URL}

# Post a message to Slack
echo curl -X POST -H 'Content-type: application/json' \
--data '{"attachments": [{"fallback": "Android agent version '"${AGENT_VERSION}"' has been deployed to Sonatype repo ####.", "color": "#A020F0", "pretext": "Android agent version '"${AGENT_VERSION}"' has been deployed to Sonatype repo ####.","fields": [{"title": "Login to Sonatype Repository Manager: ", "value": "You will have to go to the Sonatype Repository Manager and Close repo #### before it can be used as a SNAPSHOT. For production deployments, the repo must later be Released.", "short": false}, {"title": "Sonatype", "value": "https://oss.sonatype.org", "short": false}]}],"channel": "#mobile-notifications", "username": "Jenkins"}' \
https://hooks.slack.com/services/${WEBHOOK_URL}

# Post message to Slack
echo curl -X POST -H 'Content-type: application/json' \
--data '{"attachments": [{"fallback": "Android agent version '"${AGENT_VERSION}"' has been deployed to the production servers.", "color": "#A020F0", "pretext": "Android agent version '"${AGENT_VERSION}"' has been deployed to the production servers.", "fields": [{"title": "Ant version: ", "value": "https://download.newrelic.com/android_agent/ant/NewRelic_Android_Agent_'"${AGENT_VERSION}"'.zip", "short": false}]}], "channel": "#mobile-notifications", "username": "Jenkins"}' \
https://hooks.slack.com/services/${WEBHOOK_URL}

echo curl -X POST -H 'Content-type: application/json' \
--data '{"attachments": [{"fallback": "Android agent version '"${AGENT_VERSION}"' has been deployed to the production servers.", "color": "#A020F0", "pretext": "Android agent version '"${AGENT_VERSION}"' has been deployed to the production servers.", "fields": [{"title": "Ant version: ", "value": "https://download.newrelic.com/android_agent/ant/NewRelic_Android_Agent_'"${AGENT_VERSION}"'.zip", "short": false}]}], "channel": "#gts-mobile", "username": "Jenkins"}' \
https://hooks.slack.com/services/${WEBHOOK_URL}