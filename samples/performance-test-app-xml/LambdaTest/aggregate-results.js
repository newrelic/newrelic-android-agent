#!/usr/bin/env node
/**
 * Aggregate performance test results from multiple runs
 * Calculates averages, std dev, and comparisons
 */

const fs = require('fs');
const path = require('path');

const RESULTS_DIR = 'results';

// Helper functions
const mean = (arr) => arr.reduce((a, b) => a + b, 0) / arr.length;
const stdDev = (arr) => {
  const avg = mean(arr);
  const squareDiffs = arr.map(value => Math.pow(value - avg, 2));
  return Math.sqrt(mean(squareDiffs));
};

// Load all results for a configuration
function loadResults(configName, numRuns = 5) {
  const results = [];
  for (let i = 1; i <= numRuns; i++) {
    const filePath = path.join(RESULTS_DIR, `${configName}_run${i}.json`);
    if (fs.existsSync(filePath)) {
      const data = JSON.parse(fs.readFileSync(filePath, 'utf8'));
      results.push(data);
    } else {
      console.warn(`⚠️  Missing: ${filePath}`);
    }
  }
  return results;
}

// Aggregate metrics from multiple runs
function aggregateMetrics(results) {
  if (results.length === 0) return null;

  const avgMemories = results.map(r => r.resourceMetrics.memory.avg);
  const peakMemories = results.map(r => r.resourceMetrics.memory.peak);
  const avgCPUs = results.map(r => r.resourceMetrics.cpu.avg);
  const peakCPUs = results.map(r => r.resourceMetrics.cpu.peak);
  const startupTimes = results.map(r => r.performanceMetrics.startupTime).filter(t => t > 0);

  return {
    runs: results.length,
    memory: {
      avg: {
        mean: parseFloat(mean(avgMemories).toFixed(2)),
        stdDev: parseFloat(stdDev(avgMemories).toFixed(2)),
        min: parseFloat(Math.min(...avgMemories).toFixed(2)),
        max: parseFloat(Math.max(...avgMemories).toFixed(2))
      },
      peak: {
        mean: parseFloat(mean(peakMemories).toFixed(2)),
        stdDev: parseFloat(stdDev(peakMemories).toFixed(2)),
        min: parseFloat(Math.min(...peakMemories).toFixed(2)),
        max: parseFloat(Math.max(...peakMemories).toFixed(2))
      }
    },
    cpu: {
      avg: {
        mean: parseFloat(mean(avgCPUs).toFixed(2)),
        stdDev: parseFloat(stdDev(avgCPUs).toFixed(2)),
        min: parseFloat(Math.min(...avgCPUs).toFixed(2)),
        max: parseFloat(Math.max(...avgCPUs).toFixed(2))
      },
      peak: {
        mean: parseFloat(mean(peakCPUs).toFixed(2)),
        stdDev: parseFloat(stdDev(peakCPUs).toFixed(2)),
        min: parseFloat(Math.min(...peakCPUs).toFixed(2)),
        max: parseFloat(Math.max(...peakCPUs).toFixed(2))
      }
    },
    startup: startupTimes.length > 0 ? {
      mean: parseFloat(mean(startupTimes).toFixed(0)),
      stdDev: parseFloat(stdDev(startupTimes).toFixed(0)),
      min: Math.min(...startupTimes),
      max: Math.max(...startupTimes)
    } : null
  };
}

// Calculate overhead percentage
function calculateOverhead(baseline, test) {
  return {
    memory: {
      avg: {
        absolute: parseFloat((test.memory.avg.mean - baseline.memory.avg.mean).toFixed(2)),
        percent: parseFloat(((test.memory.avg.mean - baseline.memory.avg.mean) / baseline.memory.avg.mean * 100).toFixed(2))
      },
      peak: {
        absolute: parseFloat((test.memory.peak.mean - baseline.memory.peak.mean).toFixed(2)),
        percent: parseFloat(((test.memory.peak.mean - baseline.memory.peak.mean) / baseline.memory.peak.mean * 100).toFixed(2))
      }
    },
    cpu: {
      avg: {
        absolute: parseFloat((test.cpu.avg.mean - baseline.cpu.avg.mean).toFixed(2)),
        percent: parseFloat(((test.cpu.avg.mean - baseline.cpu.avg.mean) / baseline.cpu.avg.mean * 100).toFixed(2))
      },
      peak: {
        absolute: parseFloat((test.cpu.peak.mean - baseline.cpu.peak.mean).toFixed(2)),
        percent: parseFloat(((test.cpu.peak.mean - baseline.cpu.peak.mean) / baseline.cpu.peak.mean * 100).toFixed(2))
      }
    },
    startup: test.startup && baseline.startup ? {
      absolute: test.startup.mean - baseline.startup.mean,
      percent: parseFloat(((test.startup.mean - baseline.startup.mean) / baseline.startup.mean * 100).toFixed(2))
    } : null
  };
}

// Print results table
function printResults(config, metrics) {
  console.log(`\n${'='.repeat(70)}`);
  console.log(`${config.toUpperCase()}`);
  console.log(`${'='.repeat(70)}`);
  console.log(`Runs: ${metrics.runs}`);
  console.log('');
  console.log('MEMORY (MB):');
  console.log(`  Average: ${metrics.memory.avg.mean} ± ${metrics.memory.avg.stdDev} (${metrics.memory.avg.min} - ${metrics.memory.avg.max})`);
  console.log(`  Peak:    ${metrics.memory.peak.mean} ± ${metrics.memory.peak.stdDev} (${metrics.memory.peak.min} - ${metrics.memory.peak.max})`);
  console.log('');
  console.log('CPU (%):');
  console.log(`  Average: ${metrics.cpu.avg.mean} ± ${metrics.cpu.avg.stdDev} (${metrics.cpu.avg.min} - ${metrics.cpu.avg.max})`);
  console.log(`  Peak:    ${metrics.cpu.peak.mean} ± ${metrics.cpu.peak.stdDev} (${metrics.cpu.peak.min} - ${metrics.cpu.peak.max})`);

  if (metrics.startup) {
    console.log('');
    console.log('STARTUP (ms):');
    console.log(`  ${metrics.startup.mean} ± ${metrics.startup.stdDev} (${metrics.startup.min} - ${metrics.startup.max})`);
  }
}

// Print overhead comparison
function printOverhead(title, baseline, test, overhead) {
  console.log(`\n${'='.repeat(70)}`);
  console.log(`${title}`);
  console.log(`${'='.repeat(70)}`);
  console.log('');
  console.log('MEMORY OVERHEAD:');
  console.log(`  Average: +${overhead.memory.avg.absolute} MB (${overhead.memory.avg.percent > 0 ? '+' : ''}${overhead.memory.avg.percent}%)`);
  console.log(`  Peak:    +${overhead.memory.peak.absolute} MB (${overhead.memory.peak.percent > 0 ? '+' : ''}${overhead.memory.peak.percent}%)`);
  console.log('');
  console.log('CPU OVERHEAD:');
  console.log(`  Average: ${overhead.cpu.avg.absolute > 0 ? '+' : ''}${overhead.cpu.avg.absolute}% (${overhead.cpu.avg.percent > 0 ? '+' : ''}${overhead.cpu.avg.percent}%)`);
  console.log(`  Peak:    ${overhead.cpu.peak.absolute > 0 ? '+' : ''}${overhead.cpu.peak.absolute}% (${overhead.cpu.peak.percent > 0 ? '+' : ''}${overhead.cpu.peak.percent}%)`);

  if (overhead.startup) {
    console.log('');
    console.log('STARTUP OVERHEAD:');
    console.log(`  ${overhead.startup.absolute > 0 ? '+' : ''}${overhead.startup.absolute}ms (${overhead.startup.percent > 0 ? '+' : ''}${overhead.startup.percent}%)`);
  }
}

// Main
const numRuns = parseInt(process.argv[2]) || 5;  // Default to 5, but allow override

console.log('\n========================================');
console.log('Android Performance Test - Results');
console.log(`Aggregating ${numRuns} runs per configuration`);
console.log('========================================\n');

// Load and aggregate results
const noAgentResults = loadResults('no_agent', numRuns);
const baselineResults = loadResults('baseline', numRuns);
const sessionReplayResults = loadResults('session_replay', numRuns);

const noAgentMetrics = aggregateMetrics(noAgentResults);
const baselineMetrics = aggregateMetrics(baselineResults);
const sessionReplayMetrics = aggregateMetrics(sessionReplayResults);

// Print individual results
if (noAgentMetrics) printResults('No Agent (Pure Baseline)', noAgentMetrics);
if (baselineMetrics) printResults('Baseline (Agent ON, SR OFF)', baselineMetrics);
if (sessionReplayMetrics) printResults('Session Replay (Agent ON, SR ON)', sessionReplayMetrics);

// Print comparisons
if (noAgentMetrics && baselineMetrics) {
  const agentOverhead = calculateOverhead(noAgentMetrics, baselineMetrics);
  printOverhead('AGENT OVERHEAD (vs No Agent)', noAgentMetrics, baselineMetrics, agentOverhead);
}

if (baselineMetrics && sessionReplayMetrics) {
  const srOverhead = calculateOverhead(baselineMetrics, sessionReplayMetrics);
  printOverhead('SESSION REPLAY OVERHEAD (vs Baseline)', baselineMetrics, sessionReplayMetrics, srOverhead);
}

if (noAgentMetrics && sessionReplayMetrics) {
  const totalOverhead = calculateOverhead(noAgentMetrics, sessionReplayMetrics);
  printOverhead('TOTAL OVERHEAD (Agent + SR vs No Agent)', noAgentMetrics, sessionReplayMetrics, totalOverhead);
}

// Save aggregated results
const aggregatedResults = {
  timestamp: new Date().toISOString(),
  noAgent: noAgentMetrics,
  baseline: baselineMetrics,
  sessionReplay: sessionReplayMetrics,
  overhead: {
    agent: noAgentMetrics && baselineMetrics ? calculateOverhead(noAgentMetrics, baselineMetrics) : null,
    sessionReplay: baselineMetrics && sessionReplayMetrics ? calculateOverhead(baselineMetrics, sessionReplayMetrics) : null,
    total: noAgentMetrics && sessionReplayMetrics ? calculateOverhead(noAgentMetrics, sessionReplayMetrics) : null
  }
};

const outputFile = path.join(RESULTS_DIR, 'aggregated_results.json');
fs.writeFileSync(outputFile, JSON.stringify(aggregatedResults, null, 2));
console.log(`\n${'='.repeat(70)}`);
console.log(`✓ Aggregated results saved to: ${outputFile}`);
console.log(`${'='.repeat(70)}\n`);
