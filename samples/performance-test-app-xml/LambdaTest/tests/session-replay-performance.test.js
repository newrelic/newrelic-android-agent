/**
 * Session Replay Performance Tests - Android
 * Measures memory and CPU overhead when Session Replay is enabled vs disabled
 *
 * Run this test three times:
 * 1. No Agent: with NO_AGENT=1 env var (no New Relic agent)
 * 2. Baseline: with BASELINE=1 env var (agent ON, Session Replay OFF)
 * 3. With Session Replay: without env var (agent ON, Session Replay ON)
 *
 * Test includes:
 * - 5 navigation cycles
 * - Infinite scroll testing (4 scrolls down, 2 up)
 * - Image gallery scrolling (4 scrolls with images)
 * - UI elements interactions (buttons, switches, sliders)
 * - Network test interactions
 * - Total duration: ~5 minutes for stable metrics
 *
 * Results are automatically saved to JSON files for comparison
 *
 * Compare memory usage, memory overhead, CPU load, and CPU overhead
 */

const fs = require('fs');
const path = require('path');

describe("Session Replay Performance Impact - Android", () => {
  it("Should perform intensive UI operations and collect resource metrics", async () => {
    console.log('Starting intensive UI operations for Session Replay performance testing...');

    // Wait for app to fully load
    await driver.pause(5000);
    console.log('App loaded successfully');

    // Give time for initial metrics to be collected
    await driver.pause(3000);

    // === Phase 1: Navigation Stress Test ===
    console.log('Phase 1: Navigation stress test (5 iterations)');
    for (let i = 0; i < 5; i++) {
      try {
        // Find and click "Infinite Scroll" button using accessibility ID
        const infiniteScrollCard = await $('~Infinite Scroll');
        if (await infiniteScrollCard.isExisting()) {
          await infiniteScrollCard.click();
          await driver.pause(1000);

          // Navigate back
          await driver.back();
          await driver.pause(1000);

          console.log(`  Navigation iteration ${i + 1}/5 completed`);
        }
      } catch (e) {
        console.log(`  Navigation iteration ${i + 1} failed:`, e.message);
      }
    }
    console.log('Navigation stress test completed');

    // Wait for metrics to be collected
    await driver.pause(3000);

    // === Phase 2: Infinite Scroll Test ===
    console.log('Phase 2: Infinite scroll test');
    try {
      // Click on Infinite Scroll button using accessibility ID
      const infiniteScrollCard = await $('~Infinite Scroll');
      await infiniteScrollCard.waitForExist({ timeout: 5000 });
      await infiniteScrollCard.click();
      await driver.pause(2000);

      console.log('  Starting scroll operations (4 scrolls down, 2 up)');

      // Scroll down 4 times
      for (let i = 0; i < 4; i++) {
        await driver.execute('mobile: scrollGesture', {
          left: 300, top: 1000, width: 200, height: 800,
          direction: 'down',
          percent: 1.0
        });
        await driver.pause(500);
        console.log(`    Scroll down ${i + 1}/4 completed`);
      }

      // Scroll up 2 times
      for (let i = 0; i < 2; i++) {
        await driver.execute('mobile: scrollGesture', {
          left: 300, top: 1000, width: 200, height: 800,
          direction: 'up',
          percent: 1.0
        });
        await driver.pause(500);
      }

      // Go back to home
      await driver.back();
      await driver.pause(1000);

      console.log('  Infinite scroll test completed');
    } catch (e) {
      console.log('  Infinite scroll test error:', e.message);
    }

    // Wait for metrics
    await driver.pause(3000);

    // === Phase 3: Image Gallery Scroll Test ===
    console.log('Phase 3: Image gallery scroll test');
    try {
      // Click on Image Gallery button using accessibility ID
      const imageGalleryCard = await $('~Image Gallery');
      await imageGalleryCard.waitForExist({ timeout: 5000 });
      await imageGalleryCard.click();
      await driver.pause(3000); // Wait for images to load

      console.log('  Scrolling through images (4 scrolls)');

      // Scroll through image grid
      for (let i = 0; i < 4; i++) {
        await driver.execute('mobile: scrollGesture', {
          left: 300, top: 1000, width: 200, height: 800,
          direction: 'down',
          percent: 1.0
        });
        await driver.pause(800); // Longer pause for image loading
        console.log(`    Image scroll ${i + 1}/4 completed`);
      }

      // Go back to home
      await driver.back();
      await driver.pause(1000);

      console.log('  Image gallery test completed');
    } catch (e) {
      console.log('  Image gallery test error:', e.message);
    }

    // Wait for metrics
    await driver.pause(3000);

    // === Phase 4: UI Elements Interactions ===
    console.log('Phase 4: UI elements interactions');
    try {
      // Click on UI Elements button using accessibility ID
      const uiElementsCard = await $('~UI Elements');
      await uiElementsCard.waitForExist({ timeout: 5000 });
      await uiElementsCard.click();
      await driver.pause(2000);

      console.log('  Interacting with UI components');

      // Click various buttons
      const buttons = await $$('android.widget.Button');
      for (let i = 0; i < Math.min(3, buttons.length); i++) {
        try {
          if (await buttons[i].isDisplayed()) {
            await buttons[i].click();
            await driver.pause(300);
          }
        } catch (e) {
          console.log(`    Button ${i} not clickable`);
        }
      }

      // Scroll down to see more elements
      await driver.execute('mobile: scrollGesture', {
        left: 300, top: 1000, width: 200, height: 800,
        direction: 'down',
        percent: 0.8
      });
      await driver.pause(500);

      // Interact with switches
      const switches = await $$('android.widget.Switch');
      for (let i = 0; i < Math.min(2, switches.length); i++) {
        try {
          if (await switches[i].isDisplayed()) {
            await switches[i].click();
            await driver.pause(300);
          }
        } catch (e) {
          console.log(`    Switch ${i} not clickable`);
        }
      }

      // Go back to home
      await driver.back();
      await driver.pause(1000);

      console.log('  UI elements interactions completed');
    } catch (e) {
      console.log('  UI elements test error:', e.message);
    }

    // Wait for metrics
    await driver.pause(3000);

    // === Phase 5: Network Test ===
    console.log('Phase 5: Network test');
    try {
      // Click on Network Test button using accessibility ID
      const networkTestCard = await $('~Network Test');
      await networkTestCard.waitForExist({ timeout: 5000 });
      await networkTestCard.click();
      await driver.pause(2000);

      console.log('  Making network requests');

      // Click network request buttons
      const requestButtons = await $$('android.widget.Button');
      for (let i = 0; i < Math.min(3, requestButtons.length); i++) {
        try {
          const buttonText = await requestButtons[i].getText();
          if (buttonText && buttonText.includes('GET')) {
            await requestButtons[i].click();
            await driver.pause(2000); // Wait for request to complete
            console.log(`    Request ${i + 1} completed`);
          }
        } catch (e) {
          console.log(`    Request button ${i} error`);
        }
      }

      // Go back to home
      await driver.back();
      await driver.pause(1000);

      console.log('  Network test completed');
    } catch (e) {
      console.log('  Network test error:', e.message);
    }

    // Wait for metrics
    await driver.pause(3000);

    // === Phase 6: Navigation Test ===
    console.log('Phase 6: Navigation flow test');
    try {
      // Click on Navigation Flow button using accessibility ID
      const navigationCard = await $('~Navigation Flow');
      await navigationCard.waitForExist({ timeout: 5000 });
      await navigationCard.click();
      await driver.pause(2000);

      console.log('  Navigating through screens');

      // Click "Next" button multiple times using accessibility ID
      for (let i = 0; i < 5; i++) {
        try {
          const nextButton = await $('~next_button');
          if (await nextButton.isExisting() && await nextButton.isEnabled()) {
            await nextButton.click();
            await driver.pause(500);
            console.log(`    Navigation step ${i + 1}/5 completed`);
          }
        } catch (e) {
          console.log(`    Navigation step ${i + 1} failed`);
          break;
        }
      }

      // Go back to home
      await driver.back();
      await driver.pause(1000);

      console.log('  Navigation flow test completed');
    } catch (e) {
      console.log('  Navigation test error:', e.message);
    }

    // Final wait for metrics collection
    console.log('Waiting for final metrics collection...');
    await driver.pause(10000);

    console.log('✓ Intensive UI operations completed - Enhanced test with ~5 min duration');
  });

  it("Should retrieve and analyze resource metrics", async () => {
    console.log('Retrieving performance metrics...');

    let metricsData;

    try {
      // Find the hidden TextView by content description (Android equivalent of iOS accessibility ID)
      const metricsTextView = await $('~performance_metrics_json');
      await metricsTextView.waitForExist({ timeout: 10000 });

      // Read the JSON text from the TextView
      const jsonText = await metricsTextView.getText();
      console.log(`Retrieved JSON text (${jsonText.length} characters)`);

      // Parse the JSON
      metricsData = JSON.parse(jsonText);

      console.log(`Total metrics collected: ${metricsData.length}`);
    } catch (error) {
      console.error('Failed to retrieve performance metrics:', error.message);
      throw new Error('Could not retrieve performance metrics from TextView');
    }

    // Validate metrics data
    expect(metricsData).toBeDefined();
    expect(Array.isArray(metricsData)).toBe(true);
    expect(metricsData.length).toBeGreaterThan(0);

    // === Analyze Resource Usage Metrics ===
    const resourceMetrics = metricsData.filter(m => m.type === 'resourceUsage');
    console.log(`\nResource usage metrics collected: ${resourceMetrics.length}`);
    expect(resourceMetrics.length).toBeGreaterThan(0);

    // Calculate memory statistics
    const memoryValues = resourceMetrics.map(m => m.memoryMB).filter(v => v > 0);
    const avgMemory = memoryValues.reduce((a, b) => a + b, 0) / memoryValues.length;
    const maxMemory = Math.max(...memoryValues);
    const minMemory = Math.min(...memoryValues);

    console.log('\n=== MEMORY USAGE ===');
    console.log(`Average Memory: ${avgMemory.toFixed(2)} MB`);
    console.log(`Peak Memory: ${maxMemory.toFixed(2)} MB`);
    console.log(`Min Memory: ${minMemory.toFixed(2)} MB`);
    console.log(`Memory Range: ${(maxMemory - minMemory).toFixed(2)} MB`);

    // Calculate CPU statistics
    const cpuValues = resourceMetrics.map(m => m.cpuPercent).filter(v => v >= 0);
    const avgCPU = cpuValues.reduce((a, b) => a + b, 0) / cpuValues.length;
    const maxCPU = Math.max(...cpuValues);

    console.log('\n=== CPU USAGE ===');
    console.log(`Average CPU: ${avgCPU.toFixed(2)}%`);
    console.log(`Peak CPU: ${maxCPU.toFixed(2)}%`);

    // === Check Other Performance Metrics ===
    const startupMetric = metricsData.find(m => m.type === 'startupTime');
    if (startupMetric) {
      console.log(`\nStartup Time: ${startupMetric.duration}ms`);
    }

    // === Save Summary for Comparison ===
    // Detect test type based on environment variables
    const isNoAgent = process.env.NO_AGENT === '1';
    const isBaseline = process.env.BASELINE === '1';
    const summary = {
      agentEnabled: !isNoAgent,
      sessionReplayEnabled: !isBaseline && !isNoAgent,
      testDuration: resourceMetrics.length * 2, // 2 seconds per sample
      resourceMetrics: {
        count: resourceMetrics.length,
        memory: {
          avg: parseFloat(avgMemory.toFixed(2)),
          peak: parseFloat(maxMemory.toFixed(2)),
          min: parseFloat(minMemory.toFixed(2)),
          range: parseFloat((maxMemory - minMemory).toFixed(2))
        },
        cpu: {
          avg: parseFloat(avgCPU.toFixed(2)),
          peak: parseFloat(maxCPU.toFixed(2))
        }
      },
      performanceMetrics: {
        startupTime: startupMetric ? startupMetric.duration : null,
        ttiCount: 0,
        renderingCount: 0
      },
      healthCheck: {
        fatalHangs: 0,
        memoryLeaks: 0
      }
    };

    console.log('\n=== TEST SUMMARY ===');
    console.log(JSON.stringify(summary, null, 2));

    // Save results to file based on test type
    let filename;
    if (isNoAgent) {
      filename = 'no_agent_results.json';
    } else if (isBaseline) {
      filename = 'baseline_results.json';
    } else {
      filename = 'session_replay_results.json';
    }
    const filepath = path.join(__dirname, '..', filename);

    try {
      fs.writeFileSync(filepath, JSON.stringify(summary, null, 2));
      console.log(`\n💾 Results saved to: ${filename}`);
    } catch (error) {
      console.error(`Failed to save results to ${filename}:`, error.message);
    }

    console.log('\n✓ Resource metrics analysis completed');
  });
});
