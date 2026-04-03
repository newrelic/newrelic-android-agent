/**
 * WebDriverIO Configuration - Android Session Replay (Agent ON, SR ON) - XML Layouts
 *
 * Run with: npx wdio LambdaTest/wdio-config-android-session-replay.js
 */

exports.config = {
  user: process.env.LT_USERNAME || 'YOUR_LAMBDATEST_USERNAME',
  key: process.env.LT_ACCESSKEY || 'YOUR_LAMBDATEST_ACCESS_KEY',

  updateJob: false,
  specs: [
    './tests/session-replay-performance.test.js'
  ],
  exclude: [],

  capabilities: [{
    platformName: 'Android',
    'appium:platformVersion': '14',
    'appium:deviceName': 'Pixel 8',
    'appium:isRealMobile': true,
    'appium:app': process.env.LT_APP_ID || 'lt://YOUR_SESSION_REPLAY_APP_ID',
    'appium:deviceOrientation': 'PORTRAIT',
    'appium:console': true,
    'appium:network': false,
    'appium:visual': true,
    'appium:devicelog': true,

    // LambdaTest specific capabilities
    'lt:options': {
      w3c: true,
      platformName: 'Android',
      deviceName: 'Pixel 8',
      platformVersion: '14',
      isRealMobile: true,
      build: 'Android Agent Performance Test - XML Layouts - Session Replay',
      name: 'Session Replay Performance - XML - Full Test',
      video: true,
      console: true,
      network: false,
      devicelog: true
    }
  }],

  logLevel: 'info',
  bail: 0,
  baseUrl: '',
  waitforTimeout: 30000,
  connectionRetryTimeout: 120000,
  connectionRetryCount: 3,
  path: '/wd/hub',
  hostname: 'mobile-hub.lambdatest.com',
  port: 80,

  framework: 'jasmine',
  jasmineOpts: {
    defaultTimeoutInterval: 600000, // 10 minutes for test completion
  },
  reporters: ['spec'],

  onPrepare: function (config, capabilities) {
    console.log('\n==============================================');
    console.log('📱 Android Performance Test - SESSION REPLAY (XML)');
    console.log('Agent: ON | Session Replay: ON');
    console.log('==============================================\n');
  },

  onComplete: function(exitCode, config, capabilities, results) {
    console.log('\n==============================================');
    console.log('✓ Session Replay test completed');
    console.log('Results saved to: session_replay_results.json');
    console.log('==============================================\n');
  }
}
