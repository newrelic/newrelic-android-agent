#!/usr/bin/env node

/**
 * Session Replay Overhead Comparison Script
 *
 * Compares performance metrics from baseline (Session Replay OFF) vs
 * Session Replay enabled (Session Replay ON) test runs.
 *
 * Usage:
 *   node compare-session-replay-overhead.js <baseline.json> <session-replay.json>
 *
 * The JSON files should contain the test summary output from the test runs.
 */

const fs = require('fs');
const path = require('path');

// Capture console output for saving to file
let outputLines = [];
const originalConsoleLog = console.log;

// Override console.log to capture output
console.log = function(...args) {
  const message = args.join(' ');
  outputLines.push(message);
  originalConsoleLog.apply(console, args);
};

function loadMetrics(filePath) {
  try {
    const content = fs.readFileSync(filePath, 'utf8');
    return JSON.parse(content);
  } catch (error) {
    console.error(`Error loading ${filePath}:`, error.message);
    process.exit(1);
  }
}

function calculateOverhead(baseline, sessionReplay, metricName) {
  if (baseline === 0) {
    return {
      absolute: sessionReplay,
      percentage: sessionReplay > 0 ? Infinity : 0
    };
  }

  const absolute = sessionReplay - baseline;
  const percentage = (absolute / baseline) * 100;

  return {
    absolute: parseFloat(absolute.toFixed(2)),
    percentage: parseFloat(percentage.toFixed(2))
  };
}

function compareMetrics(baselineData, sessionReplayData) {
  console.log('\n' + '='.repeat(70));
  console.log('SESSION REPLAY PERFORMANCE OVERHEAD ANALYSIS');
  console.log('='.repeat(70));

  // Validate data
  if (!baselineData.resourceMetrics || !sessionReplayData.resourceMetrics) {
    console.error('\nError: Invalid metrics data. Missing resourceMetrics.');
    process.exit(1);
  }

  const baseline = baselineData.resourceMetrics;
  const sessionReplay = sessionReplayData.resourceMetrics;

  console.log('\n--- TEST CONFIGURATION ---');
  console.log(`Baseline Session Replay: ${baselineData.sessionReplayEnabled ? 'ON' : 'OFF'}`);
  console.log(`Test Session Replay: ${sessionReplayData.sessionReplayEnabled ? 'ON' : 'OFF'}`);
  console.log(`Baseline Test Duration: ~${baselineData.testDuration}s (${baseline.count} samples)`);
  console.log(`Test Test Duration: ~${sessionReplayData.testDuration}s (${sessionReplay.count} samples)`);

  // Memory comparison
  console.log('\n--- MEMORY USAGE COMPARISON ---');
  console.log('\nAverage Memory:');
  console.log(`  Baseline:        ${baseline.memory.avg} MB`);
  console.log(`  Session Replay:  ${sessionReplay.memory.avg} MB`);
  const avgMemOverhead = calculateOverhead(baseline.memory.avg, sessionReplay.memory.avg);
  console.log(`  Overhead:        ${avgMemOverhead.absolute > 0 ? '+' : ''}${avgMemOverhead.absolute} MB (${avgMemOverhead.percentage > 0 ? '+' : ''}${avgMemOverhead.percentage}%)`);

  console.log('\nPeak Memory:');
  console.log(`  Baseline:        ${baseline.memory.peak} MB`);
  console.log(`  Session Replay:  ${sessionReplay.memory.peak} MB`);
  const peakMemOverhead = calculateOverhead(baseline.memory.peak, sessionReplay.memory.peak);
  console.log(`  Overhead:        ${peakMemOverhead.absolute > 0 ? '+' : ''}${peakMemOverhead.absolute} MB (${peakMemOverhead.percentage > 0 ? '+' : ''}${peakMemOverhead.percentage}%)`);

  console.log('\nMemory Range (Variability):');
  console.log(`  Baseline:        ${baseline.memory.range} MB`);
  console.log(`  Session Replay:  ${sessionReplay.memory.range} MB`);
  const rangeMemOverhead = calculateOverhead(baseline.memory.range, sessionReplay.memory.range);
  console.log(`  Difference:      ${rangeMemOverhead.absolute > 0 ? '+' : ''}${rangeMemOverhead.absolute} MB (${rangeMemOverhead.percentage > 0 ? '+' : ''}${rangeMemOverhead.percentage}%)`);

  // CPU comparison
  console.log('\n--- CPU USAGE COMPARISON ---');
  console.log('\nAverage CPU:');
  console.log(`  Baseline:        ${baseline.cpu.avg}%`);
  console.log(`  Session Replay:  ${sessionReplay.cpu.avg}%`);
  const avgCpuOverhead = calculateOverhead(baseline.cpu.avg, sessionReplay.cpu.avg);
  console.log(`  Overhead:        ${avgCpuOverhead.absolute > 0 ? '+' : ''}${avgCpuOverhead.absolute}% (${avgCpuOverhead.percentage > 0 ? '+' : ''}${avgCpuOverhead.percentage}%)`);

  console.log('\nPeak CPU:');
  console.log(`  Baseline:        ${baseline.cpu.peak}%`);
  console.log(`  Session Replay:  ${sessionReplay.cpu.peak}%`);
  const peakCpuOverhead = calculateOverhead(baseline.cpu.peak, sessionReplay.cpu.peak);
  console.log(`  Overhead:        ${peakCpuOverhead.absolute > 0 ? '+' : ''}${peakCpuOverhead.absolute}% (${peakCpuOverhead.percentage > 0 ? '+' : ''}${peakCpuOverhead.percentage}%)`);

  // Performance metrics comparison (if available)
  if (baselineData.performanceMetrics && sessionReplayData.performanceMetrics) {
    console.log('\n--- PERFORMANCE METRICS COMPARISON ---');

    if (baselineData.performanceMetrics.startupTime && sessionReplayData.performanceMetrics.startupTime) {
      console.log('\nStartup Time:');
      console.log(`  Baseline:        ${baselineData.performanceMetrics.startupTime}ms`);
      console.log(`  Session Replay:  ${sessionReplayData.performanceMetrics.startupTime}ms`);
      const startupOverhead = calculateOverhead(
        baselineData.performanceMetrics.startupTime,
        sessionReplayData.performanceMetrics.startupTime
      );
      console.log(`  Overhead:        ${startupOverhead.absolute > 0 ? '+' : ''}${startupOverhead.absolute}ms (${startupOverhead.percentage > 0 ? '+' : ''}${startupOverhead.percentage}%)`);
    }

    console.log(`\nTTI Measurements:    Baseline: ${baselineData.performanceMetrics.ttiCount}, Session Replay: ${sessionReplayData.performanceMetrics.ttiCount}`);
    console.log(`Rendering Metrics:   Baseline: ${baselineData.performanceMetrics.renderingCount}, Session Replay: ${sessionReplayData.performanceMetrics.renderingCount}`);
  }

  // Health check comparison
  console.log('\n--- HEALTH CHECK ---');
  console.log(`Fatal Hangs:     Baseline: ${baselineData.healthCheck.fatalHangs}, Session Replay: ${sessionReplayData.healthCheck.fatalHangs}`);
  console.log(`Memory Leaks:    Baseline: ${baselineData.healthCheck.memoryLeaks}, Session Replay: ${sessionReplayData.healthCheck.memoryLeaks}`);

  // Summary and verdict
  console.log('\n--- OVERHEAD SUMMARY ---');
  const overheadSummary = {
    memory: {
      avg: avgMemOverhead,
      peak: peakMemOverhead
    },
    cpu: {
      avg: avgCpuOverhead,
      peak: peakCpuOverhead
    }
  };

  console.log('\nSession Replay introduces:');
  console.log(`  Memory Overhead: ${avgMemOverhead.absolute > 0 ? '+' : ''}${avgMemOverhead.absolute} MB average (${avgMemOverhead.percentage > 0 ? '+' : ''}${avgMemOverhead.percentage}%)`);
  console.log(`  CPU Overhead:    ${avgCpuOverhead.absolute > 0 ? '+' : ''}${avgCpuOverhead.absolute}% average (${avgCpuOverhead.percentage > 0 ? '+' : ''}${avgCpuOverhead.percentage}%)`);

  // Thresholds for acceptable overhead
  const ACCEPTABLE_MEMORY_OVERHEAD_MB = 50; // 50 MB
  const ACCEPTABLE_MEMORY_OVERHEAD_PERCENT = 25; // 25%
  const ACCEPTABLE_CPU_OVERHEAD_PERCENT = 15; // 15%

  console.log('\n--- VERDICT ---');
  let passed = true;

  if (avgMemOverhead.absolute > ACCEPTABLE_MEMORY_OVERHEAD_MB) {
    console.log(`❌ FAIL: Average memory overhead (${avgMemOverhead.absolute} MB) exceeds threshold (${ACCEPTABLE_MEMORY_OVERHEAD_MB} MB)`);
    passed = false;
  } else {
    console.log(`✅ PASS: Average memory overhead (${avgMemOverhead.absolute} MB) is within acceptable limits`);
  }

  if (avgMemOverhead.percentage > ACCEPTABLE_MEMORY_OVERHEAD_PERCENT) {
    console.log(`❌ FAIL: Memory overhead percentage (${avgMemOverhead.percentage}%) exceeds threshold (${ACCEPTABLE_MEMORY_OVERHEAD_PERCENT}%)`);
    passed = false;
  } else {
    console.log(`✅ PASS: Memory overhead percentage (${avgMemOverhead.percentage}%) is within acceptable limits`);
  }

  if (avgCpuOverhead.percentage > ACCEPTABLE_CPU_OVERHEAD_PERCENT) {
    console.log(`❌ FAIL: CPU overhead (${avgCpuOverhead.percentage}%) exceeds threshold (${ACCEPTABLE_CPU_OVERHEAD_PERCENT}%)`);
    passed = false;
  } else {
    console.log(`✅ PASS: CPU overhead (${avgCpuOverhead.percentage}%) is within acceptable limits`);
  }

  console.log('\n' + '='.repeat(70));

  if (passed) {
    console.log('✅ SESSION REPLAY OVERHEAD IS ACCEPTABLE');
    console.log('='.repeat(70) + '\n');
  } else {
    console.log('❌ SESSION REPLAY OVERHEAD EXCEEDS ACCEPTABLE LIMITS');
    console.log('='.repeat(70) + '\n');
  }

  // Save output to file
  const outputFile = path.join(process.cwd(), 'comparison-report.txt');
  try {
    fs.writeFileSync(outputFile, outputLines.join('\n'));
    console.log(`📄 Comparison report saved to: comparison-report.txt`);
  } catch (error) {
    console.error(`Failed to save comparison report: ${error.message}`);
  }

  process.exit(passed ? 0 : 1);
}

// Main execution
if (process.argv.length < 4) {
  console.log('Usage: node compare-session-replay-overhead.js <baseline.json> <session-replay.json>');
  console.log('\nExample:');
  console.log('  node compare-session-replay-overhead.js baseline-metrics.json session-replay-metrics.json');
  process.exit(1);
}

const baselineFile = process.argv[2];
const sessionReplayFile = process.argv[3];

console.log(`Loading baseline metrics from: ${baselineFile}`);
const baselineData = loadMetrics(baselineFile);

console.log(`Loading Session Replay metrics from: ${sessionReplayFile}`);
const sessionReplayData = loadMetrics(sessionReplayFile);

compareMetrics(baselineData, sessionReplayData);
